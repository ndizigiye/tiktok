# Loop architecture

Loop is an original short-video app: a vertical, full-screen, swipeable video
feed with likes, comments, sharing, discovery, in-app recording and creator
profiles, backed by its own server. It reproduces the *interaction model*
popularised by short-video apps; it contains no code, assets, names or
branding from any existing app. The app talks only to its own server, which
keeps its data in SQLite or in the project's own Supabase Postgres database, and
its media on disk or in the project's Supabase Storage bucket. The app never uses
Supabase's APIs or keys; with Supabase Storage it loads media from the bucket's
public URLs that the server puts in the DTOs.

```
repo/
├── settings.gradle.kts, build.gradle.kts   Android build (:app + :shared)
├── gradle/libs.versions.toml               single version catalog for both builds
├── shared/                                 Kotlin/JVM: API DTOs + pure helpers
├── app/                                    Android app (Kotlin, Compose, Media3, CameraX)
├── server/                                 separate Gradle build: Ktor backend (SQLite or Supabase Postgres,
│                                           local disk or Supabase Storage); supabase/migrations/ for the schema
├── scripts/                                e2e-server.sh: a seeded server for the end-to-end tests,
│                                           device-screenshots.sh: screenshots of the device tests (see Tests)
└── docs/                                   api.md, architecture.md, feature-analysis.md, rea-playbook.md,
                                            supabase-live-check.md
```

## Toolchain (pinned in `gradle/libs.versions.toml`)

Gradle 8.14.3 wrapper, AGP 8.7.3, Kotlin 2.1.0 (Compose compiler plugin),
compileSdk/targetSdk 35, minSdk 26, Java/JVM target 17.
Compose BOM 2024.12.01 (Compose 1.7.x, Material 3 1.3.x), activity-compose 1.9.3,
lifecycle 2.8.7, navigation-compose 2.8.5 (type-safe `@Serializable` routes),
Media3 1.5.1, CameraX 1.4.1, Coil 3.0.4, Retrofit 2.11.0 with the official
kotlinx-serialization converter, OkHttp 4.12.0, kotlinx-serialization 1.7.3,
coroutines 1.9.0. Server: Ktor 3.0.3, Exposed 0.57.0, sqlite-jdbc 3.47.1.0,
PostgreSQL JDBC 42.7.14, HikariCP 7.1.0, logback 1.5.12.
Tests: JUnit 4.13.2, kotlinx-coroutines-test, MockWebServer 4.12.0, Robolectric 4.14.1,
Compose ui-test (BOM), androidx.test core 1.6.1, ext-junit 1.2.1, runner 1.6.2 and rules 1.6.1,
Espresso 3.6.1, UiAutomator 2.3.0.

No annotation processing (no KSP/kapt, no Hilt, no Room): dependency injection
is a hand-written `AppContainer`, persistence is `SharedPreferences`, and the
server owns all durable data.

## Android app

Package root `com.ndizigiye.loop`, sources in `app/src/main/java/com/ndizigiye/loop/`.

### Layers and ownership

| Package | Contents |
|---|---|
| `.` | `LoopApplication` (creates `AppContainer`), `MainActivity` (edge-to-edge, portrait, sets content to `LoopApp()`). |
| `di` | `AppContainer` |
| `data.remote` | `LoopApi` (Retrofit), `ApiException` / `apiCall`, interceptors, `ProgressRequestBody` |
| `data.session` | `SessionStore` |
| `data` | repositories, `InteractionStore`, `Paginator`, `VideoListHandoff`, `FileUtils` |
| `player` | `VideoCache`, `FeedPlayerPool`, `VideoSurface` |
| `ui.theme` | `LoopTheme`, colors, typography |
| `ui.common` | shared composables (avatar, video grid, thumbnails, states, caption text, follow button, top bar) |
| `ui.navigation` | routes, `LoopNavigator`, `LoopNavHost`, composition locals, `LoopApp` |
| `ui.main` | `MainScreen` (bottom navigation shell) |
| `ui.feed` | home feed, video page, pager screen, heart animation, options sheet |
| `ui.comments` | comments sheet, share sheet |
| `ui.discover` | discover, search, hashtag and sound screens |
| `ui.create` | camera recording and post/upload screens |
| `ui.profile` | profile, edit profile, follow lists |
| `ui.inbox` | notifications |
| `ui.auth` | log in / sign up |
| `ui.settings` | server URL, log out, about |

Feature packages depend on the core packages (`di`, `data*`, `player`,
`ui.theme`, `ui.common`, `ui.navigation`) and never on each other, except that
`ui.feed` uses `CommentsSheet` and `ShareSheet` from `ui.comments`.

### Core API (the contract feature code is written against)

```kotlin
// ---- di/AppContainer.kt
class AppContainer(context: Context) {
    val applicationScope: CoroutineScope      // SupervisorJob + Dispatchers.Default; work that must outlive its screen (view reports, share counts)
    val sessionStore: SessionStore
    val okHttpClient: OkHttpClient            // auth + base-URL interceptors, used by Retrofit, Coil and Media3
    val api: LoopApi
    val interactions: InteractionStore
    val authRepository: AuthRepository
    val videoRepository: VideoRepository
    val userRepository: UserRepository
    val commentRepository: CommentRepository
    val discoverRepository: DiscoverRepository
    val inboxRepository: InboxRepository
    val videoCache: VideoCache
    val videoListHandoff: VideoListHandoff
    val mainTab: MutableStateFlow<MainTab>      // selected bottom tab, survives navigation
}
enum class MainTab { HOME, DISCOVER, INBOX, PROFILE }   // in ui.navigation

// ---- data/session/SessionStore.kt  (SharedPreferences "loop_session")
data class Session(val token: String, val user: UserDto)
class SessionStore(context: Context) {
    val session: StateFlow<Session?>
    val token: String?
    val currentUser: UserDto?
    val isLoggedIn: Boolean
    fun save(token: String, user: UserDto)
    fun updateUser(user: UserDto)
    fun clear()
    val baseUrl: StateFlow<String>             // default BuildConfig.DEFAULT_BASE_URL = "http://10.0.2.2:8080/"
    fun setBaseUrl(url: String)                // normalised to end with "/"
    fun resolveUrl(path: String?): String?     // "/media/x.mp4" -> "<baseUrl>media/x.mp4"; absolute URLs (Supabase Storage) unchanged; null stays null
}

// ---- data/remote/ApiErrors.kt
class ApiException(val status: Int, val code: String, override val message: String) : Exception(message)
suspend fun <T> apiCall(block: suspend () -> T): Result<T>
//  HttpException -> ApiException(status, ErrorDto.code, ErrorDto.message) (falls back to a generic message)
//  IOException   -> ApiException(0, "network", "Can't reach the server. Check your connection and server URL.")
//  CancellationException is rethrown, never wrapped.
fun Throwable.userMessage(): String

// ---- data/Paginator.kt
data class PagedState<T>(
    val items: List<T> = emptyList(),
    val nextCursor: String? = null,
    val isLoading: Boolean = false,       // first page or next page in flight
    val isRefreshing: Boolean = false,    // refresh() in flight while items stay visible
    val error: String? = null,
    val endReached: Boolean = false,
    val loadedOnce: Boolean = false,
)
class Paginator<T>(scope: CoroutineScope, load: suspend (cursor: String?) -> Result<PageDto<T>>) {
    val state: StateFlow<PagedState<T>>
    fun refresh()                                    // reload from the first page
    fun loadMore()                                   // no-op while loading or at the end; first call loads page 1
    fun update(transform: (List<T>) -> List<T>)      // local edits (remove a deleted item, prepend a new one)
}

// ---- data/InteractionStore.kt — one source of truth for like/bookmark/follow state across screens
data class VideoStats(
    val liked: Boolean, val likeCount: Long,
    val bookmarked: Boolean, val bookmarkCount: Long,
    val commentCount: Long, val shareCount: Long,
)
class InteractionStore {
    val videos: StateFlow<Map<Long, VideoStats>>
    val following: StateFlow<Map<Long, Boolean>>       // userId -> isFollowing
    fun remember(videos: List<VideoDto>)               // record server state (skips ids with a mutation in flight)
    fun rememberUsers(users: List<UserDto>)
    fun statsFor(video: VideoDto): VideoStats           // stored value, else derived from the DTO
    fun update(video: VideoDto, transform: (VideoStats) -> VideoStats)
    fun isFollowing(user: UserDto): Boolean
    fun setFollowing(userId: Long, following: Boolean)
    fun beginMutation(videoId: Long); fun endMutation(videoId: Long)
    fun clear()
}

// ---- data/*Repository.kt — every list call feeds InteractionStore.remember / rememberUsers
class AuthRepository {
    suspend fun login(username: String, password: String): Result<UserDto>
    suspend fun register(username: String, displayName: String, password: String): Result<UserDto>
    suspend fun refreshMe(): Result<UserDto>
    fun logout()                                       // clears session and interaction state
}
enum class FeedType { FOR_YOU, FOLLOWING }
class VideoRepository {
    suspend fun feed(type: FeedType, cursor: String?): Result<PageDto<VideoDto>>
    suspend fun video(id: Long): Result<VideoDto>
    suspend fun userVideos(username: String, cursor: String?): Result<PageDto<VideoDto>>
    suspend fun likedVideos(username: String, cursor: String?): Result<PageDto<VideoDto>>
    suspend fun bookmarks(cursor: String?): Result<PageDto<VideoDto>>
    suspend fun hashtagVideos(name: String, cursor: String?): Result<PageDto<VideoDto>>
    suspend fun soundVideos(soundId: Long, cursor: String?): Result<PageDto<VideoDto>>
    suspend fun setLiked(video: VideoDto, liked: Boolean): Result<ToggleResultDto>          // optimistic, reverts on failure
    suspend fun setBookmarked(video: VideoDto, bookmarked: Boolean): Result<ToggleResultDto> // optimistic, reverts on failure
    suspend fun share(video: VideoDto): Result<CountDto>
    suspend fun recordView(videoId: Long, watchedMs: Long, completed: Boolean): Result<Unit>
    suspend fun notInterested(videoId: Long): Result<Unit>
    suspend fun delete(videoId: Long): Result<Unit>
    suspend fun upload(file: File, caption: String, soundId: Long?, onProgress: (Float) -> Unit): Result<VideoDto>
}
class UserRepository {
    suspend fun user(username: String): Result<UserDto>
    suspend fun setFollowing(user: UserDto, follow: Boolean): Result<ToggleResultDto>       // optimistic
    suspend fun followers(username: String, cursor: String?): Result<PageDto<UserDto>>
    suspend fun following(username: String, cursor: String?): Result<PageDto<UserDto>>
    suspend fun updateProfile(displayName: String?, bio: String?): Result<UserDto>         // also updates SessionStore
    suspend fun uploadAvatar(file: File, mimeType: String): Result<UserDto>                 // also updates SessionStore
}
class CommentRepository {
    suspend fun comments(videoId: Long, cursor: String?): Result<PageDto<CommentDto>>
    suspend fun replies(commentId: Long, cursor: String?): Result<PageDto<CommentDto>>
    suspend fun post(video: VideoDto, text: String, parentId: Long?): Result<CommentDto>    // bumps commentCount
    suspend fun setLiked(commentId: Long, liked: Boolean): Result<ToggleResultDto>
    suspend fun delete(video: VideoDto, comment: CommentDto): Result<Unit>                 // lowers commentCount (1 + replyCount)
}
class DiscoverRepository {
    suspend fun discover(): Result<TrendingDto>
    suspend fun search(query: String): Result<SearchResultDto>
    suspend fun hashtag(name: String): Result<HashtagDto>
    suspend fun sound(id: Long): Result<SoundDto>
}
class InboxRepository {
    val unreadCount: StateFlow<Long>
    suspend fun notifications(cursor: String?): Result<PageDto<NotificationDto>>
    suspend fun refreshUnreadCount()
    suspend fun markAllRead(upTo: Long): Result<Unit>   // upTo = newest loaded id; refreshes unreadCount afterwards
}

// ---- data/VideoListHandoff.kt — passes an already-loaded list to the full-screen pager
class VideoListSource(
    val videos: List<VideoDto>,
    val nextCursor: String?,
    val loadMore: (suspend (cursor: String) -> Result<PageDto<VideoDto>>)?,
)
class VideoListHandoff { fun put(source: VideoListSource): String; fun get(key: String): VideoListSource? }
//  In memory only: after process death the pager gets null and shows just the video that was on
//  screen (VideoPagerViewModel keeps its id in a SavedStateHandle), else the route's videoId.

// ---- data/FileUtils.kt
const val TEMP_FILE_PREFIX = "loop-"                                  // every Loop temp file in cacheDir starts with it
suspend fun Context.copyUriToCache(uri: Uri, prefix: String): File   // IO dispatcher; "loop-<prefix>…"; deleted on failure/cancellation
suspend fun Context.deleteStaleTempFiles(maxAgeMs: Long)             // LoopApplication.onCreate sweeps files older than 24 h
fun Context.mimeTypeOf(uri: Uri): String?

// ---- player/
@UnstableApi class VideoCache(context: Context, okHttpClient: OkHttpClient) {
    val dataSourceFactory: DataSource.Factory    // CacheDataSource over OkHttpDataSource, 512 MB LRU SimpleCache
    fun prefetch(url: String, bytes: Long = 1_500_000L)   // background CacheWriter for the first bytes; de-duplicated
    suspend fun usedBytes(): Long                         // IO dispatcher
    suspend fun clear()                                   // IO dispatcher; readers of removed videos refetch them
    fun release()
}
class FeedPlayerPool(context: Context, videoCache: VideoCache, size: Int = 3) {
    fun acquire(index: Int, url: String): ExoPlayer      // slot index % size, prepared with url, repeat-one, paused
    fun setCurrent(index: Int, play: Boolean)            // play the current page, pause + rewind the others
    fun pauseAll()
    fun release()
}
@Composable fun rememberFeedPlayerPool(): FeedPlayerPool  // released when it leaves composition
@Composable fun VideoSurface(player: ExoPlayer?, modifier: Modifier = Modifier, fill: Boolean = true)
// PlayerView via AndroidView, no controller, RESIZE_MODE_ZOOM when fill else FIT, black shutter.

// ---- ui/common/ (besides avatar, thumbnails, caption text, follow button, state boxes, top bar)
@Composable fun VideoGrid(videos: List<VideoDto>, onVideoClick: (index: Int) -> Unit, modifier: Modifier = Modifier,
                          onLoadMore: () -> Unit = {}, isLoadingMore: Boolean = false, loadMoreFailed: Boolean = false,
                          state: LazyGridState = rememberLazyGridState(), contentPadding: PaddingValues = PaddingValues(0.dp),
                          header: LazyGridScope.() -> Unit = {}, footer: LazyGridScope.() -> Unit = {})
fun LazyGridScope.videoGridItems(videos: List<VideoDto>, onClick: (index: Int) -> Unit)
fun LazyGridScope.fullWidthItem(key: String, content: @Composable LazyGridItemScope.() -> Unit)
@Composable fun LoadMoreEffect(state: LazyGridState | LazyListState, enabled: Boolean = true, onLoadMore: () -> Unit)
//  near the end -> onLoadMore; pass enabled = false while the last page failed (no retry loop while offline)
@Composable fun LoadingMoreFooter(modifier: Modifier = Modifier)
@Composable fun LoadMoreErrorFooter(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier)
@Composable fun rememberMessageScope(): CoroutineScope  // activity scope for snackbars that outlive the destination

// ---- ui/navigation/
val LocalAppContainer: ProvidableCompositionLocal<AppContainer>
val LocalNavigator: ProvidableCompositionLocal<LoopNavigator>
class LoopNavigator {
    fun back()
    fun openProfile(username: String)
    fun openVideoPager(videos: List<VideoDto>, startIndex: Int, nextCursor: String? = null,
                       loadMore: (suspend (cursor: String) -> Result<PageDto<VideoDto>>)? = null)
    fun openVideo(videoId: Long)
    fun openHashtag(name: String)
    fun openSound(soundId: Long)
    fun openSearch()
    fun openAuth()
    fun openCamera(soundId: Long? = null)
    fun openPost(videoUri: String, soundId: Long? = null)
    fun openEditProfile()
    fun openFollowList(username: String, showFollowers: Boolean)
    fun openSettings()
    fun selectTab(tab: MainTab)                          // pops back to the main shell and selects the tab
    fun requireAuth(): Boolean                           // true if logged in, else opens auth and returns false
}
```

### Screens (each feature package implements exactly these entry points)

```kotlin
// ui.main
@Composable fun MainScreen()
// ui.feed
@Composable fun FeedScreen(isActive: Boolean, modifier: Modifier = Modifier)
@Composable fun VideoPagerScreen(sourceKey: String?, startIndex: Int, videoId: Long?)
// ui.comments
@Composable fun CommentsSheet(video: VideoDto, onDismiss: () -> Unit)
@Composable fun ShareSheet(video: VideoDto, onDismiss: () -> Unit)
// ui.discover
@Composable fun DiscoverScreen(modifier: Modifier = Modifier)
@Composable fun SearchScreen()
@Composable fun HashtagScreen(name: String)
@Composable fun SoundScreen(soundId: Long)
// ui.create
@Composable fun CameraScreen(soundId: Long?)
@Composable fun PostScreen(videoUri: String, soundId: Long?)
// ui.profile
@Composable fun ProfileScreen(username: String?, modifier: Modifier = Modifier)  // null = signed-in user (tab)
@Composable fun EditProfileScreen()
@Composable fun FollowListScreen(username: String, showFollowers: Boolean)
// ui.inbox
@Composable fun InboxScreen(modifier: Modifier = Modifier)
// ui.auth
@Composable fun AuthScreen()
// ui.settings
@Composable fun SettingsScreen()
```

Routes (`ui/navigation/Routes.kt`, `@Serializable`): `MainRoute`, `AuthRoute`,
`ProfileRoute(username)`, `VideoPagerRoute(sourceKey: String?, startIndex: Int, videoId: Long?)`,
`HashtagRoute(name)`, `SoundRoute(soundId)`, `SearchRoute`, `EditProfileRoute`,
`FollowListRoute(username, showFollowers)`, `CameraRoute(soundId: Long?)`,
`PostRoute(videoUri, soundId: Long?)`, `SettingsRoute`.

### Conventions

- ViewModels are created with `viewModel { FooViewModel(container.x, ...) }`
  (`androidx.lifecycle.viewmodel.compose.viewModel`), keyed with `key = ...`
  when a screen is parameterised. They expose `StateFlow`s collected with
  `collectAsStateWithLifecycle()`. State that must survive process death goes
  into a `SavedStateHandle` from `createSavedStateHandle()` in that initializer
  (`VideoPagerViewModel` keeps the video on screen; `VerticalVideoFeed` reports it
  through `onSettled`).
- User-visible strings are in English in the composables (the app is a
  reference implementation; strings.xml holds only `app_name`).
- Icons come from `androidx.compose.material.icons` (core + extended); no
  bitmap assets ship with the app.
- Errors surface as text from `Throwable.userMessage()`; one-off messages use
  the `SnackbarHostState` from `LocalSnackbarHostState`. A message shown while
  its screen closes is launched in `rememberMessageScope()`; a request that must
  finish after its screen closes runs in `AppContainer.applicationScope`.
- Actions that need an account call `LocalNavigator.current.requireAuth()` first.
- All media URLs go through `SessionStore.resolveUrl` (`rememberResolvedUrl(path)` in `ui.common`).

## Playback design

- The home feed is a `HorizontalPager` with two pages (Following, For You); each
  page is a `VerticalPager` of full-screen videos with
  `beyondViewportPageCount = 1`, so the previous, current and next pages are
  composed. A `FeedPlayerPool` of three ExoPlayers maps page `i` to slot `i % 3`:
  neighbours are prepared and paused on their first frame, so a swipe starts
  playback without a network round trip.
- All players read through one Media3 `SimpleCache` (512 MB LRU). The feed
  prefetches the first ~1.5 MB of the next three videos with `CacheWriter`.
- Playback pauses when the page is not settled, the tab is not visible, another
  destination covers the feed, or the app goes to the background.
- Each page reports a view (`watchedMs`, `completed`) when it stops being current.

## Server

`server/` is a Ktor 3 application (Netty) and the app's only backend. Where it keeps
data is configuration; routes and services are the same either way.

- Rows: Exposed over SQLite in `LOOP_DATA_DIR` by default, or over PostgreSQL when
  `LOOP_DATABASE_URL` is set. Production is the Supabase project `loop` (ref
  `qzqkxwtbajifzsdkotud`, eu-central-1), reached through its session pooler. Every
  table lives in the private schema `loop`, which only `server/supabase/migrations/`
  creates: row level security on without policies, no privileges for `anon` and
  `authenticated`, not exposed through the Data API. The server never creates tables
  there; at startup it compares the schema with `db/Tables.kt` and exits with
  instructions when they differ.
- Media: a `MediaStore`. By default files in `LOOP_DATA_DIR`, served at `/media/...`;
  with `LOOP_SUPABASE_SERVICE_KEY`, the public Storage bucket `loop-media` (uploads and
  deletes through the Storage REST API with the secret key; DTOs carry the absolute
  public object URLs, which clients load directly, with Range requests).
  ffmpeg/ffprobe always work on local temporary copies in `LOOP_DATA_DIR/tmp`.
- Secrets (database URL, Storage key, JWT secret) come only from the environment and
  never appear in logs or error messages. The app never talks to Supabase and never
  holds a key.

Routes follow `docs/api.md`. On first start with an empty database it seeds demo
users and generates demo clips with ffmpeg's built-in test sources (no third-party
media), stored through the same `MediaStore`. Setup, configuration and operations are
in `server/README.md` (section "Supabase"); `docs/supabase-live-check.md` verifies a
deployment against the live project.

## Tests

- `./gradlew :app:testDebugUnitTest`: JVM unit tests of the data layer and ViewModels
  (MockWebServer plays the server). `./gradlew -p server test`: the server's integration tests.
- End-to-end, on the JVM: `app/src/test/java/com/ndizigiye/loop/e2e/` runs the real app
  (MainActivity, LoopApp, repositories, OkHttp) on Robolectric 4.14 (SDK 35, native graphics)
  with Compose UI tests against a live, seeded Loop server, and checks every UI action against
  the server over plain HTTP, using a second account for the inbox. The tests are skipped
  unless the Gradle property `loop.e2e.baseUrl` names a server:

  ```sh
  URL=$(scripts/e2e-server.sh start)          # --postgres (default with a local cluster) or --sqlite
  ./gradlew :app:testDebugUnitTest -Ploop.e2e.baseUrl=$URL
  scripts/e2e-server.sh stop
  ```

  `scripts/e2e-server.sh` builds the server, starts it on a free port (from 18080) with a fresh
  data directory under `/tmp/claude-0/loop-e2e` and `LOOP_SEED=true`, waits for demo seeding,
  and with `--postgres` uses a throwaway database (migrations applied) on the local PostgreSQL
  cluster; `stop` removes all of it. Video decoding (Media3), recording (CameraX),
  picture-in-picture and the upload's foreground service don't run on Robolectric; the suite
  only checks that the app survives them there, and they are verified on an emulator.
- End-to-end, on a device: `app/src/androidTest/java/com/ndizigiye/loop/e2e/` covers what the
  JVM can't: real playback (read from the page's ExoPlayer: playing, position, decoded frames,
  repeat), taps, double taps and scrubbing on the video, a comment, sign-up and log-in, the
  camera's permission dialogs (UiAutomator), the lens flip (the open camera, as the camera
  service reports it), the timer's countdown, a take recorded in two segments with CameraX and
  posted while the app is in the background (the upload's foreground service, checked with
  `dumpsys`), `loop://video/{id}` links started from the shell, picture-in-picture, and
  recovery from an unreachable server. Both suites share the server client, the forwarding
  proxy and the feed helpers in `app/src/sharedTest/java` (a source directory of `test` and
  `androidTest`); each has its own `waitFor`. The server must be reachable from the device, by
  default at the app's `http://10.0.2.2:8080/` (the emulator's alias for the host):

  ```sh
  scripts/e2e-server.sh start --port 8080
  scripts/device-screenshots.sh build/e2e-screens &   # optional: a PNG per saveScreenshot() step
  ./gradlew :app:connectedDebugAndroidTest           # another server: -Pandroid.testInstrumentationRunnerArguments.loopBaseUrl=<url>
  kill %1; scripts/e2e-server.sh stop
  ```

  `CreateDeviceTest` expects the camera and microphone permissions not to be granted yet (as
  after installing); the picture-in-picture test is skipped on devices without the
  `android.software.picture_in_picture` feature. Screenshots are taken from the host because
  capturing on a software-emulated device takes over a minute while video plays.

  On a software-emulated Automated Test Device image (no KVM), prepare the device first:
  `adb shell setprop debug.hwui.drawing_enabled 1` (ATD images skip drawing app windows, so
  screenshots would show only video surfaces), `adb shell am set-debug-app --persistent
  com.ndizigiye.loop` (Android 11 otherwise ends an instrumented app as soon as one input event
  waits 5 s, e.g. behind a slow first frame), `adb shell settings put secure long_press_timeout
  1500` (so slow devices don't turn taps into long presses), animation scales 0, and optionally
  `adb shell wm size 540x1170; adb shell wm density 220` (same dp layout, a quarter of the
  pixels); `adb shell am clear-debug-app` undoes the debug flag afterwards. ATD images have no
  picture-in-picture, so that test is skipped there, and under heavy load their `audioserver`
  can be aborted by its own watchdog, after which every audio call of the app waits for it;
  `adb root && adb shell start audioserver` brings it back.
