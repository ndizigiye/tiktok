# Feature analysis: short-video app interaction model

This is the investigation ledger behind Loop, kept in the format REA's
[reverse-engineer-anything skill](https://github.com/morluto/rea/tree/main/.agents/skills/reverse-engineer-anything)
asks for: a checklist of questions, the evidence each answer needs, what is
observed vs. inferred vs. unknown, and the design choice Loop made.

## Scope and honesty note

- **Goal:** build an original Android app with the familiar short-video
  interaction model (vertical full-screen feed, engagement rail, comments,
  discovery, in-app capture, profiles), not a copy of any existing app.
  Loop has its own name, visuals, code and backend.
- **No REA tool was run for this first version.** Static Android analysis
  (`inspect_android_package` and friends) needs the target APK plus
  jadx-headless-mcp and a JDK. The build sandbox's network policy blocked APK
  mirrors, the vendor's website and Google's Android download hosts, so no
  artifact could be obtained or inspected. Every "observed" entry below
  describes publicly visible, user-facing behaviour of short-video apps, not
  binary evidence. Questions that need static or runtime evidence are marked
  **unresolved**, and [rea-playbook.md](rea-playbook.md) shows how to answer
  them on an APK you are entitled to analyse.
- What Loop takes from this analysis is the *interaction model*. Interaction
  patterns such as "swipe up for the next video" are common across apps; logos,
  names, artwork, sounds, colours and code are not borrowed.

## Checklist

| # | Question | Evidence needed | Status | Basis |
|---|---|---|---|---|
| 1 | How is the home feed laid out and navigated? | UI observation | answered | observed behaviour |
| 2 | Which actions sit on the video and what do they do? | UI observation | answered | observed behaviour |
| 3 | How do tap / double-tap / long-press behave? | UI observation | answered | observed behaviour |
| 4 | How does the app make the next video start instantly? | static code (player setup, preload) + runtime traffic | **unresolved** (design chosen independently) | inference |
| 5 | How is the feed ranked? | server-side; not recoverable from the client | **out of reach** | the client only sees ranked pages |
| 6 | How are comments structured? | UI observation | answered | observed behaviour |
| 7 | What does sharing offer? | UI observation | answered | observed behaviour |
| 8 | How does discovery/search work? | UI observation | answered | observed behaviour |
| 9 | How does capture work (segments, timer, flip, upload)? | UI observation; static code for encoder settings | partially answered | observed behaviour; encoder details unresolved |
| 10 | What is on a profile? | UI observation | answered | observed behaviour |
| 11 | What does the inbox show? | UI observation | answered | observed behaviour |
| 12 | Which Android components, permissions and libraries does the official client use? | `inspect_android_package` manifest + class inventory | **unresolved** | needs APK |
| 13 | How does the client authenticate and sign API requests? | out of scope | **not investigated, by design** | see playbook boundaries |

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

### 4. Instant start (unresolved — independent design)
- **Inference:** near-zero start latency implies the neighbouring videos are
  prepared and their first bytes cached before the swipe.
- **Unknown:** the official client's actual player, buffer sizes and preload
  policy (question for REA: see playbook Q3).
- **Loop:** three pooled ExoPlayers (previous / current / next, slot = page % 3)
  prepared and paused on their first frame, one shared 512 MB Media3
  `SimpleCache`, `CacheWriter` prefetch of the first ~1.5 MB of the next three
  videos, and a small `DefaultLoadControl` buffer tuned for short clips
  (`player/`).

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
- **Loop:** `ShareSheet` with copy link (`/v/{id}` web page served by the Loop
  server), Android share chooser and DownloadManager save.

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
- **Unknown:** the official encoder settings and any on-device effects pipeline.
- **Loop:** CameraX `Recorder` with `Recording.pause()/resume()` so all segments
  land in one MP4 (no client-side concatenation), 60 s cap, 3 s timer, torch,
  Photo Picker upload, multipart upload with progress (`ui/create/`). Audio
  mixing of a chosen sound is not implemented; the sound is attached as
  metadata.

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

### 12–13. Static questions
Not answered in this version; see the [playbook](rea-playbook.md).

## Residual unknowns

- Player/preload parameters of the official client (affects tuning only).
- Exact encoder settings for capture.
- Whether the official client uses any platform features Loop lacks (for
  example picture-in-picture or background audio) — answerable from the
  manifest with `inspect_android_package`.
