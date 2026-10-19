package com.ndizigiye.loop.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.data.InteractionStore
import com.ndizigiye.loop.data.PagedState
import com.ndizigiye.loop.data.Paginator
import com.ndizigiye.loop.data.UserRepository
import com.ndizigiye.loop.data.VideoRepository
import com.ndizigiye.loop.data.remote.ApiException
import com.ndizigiye.loop.data.remote.userMessage
import com.ndizigiye.loop.data.session.Session
import com.ndizigiye.loop.data.session.SessionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    /** The profile shown; for your own profile it follows SessionStore. */
    val user: UserDto? = null,
    val isOwn: Boolean = false,
    val isLoadingUser: Boolean = false,
    /** Why the last user request failed; cleared when one succeeds. */
    val error: String? = null,
    val notFound: Boolean = false,
    val selectedTab: ProfileTab = ProfileTab.VIDEOS,
    val isPullRefreshing: Boolean = false,
) {
    val tabs: List<ProfileTab> get() = ProfileTab.visibleTo(isOwn)
}

private data class UserLoad(
    val user: UserDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val notFound: Boolean = false,
)

/** Profile of [username] (lowercase): the user and one paginated video grid per [ProfileTab]. */
class ProfileViewModel(
    val username: String,
    private val userRepository: UserRepository,
    private val videoRepository: VideoRepository,
    private val interactions: InteractionStore,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val userLoad = MutableStateFlow(UserLoad())
    private val requestedTab = MutableStateFlow(ProfileTab.VIDEOS)
    private val pullRefreshing = MutableStateFlow(false)
    private val _messages = Channel<String>(Channel.BUFFERED)

    private val videos = Paginator(viewModelScope) { cursor -> load(ProfileTab.VIDEOS, cursor) }
    private val liked = Paginator(viewModelScope) { cursor -> load(ProfileTab.LIKED, cursor) }
    private val bookmarks = Paginator(viewModelScope) { cursor -> load(ProfileTab.BOOKMARKS, cursor) }

    /** Tabs to bring up to date when they are next selected; every visit marks them all. */
    private val staleTabs = mutableSetOf<ProfileTab>()

    private var userJob: Job? = null

    /** One-off messages for the snackbar. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    val uiState: StateFlow<ProfileUiState> =
        combine(userLoad, sessionStore.session, requestedTab, pullRefreshing) { load, session, tab, pulling ->
            buildState(load, session, tab, pulling)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = buildState(userLoad.value, sessionStore.session.value, requestedTab.value, false),
        )

    init {
        // A video deleted from the pager would otherwise stay in the grids until they reload.
        viewModelScope.launch {
            videoRepository.deletedVideos.collect { videoId ->
                for (tab in ProfileTab.entries) {
                    paginator(tab).update { list -> list.filterNot { it.id == videoId } }
                }
            }
        }
    }

    fun tabState(tab: ProfileTab): StateFlow<PagedState<VideoDto>> = paginator(tab).state

    /**
     * Reloads the user and brings the selected tab up to date; called whenever the screen
     * is shown. The other tabs catch up when they are selected.
     */
    fun refresh() {
        loadUser()
        staleTabs += ProfileTab.entries
        syncTab(currentTab())
    }

    /** Pull to refresh: reloads the user and the selected tab from its first page. */
    fun pullRefresh() {
        if (pullRefreshing.value) return
        pullRefreshing.value = true
        loadUser()
        val tabPaginator = paginator(currentTab())
        tabPaginator.refresh()
        viewModelScope.launch {
            try {
                userLoad.first { !it.isLoading }
                tabPaginator.state.first { !it.isLoading && !it.isRefreshing }
                // Without a user the screen shows the error itself; grid errors show in the grid.
                val error = userLoad.value.error
                if (error != null && uiState.value.user != null) _messages.send(error)
            } finally {
                pullRefreshing.value = false
            }
        }
    }

    fun selectTab(tab: ProfileTab) {
        requestedTab.value = tab
        if (!paginator(tab).state.value.loadedOnce || tab in staleTabs) syncTab(tab)
    }

    /** Next page of [tab]; ignored before its first page and after an error (see [retry]). */
    fun loadMore(tab: ProfileTab) {
        val paginator = paginator(tab)
        val state = paginator.state.value
        if (state.loadedOnce && state.error == null) paginator.loadMore()
    }

    fun retry(tab: ProfileTab) {
        val paginator = paginator(tab)
        val state = paginator.state.value
        if (state.loadedOnce && !state.endReached) paginator.loadMore() else paginator.refresh()
    }

    /** Continues [tab]'s list for the full-screen pager. Captures no reference to this ViewModel. */
    fun pageLoader(tab: ProfileTab): suspend (cursor: String) -> Result<PageDto<VideoDto>> {
        val repository = videoRepository
        val name = username
        return when (tab) {
            ProfileTab.VIDEOS -> { cursor -> repository.userVideos(name, cursor) }
            ProfileTab.LIKED -> { cursor -> repository.likedVideos(name, cursor) }
            ProfileTab.BOOKMARKS -> { cursor -> repository.bookmarks(cursor) }
        }
    }

    private fun loadUser() {
        userJob?.cancel()
        userLoad.update { it.copy(isLoading = true) }
        userJob = viewModelScope.launch {
            userRepository.user(username)
                .onSuccess { user -> userLoad.value = UserLoad(user = user) }
                .onFailure { error ->
                    userLoad.update {
                        it.copy(
                            isLoading = false,
                            error = error.userMessage(),
                            notFound = error is ApiException && error.status == 404,
                        )
                    }
                }
        }
    }

    /**
     * Brings [tab] up to date without losing the reader's place (see [Paginator.sync]).
     * Videos the viewer has since unliked or unsaved leave their own Liked and Saved
     * grids at once, including those past the first page.
     */
    private fun syncTab(tab: ProfileTab) {
        val paginator = paginator(tab)
        val state = paginator.state.value
        if (state.isLoading || state.isRefreshing) return
        staleTabs -= tab
        stillListed(tab)?.let { keep -> paginator.update { list -> list.filter(keep) } }
        paginator.sync { it.id }
    }

    /**
     * Whether a video still belongs in [tab] going by the viewer's own likes and saves;
     * null when those do not decide it (videos, or someone else's likes).
     */
    private fun stillListed(tab: ProfileTab): ((VideoDto) -> Boolean)? = when {
        tab == ProfileTab.BOOKMARKS -> { video -> interactions.statsFor(video).bookmarked }
        tab == ProfileTab.LIKED && isOwn() -> { video -> interactions.statsFor(video).liked }
        else -> null
    }

    private fun isOwn(): Boolean = sessionStore.currentUser?.username.equals(username, ignoreCase = true)

    private fun currentTab(): ProfileTab = effectiveTab(requestedTab.value, isOwn())

    private fun buildState(load: UserLoad, session: Session?, tab: ProfileTab, pulling: Boolean): ProfileUiState {
        val me = session?.user?.takeIf { it.username.equals(username, ignoreCase = true) }
        return ProfileUiState(
            user = me ?: load.user,
            isOwn = me != null,
            isLoadingUser = load.isLoading,
            error = load.error,
            notFound = load.notFound && me == null,
            selectedTab = effectiveTab(tab, isOwn = me != null),
            isPullRefreshing = pulling,
        )
    }

    private fun paginator(tab: ProfileTab): Paginator<VideoDto> = when (tab) {
        ProfileTab.VIDEOS -> videos
        ProfileTab.LIKED -> liked
        ProfileTab.BOOKMARKS -> bookmarks
    }

    private suspend fun load(tab: ProfileTab, cursor: String?): Result<PageDto<VideoDto>> = when (tab) {
        ProfileTab.VIDEOS -> videoRepository.userVideos(username, cursor)
        ProfileTab.LIKED -> videoRepository.likedVideos(username, cursor)
        ProfileTab.BOOKMARKS -> videoRepository.bookmarks(cursor)
    }
}

private fun effectiveTab(requested: ProfileTab, isOwn: Boolean): ProfileTab =
    requested.takeIf { it in ProfileTab.visibleTo(isOwn) } ?: ProfileTab.VIDEOS
