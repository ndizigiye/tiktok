# Feature analysis: short-video app interaction model

This is the investigation ledger behind Loop, kept in the format REA's
[reverse-engineer-anything skill](https://github.com/morluto/rea/tree/main/.agents/skills/reverse-engineer-anything)
asks for: a checklist of questions, the evidence each answer needs, what is
observed vs. inferred vs. unknown, and the design choice Loop made.

## Scope

- **Goal:** build an original Android app with the familiar short-video
  interaction model (vertical full-screen feed, engagement rail, comments,
  discovery, in-app capture, profiles). Loop has its own name, visuals, code
  and backend. Nothing decompiled is copied into this repository: findings
  are recorded as behaviour and parameters, and Loop's code is written from
  that description.
- **Two kinds of evidence:**
  - *Observed behaviour* — publicly visible, user-facing behaviour of
    short-video apps (questions 1–3, 6–11).
  - *Static evidence* — REA's Android tools run on the official TikTok APK
    (questions 4, 12; see [Provenance](#provenance)). REA observes declarations
    and decompiled code; it never runs the app, so runtime behaviour stays
    inferred.

## Checklist

| # | Question | Evidence needed | Status | Basis |
|---|---|---|---|---|
| 1 | How is the home feed laid out and navigated? | UI observation | answered | observed behaviour |
| 2 | Which actions sit on the video and what do they do? | UI observation | answered | observed behaviour |
| 3 | How do tap / double-tap / long-press behave? | UI observation | answered | observed behaviour |
| 4 | How does the app make the next video start instantly? | static code (player pool, preload config) | **answered (static)**; production values unresolved | REA evidence + inference |
| 5 | How is the feed ranked? | server-side; not recoverable from the client | **out of reach** | the client only sees ranked pages |
| 6 | How are comments structured? | UI observation | answered | observed behaviour |
| 7 | What does sharing offer? | UI observation | answered | observed behaviour |
| 8 | How does discovery/search work? | UI observation | answered | observed behaviour |
| 9 | How does capture work (segments, timer, flip, upload)? | UI observation; static code for encoder settings | answered; production overrides unresolved | observed behaviour + REA evidence |
| 10 | What is on a profile? | UI observation | answered | observed behaviour |
| 11 | What does the inbox show? | UI observation | answered | observed behaviour |
| 12 | Which Android components, permissions and libraries does the official client use? | manifest + class inventory | **answered (static)** | REA evidence + dex index |
| 13 | How does the client authenticate and sign API requests? | out of scope | **not investigated, by design** | see [rea-playbook.md](rea-playbook.md#boundaries) |

## Provenance

| Item | Value |
|---|---|
| Target | TikTok `com.zhiliaoapp.musically` 47.4.42 (versionCode 2024704420), arm64-v8a, standalone base APK, 347 MB |
| Source | APK supplied by the project owner (downloaded from a public mirror), transferred via a private link |
| APK SHA-256 | `36b952cd43f7a6708eb3b9cd16e144d69187b0467256a4b11811a4de0598a6a9` |
| Signature | v1/v2/v3 verify (`apksigner`); one signer, DN `CN=musical.ly, OU=android, O=musical.ly Inc.`, certificate SHA-256 `9041803e91bcb814b4b4399fb5c85a91640b755e5e8ba76813814bf4cf2ab5ba`. Not compared against an independent reference of the official certificate. |
| Tools | REA 6.5.0 CLI, jadx-headless-mcp 0.7.1 (SHA-256 `6e5eacf5…ff75`, verified against REA's pinned digest), OpenJDK 21 |
| Method | The full APK (53 dex files, 438,323 classes) exceeded the memory available to JADX in the analysis sandbox, so REA ran on **derived slices**: the original `AndroidManifest.xml` and `resources.arsc` plus selected dex members, chosen with a class→dex index built from the dex headers. A slice only contains the classes it was built from, so cross-slice references are not covered. |
| Slices | `manifest-slice.apk` (manifest + resources + classes41.dex) `82a8eedd…c337`; `q3-preload.apk` (classes24, 4, 2, 18, 39) `8b06b912…1b4e`; `q4-capture.apk` (classes15, 39) `fd05b819…9245` |

## Findings and design decisions

### 1. Feed layout
- **Observed:** a full-screen, edge-to-edge vertical pager; one video per page;
  swiping up/down moves one page; two feeds (accounts you follow, and a
  recommended feed) switched by tabs at the top, with search at the top right;
  a bottom navigation bar with a prominent centre "create" button.
- **Loop:** `HorizontalPager` (Following / For You) whose pages are
  `VerticalPager`s; bottom bar Home, Discover, +, Inbox, Me
  (`ui/feed/FeedScreen.kt`, `ui/main/MainScreen.kt`).

### 2. On-video actions
- **Observed:** a right-hand vertical rail: creator avatar with a quick-follow
  badge, like with count, comments with count, save with count, share with
  count, and a rotating disc for the soundtrack; bottom-left creator handle,
  caption with tappable hashtags/mentions, and a scrolling soundtrack line; a
  thin playback progress bar at the bottom that can be scrubbed.
- **Loop:** `ActionRail`, `VideoInfo`, `SoundDisc`, `PlaybackProgress` in
  `ui/feed/`. Counts come from one `InteractionStore`, so a like on the feed is
  reflected on profiles and grids immediately.

### 3. Gestures
- **Observed:** tap toggles pause; double tap likes (it never un-likes) and
  shows a heart at the touch point; long press opens options (playback speed,
  not interested, report).
- **Loop:** `detectTapGestures` in `VideoPage`, `HeartBurst` animation,
  `VideoOptionsSheet`. Double tap while signed out routes to sign-in.

### 4. Instant start
Static observations (REA, `q3-preload.apk`):
- **Player reuse pool.** A player-kit config declares a player pool size, a
  pool keep-alive time and separate reuse switches for on-demand video and live
  (`PlayerReuseConfig`, `ev_cdc55d86…58ed`).
- **Preloading is a list of tasks.** The preload strategy holds a list of
  tasks, each with a *count* of videos, an *offset* from the current position,
  a *size* and a *progress* value (`PreloadStrategyConfig` `ev_d37ee66e…db12b`,
  `PreloadTask` `ev_52dbae4a…6cfd`). A task's size is expressed in KB; an
  unset size falls back to about 10 MB (`getVideoPreloadSize`,
  `ev_d2d266d5…eeb2`).
- **Built-in fallback strategy:** exactly one task with the no-argument
  defaults, i.e. preload **one** video ahead with progress 100 and the size
  fallback (`<clinit>` of the defaults holder `ev_e9ebf7a7…aba1`; `PreloadTask`
  constructors `ev_51ae578b…2ba0`, `ev_1fe217cc…7b`).
- **Buffer- and network-aware gating.** The same strategy carries a "secure"
  and a "danger" buffer threshold and lower/upper network-bandwidth bounds; a
  separate buffer-threshold model has "remain to start" / "remain to stop"
  values, a cache duration and a network-speed factor, scoped to video-duration
  ranges (`BufferThreshold` `ev_3b768e78…c6bd`).
- **Predictive prefetch beyond video.** A feed model carries probabilities,
  thresholds and median times for preloading a video's *comments* and its
  creator's *profile* (`feed.model.Preload`, `ev_9eec4cae…aa5e64`).

Inferences:
- Preloading the next video is suspended while the current video's buffer is
  below the danger threshold and resumes above the secure threshold; the
  "remain to start/stop" values express the same idea in buffered time.
- The production parameters are delivered remotely (the classes live in `ab`
  and `experiment` packages and the defaults are only a fallback).

Unknown: the production values, the playback engine internals (ByteDance's own
engine, not ExoPlayer), and the cache size.

**Loop:** keeps three pooled ExoPlayers (previous / current / next) and one
512 MB Media3 cache, and changes prefetching to match the observed model:
- prefetch is gated on the current player's buffered-ahead duration — start
  when at least ~2.5 s is buffered (secure), cancel in-flight prefetches when
  it drops below ~0.8 s (danger);
- tasks: next video up to 2 MB, the one after up to 512 KB;
- after a video has played for ~3 s (or on a long view), prefetch the first
  page of its comments so the comments sheet opens without a spinner.

### 5. Ranking (out of reach)
- A recommender runs server-side; a client binary cannot reveal it.
- **Loop:** a transparent heuristic in the server (engagement rate × freshness,
  follow and hashtag affinity, seen-penalty, deterministic exploration jitter,
  author diversity), documented in [api.md](api.md#ranking-for-you).

### 6. Comments
- **Observed:** a bottom sheet over the playing video; top-level comments with
  like counts, one level of collapsed replies ("view N replies"), a creator
  label, reply-to-mention, and an input bar pinned above the keyboard.
- **Loop:** `ui/comments/CommentsSheet.kt`; server attaches reply-to-reply to the
  top-level parent so threads stay one level deep.

### 7. Sharing
- **Observed:** a share sheet with copy-link, system share and save-to-device;
  shares increment the visible count.
- **Static (manifest):** 25 auto-verified app-link intent filters route the
  vendor's web share URLs straight into the app.
- **Loop:** `ShareSheet` with copy link (`/v/{id}` web page served by the Loop
  server), Android share chooser and DownloadManager save. Because a Loop
  server's host is chosen by whoever runs it, verified app links are not
  possible; the share page offers an "Open in Loop" link using a `loop://video/{id}`
  deep link that the app handles.

### 8. Discovery
- **Observed:** a search entry point, trending hashtags with video rows,
  suggested creators, sound pages listing videos that use a sound, hashtag
  pages with a video grid, and search results split into tabs.
- **Loop:** `ui/discover/` backed by `/api/discover`, `/api/search`,
  `/api/hashtags/*`, `/api/sounds/*`.

### 9. Capture (partially answered)
- **Observed:** full-screen camera; record button that records in segments
  (tap/hold to record, pause between segments) with a segmented progress bar
  and a maximum length; flip camera, flash, countdown timer; upload from the
  gallery; a post screen with caption, hashtags and mentions.
- **Static:** capture and export run on the vendor's own editing SDK
  (`com.ss.android.vesdk`, 301 named classes) rather than CameraX. Export
  settings are a set of remotely overridable properties (each change is
  logged with a strategy stage, name and priority) whose built-in defaults are
  (REA, `q4-capture.apk`: class `VEVideoEncodeConfigParams` `ev_414e9937…32da`,
  constructor `ev_184ff1ae…453c`; resolution model `CompileConfigResolution`
  `ev_79165bef…7a80`):
  - output 576 × 1024 (portrait 9:16);
  - average-bitrate mode, target 4,194,304 bit/s (4 Mbit/s), software encoder
    capped at 15 Mbit/s with its fastest preset;
  - keyframe interval (GOP) 60 frames; frame rate left at the source's
    (unset = -1);
  - hardware encoder, HDR 10-bit and remuxing (skipping re-encode) all off by
    default; a network-level input exists, which suggests quality is adapted
    to the connection.
- **Inference:** clips are re-encoded on the device to a modest bitrate before
  upload, trading quality for upload time; the remote overrides tune this per
  device and network.
- **Static (manifest):** publishing runs in a foreground service of type
  `dataSync`, so an upload continues when the user leaves the app.
- **Loop:** CameraX `Recorder` with `Recording.pause()/resume()` so all segments
  land in one MP4 (no client-side concatenation), 60 s cap, 3 s timer, torch,
  Photo Picker upload, multipart upload with progress (`ui/create/`). From the
  findings: record at CameraX's HD level (1280 × 720, the closest fixed
  quality to 576 × 1024) with a 4 Mbit/s target bitrate so a 60 s clip stays
  around 30 MB, and run the upload in a `dataSync` foreground service so it
  survives backgrounding. Audio mixing of a chosen sound is not implemented;
  the sound is attached as metadata.

### 10. Profiles
- **Observed:** avatar, handle, following/followers/likes counters, edit or
  follow button, bio, and tabs for own videos, liked videos and saved videos as
  a three-column grid of 9:16 thumbnails with play counts.
- **Loop:** `ui/profile/`.

### 11. Inbox
- **Observed:** an activity list (likes, comments, replies, follows, mentions)
  with the actor's avatar, a relative time, a video thumbnail or follow-back
  button, and an unread badge on the bottom bar.
- **Loop:** `ui/inbox/` + `/api/notifications*`; removing a like/follow/comment
  removes its notification.

### 12. Components, permissions and libraries
Manifest (REA `inspect_android_package`, `ev_7cb41969…becc`):
- minSdk 23, targetSdk 36; `largeHeap` enabled; 497 activities, 88 services,
  52 receivers, 11 providers.
- The launcher alias targets the main activity, which declares
  **picture-in-picture** support (as do the live player and one shell activity).
- 17 foreground services; relevant to Loop: background/feed audio playback
  (`mediaPlayback`) with a media session, and publishing (`dataSync`).
- 64 permissions, including camera, microphone, media reads, notifications,
  location, contacts and Bluetooth; most serve features outside Loop's scope
  (live streaming, payments, calls, casting).

Library inventory (class→dex index from the dex headers; static presence only,
and 76% of class names are obfuscated, so absence is not proven):

| Concern | Present | Not found |
|---|---|---|
| Video playback | ByteDance engine (`com.ss.ttvideoengine`, `com.ss.ttm`) | ExoPlayer / Media3 |
| Capture & editing | ByteDance VESDK (`com.ss.android.vesdk`, `medialib`) | CameraX |
| Networking | TTNet over Chromium Cronet (`org.chromium.net`); a few OkHttp classes | Retrofit |
| Images | Fresco image pipeline | Glide, Coil |
| UI | Views, RecyclerView, ViewPager2, Lynx; some Compose runtime | Material 3 Compose |
| Data | Wire (protobuf), Gson, Room | kotlinx.serialization |

**Loop:** Media3 and CameraX are open-source substitutes for proprietary
engines; Compose replaces the view/Lynx stack. Planned from the manifest:
picture-in-picture for the full-screen pager, and a foreground upload service.

## Residual unknowns

- Production values of the preload, buffer and encoder settings (remote A/B
  config; only the built-in defaults are in the APK).
- Playback engine internals and cache sizing.
