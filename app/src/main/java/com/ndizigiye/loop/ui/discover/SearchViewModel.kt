package com.ndizigiye.loop.ui.discover

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ndizigiye.loop.api.SearchResultDto
import com.ndizigiye.loop.data.DiscoverRepository
import com.ndizigiye.loop.data.remote.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEBOUNCE_MS = 300L

enum class SearchTab { TOP, USERS, VIDEOS, HASHTAGS }

/** Results together with the trimmed query they answer. */
data class SearchResults(val query: String, val result: SearchResultDto) {
    val isEmpty: Boolean
        get() = result.users.isEmpty() && result.videos.isEmpty() && result.hashtags.isEmpty()
}

data class SearchUiState(
    /** Most recent first. */
    val recents: List<String> = emptyList(),
    /** The latest results; kept while a newer query loads, cleared for a blank query or a failed search. */
    val results: SearchResults? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTab: SearchTab = SearchTab.TOP,
)

/**
 * Search as you type: the query is debounced, blank queries clear the results, and
 * a newer query cancels the request for an older one. Submitted queries are kept
 * as recent searches.
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    private val discoverRepository: DiscoverRepository,
    private val recentSearches: RecentSearchStore,
) : ViewModel() {

    /** The text field's value; Compose state, so typing never lags behind the keyboard. */
    var query by mutableStateOf(TextFieldValue())
        private set

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val submissions = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val retries = MutableStateFlow(0)

    /** The query of the latest search request; a repeat of it is dropped unless retried. */
    private var lastSearched: String? = null

    init {
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { recentSearches.load() }
            // Queries submitted while the file was being read come first.
            val merged = mergeRecents(_state.value.recents, stored)
            _state.update { it.copy(recents = merged) }
            if (merged != stored) recentSearches.save(merged)
        }
        viewModelScope.launch {
            val typed = snapshotFlow { query.text.trim() }
                .debounce { text -> if (text.isEmpty()) 0L else DEBOUNCE_MS }
            merge(typed, submissions)
                .distinctUntilChanged()
                .combine(retries) { text, _ -> text }
                .collectLatest { text -> search(text) }
        }
    }

    fun onQueryChange(value: TextFieldValue) {
        query = value
    }

    fun clearQuery() {
        query = TextFieldValue()
    }

    /** Searches for the current query without waiting for the debounce and remembers it (IME action, recent search). */
    fun submit() {
        val text = query.text.trim()
        if (text.isEmpty()) return
        rememberQuery(text)
        if (text != lastSearched) {
            submissions.tryEmit(text)
        } else if (_state.value.error != null) {
            retry()
        }
    }

    /** Remembers the query whose results the user is opening (which may lag behind the field). */
    fun onResultOpened() {
        val text = _state.value.results?.query ?: query.text.trim()
        if (text.isNotEmpty()) rememberQuery(text)
    }

    fun selectRecent(text: String) {
        query = TextFieldValue(text, selection = TextRange(text.length))
        submit()
    }

    fun removeRecent(text: String) {
        updateRecents { recents -> recents.filterNot { it == text } }
    }

    fun clearRecents() {
        updateRecents { emptyList() }
    }

    fun selectTab(tab: SearchTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun retry() {
        retries.update { it + 1 }
    }

    private suspend fun search(text: String) {
        lastSearched = text
        if (text.isEmpty()) {
            _state.update { it.copy(results = null, isLoading = false, error = null) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        discoverRepository.search(text)
            .onSuccess { result ->
                _state.update { it.copy(results = SearchResults(text, result), isLoading = false) }
            }
            .onFailure { error ->
                _state.update { it.copy(results = null, isLoading = false, error = error.userMessage()) }
            }
    }

    private fun rememberQuery(text: String) {
        updateRecents { recents -> listOf(text) + recents.filterNot { it.equals(text, ignoreCase = true) } }
    }

    private fun updateRecents(transform: (List<String>) -> List<String>) {
        val updated = transform(_state.value.recents).take(MAX_RECENT_SEARCHES)
        _state.update { it.copy(recents = updated) }
        recentSearches.save(updated)
    }
}

private fun mergeRecents(first: List<String>, second: List<String>): List<String> =
    (first + second).distinctBy { it.lowercase() }.take(MAX_RECENT_SEARCHES)
