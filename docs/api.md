# Loop REST API

The contract between the Android app (`app/`) and the backend (`server/`).
DTO classes live in `shared/src/main/kotlin/com/ndizigiye/loop/api/Dtos.kt`;
both sides serialize them with kotlinx.serialization (`ignoreUnknownKeys = true`,
`explicitNulls = false` on the client, `encodeDefaults = true` on the server).

## Conventions

- Base path `/api`, JSON bodies, UTF-8.
- Authentication: `Authorization: Bearer <jwt>` from register/login. Tokens are
  HS256 JWTs with the user id in the `uid` claim and a 30-day expiry.
  - **[auth]** endpoints return `401` without a valid token.
  - **[opt]** endpoints work anonymously; with a token, viewer-specific fields
    (`likedByMe`, `bookmarkedByMe`, `isFollowing`, `isMe`) are filled in.
- Errors: any non-2xx response has an `ErrorDto` body
  `{"code": "...", "message": "..."}`. Codes: `validation` (400),
  `unauthorized` (401), `invalid_credentials` (401), `forbidden` (403),
  `not_found` (404), `username_taken` (409), `too_large` (413), `internal` (500).
  `message` is safe to show to users.
- Pagination: list endpoints take `?cursor=<opaque>&limit=<n>` (default 10,
  max 50) and return `PageDto<T>` `{"items": [...], "nextCursor": "..."|null}`.
  Pass `nextCursor` back unchanged; `null` means the end of the list.
- Media URLs in DTOs are server-relative (`/media/videos/<file>.mp4`). Clients
  resolve them against the base URL they used for the API.
- Usernames are case-insensitive and stored lowercase; path parameters accept
  any case. Hashtag names are lowercase without `#`.
- Timestamps are epoch milliseconds.

## Health

| Method | Path | Notes |
|---|---|---|
| GET | `/health` | `200 ok` (plain text). |

## Auth and current user

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/auth/register` | `RegisterRequest` | `201 AuthResponse`. Validates `ApiLimits` (username regex, display name 1..40, password >= 6). `409 username_taken`. |
| POST | `/api/auth/login` | `LoginRequest` | `200 AuthResponse`, `401 invalid_credentials`. |
| GET | `/api/me` [auth] | | `UserDto` (`isMe = true`). |
| PATCH | `/api/me` [auth] | `UpdateProfileRequest` | `UserDto`. Null fields are left unchanged. |
| POST | `/api/me/avatar` [auth] | multipart, file part `avatar` (JPEG/PNG/WebP, <= 5 MB) | `UserDto` with new `avatarUrl`. |
| GET | `/api/me/bookmarks` [auth] | | `PageDto<VideoDto>`, most recently bookmarked first. |

## Feeds

| Method | Path | Response |
|---|---|---|
| GET | `/api/feed/for-you` [opt] | `PageDto<VideoDto>` ranked for the viewer (see Ranking). Never ends while videos exist: after every candidate has been served once, it starts a new pass. |
| GET | `/api/feed/following` [auth] | `PageDto<VideoDto>`, videos from followed accounts, newest first. |

### Ranking (For You)

Server-side heuristic standing in for a recommender:

1. Candidates: all videos except the viewer's own and those marked not-interested.
2. Score = engagement rate `(likes + 2*comments + 3*shares + 2*bookmarks + completions) / (views + 10)`
   × freshness decay `1 / (1 + ageHours / 48)`
   + 0.6 if the viewer follows the author
   + 0.4 × (share of the video's hashtags the viewer has liked before)
   − 0.8 if the viewer already watched it
   + a deterministic jitter in [0, 0.3) seeded by (viewer, pass seed, video id) for exploration.
3. Diversity: no two consecutive videos from the same author when avoidable.
4. The cursor encodes the pass seed and offset so pages are stable while
   scrolling; a refresh (no cursor) starts a new seed.

## Videos

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/videos` [auth] | multipart: file part `video` (MP4/WebM/3GP/MOV, <= 200 MB), form field `caption` (<= 300 chars), optional form field `soundId` | `201 VideoDto`. Hashtags are parsed from the caption with `CaptionParser`. Without `soundId` a new sound "original sound - {displayName}" is created. When `ffprobe`/`ffmpeg` are on PATH the server reads duration/size and writes a JPEG thumbnail; otherwise duration is 0 and `thumbnailUrl` null. Mentions in the caption notify the mentioned users. |
| GET | `/api/videos/{id}` [opt] | | `VideoDto` |
| DELETE | `/api/videos/{id}` [auth] | | `204`; `403` unless the author. Deletes comments, likes, bookmarks, notifications and files. |
| PUT | `/api/videos/{id}/like` [auth] | | `ToggleResultDto(active = true, count = likeCount)`. Idempotent. Notifies the author (not for self-likes). |
| DELETE | `/api/videos/{id}/like` [auth] | | `ToggleResultDto(active = false, ...)`. Idempotent. |
| PUT / DELETE | `/api/videos/{id}/bookmark` [auth] | | `ToggleResultDto` with `bookmarkCount`. |
| POST | `/api/videos/{id}/share` [opt] | | `CountDto` with the new share count. |
| POST | `/api/videos/{id}/views` [opt] | `ViewEventRequest` | `204`. Increments `viewCount`; records watch history for signed-in viewers. |
| POST | `/api/videos/{id}/not-interested` [auth] | | `204`. Excluded from the viewer's For You feed. |

## Comments

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/api/videos/{id}/comments` [opt] | | `PageDto<CommentDto>`: top-level comments, most liked first, then newest. |
| GET | `/api/comments/{id}/replies` [opt] | | `PageDto<CommentDto>`: replies, oldest first. |
| POST | `/api/videos/{id}/comments` [auth] | `CreateCommentRequest` (1..300 chars after trimming) | `201 CommentDto`. If `parentId` is itself a reply, the new reply attaches to that reply's top-level parent. `parentId` must belong to the same video (`400`). Notifies the video author (`comment`), the parent author (`reply`) and mentioned users (`mention`), never the actor themself. |
| DELETE | `/api/comments/{id}` [auth] | | `204`; allowed for the comment author and the video author. Deleting a top-level comment deletes its replies. |
| PUT / DELETE | `/api/comments/{id}/like` [auth] | | `ToggleResultDto`. Liking notifies the comment author (`comment_like`). |

`VideoDto.commentCount` counts top-level comments and replies.

## Users

| Method | Path | Response |
|---|---|---|
| GET | `/api/users/{username}` [opt] | `UserDto` |
| GET | `/api/users/{username}/videos` [opt] | `PageDto<VideoDto>`, newest first. |
| GET | `/api/users/{username}/liked` [opt] | `PageDto<VideoDto>`, most recently liked first. |
| GET | `/api/users/{username}/followers` [opt] | `PageDto<UserDto>`, newest follow first. |
| GET | `/api/users/{username}/following` [opt] | `PageDto<UserDto>`, newest follow first. |
| PUT | `/api/users/{username}/follow` [auth] | `ToggleResultDto(active = true, count = target followerCount)`; `400` when following yourself. Notifies the target (`follow`). |
| DELETE | `/api/users/{username}/follow` [auth] | `ToggleResultDto(active = false, ...)` |

## Discover and search

| Method | Path | Response |
|---|---|---|
| GET | `/api/discover` [opt] | `TrendingDto`: up to 8 hashtags ordered by total views of their videos, each with up to 6 of its most-viewed videos; up to 10 sounds by video count; up to 10 suggested creators (most followers, excluding the viewer and accounts they follow). |
| GET | `/api/search?q=<text>&limit=<n>` [opt] | `SearchResultDto`. Case-insensitive substring match: users on username/display name, hashtags on name (a leading `#` in `q` is ignored), videos on caption. Each list is capped at `limit` (default 20). Blank `q` returns empty lists. |
| GET | `/api/hashtags/{name}` | `HashtagDto`; `404` if no video uses it. |
| GET | `/api/hashtags/{name}/videos` [opt] | `PageDto<VideoDto>`, most viewed first. |
| GET | `/api/sounds/{id}` | `SoundDto` |
| GET | `/api/sounds/{id}/videos` [opt] | `PageDto<VideoDto>`, most viewed first. |

## Notifications (inbox)

| Method | Path | Response |
|---|---|---|
| GET | `/api/notifications` [auth] | `PageDto<NotificationDto>`, newest first. |
| GET | `/api/notifications/unread-count` [auth] | `CountDto` |
| POST | `/api/notifications/read` [auth] | `204`, marks all as read. |

Removing a like, follow or comment deletes the notification it created.

## Media

Served without the `/api` prefix, with HTTP range requests (`206 Partial
Content`) so players can seek and prefetch:

- `GET /media/videos/{file}`
- `GET /media/thumbs/{file}`
- `GET /media/avatars/{file}`

## Web

| Method | Path | Response |
|---|---|---|
| GET | `/v/{id}` | `200 text/html`: the public page behind the app's "Copy link" / share action. A small self-contained page (inline CSS, no external assets) with the author, caption, sound, counts and an HTML5 `<video controls playsinline loop>` of the clip, using the thumbnail as `poster`. `404` (`ErrorDto`) for unknown ids. Share links are `<baseUrl>v/<id>`. |

## Server configuration

Environment variables (all optional):

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port (binds 0.0.0.0). |
| `LOOP_DATA_DIR` | `./data` | SQLite database and media files. |
| `LOOP_JWT_SECRET` | dev-only constant (logged warning) | HMAC secret for tokens. |
| `LOOP_SEED` | `true` | Seed demo users, videos (generated with ffmpeg), follows, likes and comments when the database is empty. |
| `LOOP_FFMPEG` / `LOOP_FFPROBE` | `ffmpeg` / `ffprobe` | Tool paths. Without them the server still runs; uploads get no thumbnail and seeding skips media generation. |
