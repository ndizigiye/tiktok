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
    (`likedByMe`, `bookmarkedByMe`, `isFollowing`, `isMe`) are filled in. A token
    that is sent but invalid or expired still gets `401`, so the client notices
    the session has ended (the app then signs out).
  - Endpoints without a tag are public and ignore the `Authorization` header.
- Errors: any non-2xx response has an `ErrorDto` body
  `{"code": "...", "message": "..."}`. Codes: `validation` (400),
  `unauthorized` (401), `invalid_credentials` (401), `forbidden` (403),
  `not_found` (404), `username_taken` (409), `too_large` (413), `internal` (500).
  `message` is safe to show to users. `too_large` is also returned when the media store
  (Supabase Storage) refuses a file as too large.
- Pagination: list endpoints take `?cursor=<opaque>&limit=<n>` (default 10,
  max 50) and return `PageDto<T>` `{"items": [...], "nextCursor": "..."|null}`.
  Pass `nextCursor` back unchanged; `null` means the end of the list. A cursor that is
  malformed or belongs to another list's format is a `validation` error.
- Uploads (multipart): the file part's `Content-Type` decides its type. When it is missing or
  `application/octet-stream`, the file name's extension decides instead (any case): `mp4`,
  `webm`, `3gp`, `mov` for videos; `jpg`, `jpeg`, `png`, `webp` for profile photos. Anything
  else is `400 validation`, with the message "Videos must be MP4, WebM, 3GP or MOV." for videos
  and "Profile photos must be JPEG, PNG or WebP." for profile photos.
- Media URLs in DTOs (`videoUrl`, `thumbnailUrl`, `avatarUrl`, `coverUrl`) are either
  server-relative (`/media/videos/<file>.mp4`, local media) or absolute public URLs of the
  Supabase Storage bucket (production:
  `https://qzqkxwtbajifzsdkotud.supabase.co/storage/v1/object/public/loop-media/videos/<file>.mp4`).
  Clients resolve relative ones against the base URL they used for the API and use absolute ones
  as they are, without sending their token or any other credential. See Media.
- Usernames are case-insensitive and stored lowercase; path parameters accept
  any case. Hashtag names are lowercase without `#`.
- Timestamps are epoch milliseconds.
- Text must not contain U+0000 (PostgreSQL cannot store it): such input is a `validation`
  error, a path name containing it is `404`, and a search for it finds nothing.

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
| POST | `/api/me/avatar` [auth] | multipart, file part `avatar` (JPEG/PNG/WebP by type or file name, see Uploads; <= 5 MB) | `UserDto` with new `avatarUrl`. The previous photo is deleted. Other types: `400 validation`. |
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
| POST | `/api/videos` [auth] | multipart: file part `video` (MP4/WebM/3GP/MOV, <= 50 MB, `ApiLimits.MAX_UPLOAD_BYTES`: Supabase Storage's per-file maximum on the free plan), form field `caption` (<= 300 chars), optional form field `soundId` | `201 VideoDto`. Hashtags are parsed from the caption with `CaptionParser`. Without `soundId` a new sound "original sound - {displayName}" is created. When `ffprobe`/`ffmpeg` are on PATH the server reads duration/size and writes a JPEG thumbnail; otherwise duration is 0 and `thumbnailUrl` null. Mentions in the caption notify the mentioned users. |
| GET | `/api/videos/{id}` [opt] | | `VideoDto` |
| DELETE | `/api/videos/{id}` [auth] | | `204`; `403` unless the author. Deletes comments, likes, bookmarks, notifications and the stored video and thumbnail (if the media store fails, that is logged and the video is deleted anyway). |
| PUT | `/api/videos/{id}/like` [auth] | | `ToggleResultDto(active = true, count = likeCount)`. Idempotent. Notifies the author (not for self-likes). |
| DELETE | `/api/videos/{id}/like` [auth] | | `ToggleResultDto(active = false, ...)`. Idempotent. |
| PUT / DELETE | `/api/videos/{id}/bookmark` [auth] | | `ToggleResultDto` with `bookmarkCount`. |
| POST | `/api/videos/{id}/share` [opt] | | `CountDto` with the new share count. |
| POST | `/api/videos/{id}/views` [opt] | `ViewEventRequest` | `204`. Increments `viewCount`; records watch history for signed-in viewers. |
| POST | `/api/videos/{id}/not-interested` [auth] | | `204`. Excluded from the viewer's For You feed. |

## Comments

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/api/videos/{id}/comments` [opt] | | `PageDto<CommentDto>`: top-level comments, most liked first, then newest, then highest id. The cursor holds the last comment's position (likes, time, id), so comments deleted between pages never cause a skip or a repeat; a comment whose like count changes while paging can move across the cursor. |
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
| POST | `/api/notifications/read?upTo=<id>` [auth] | `204`. Without `upTo`, marks all as read. With `upTo` (the newest notification the client has loaded), marks that notification and every one after it in the inbox order (older ones) as read, so notifications that arrived since stay unread. `upTo` that is not a number: `400 validation`; not one of the caller's notifications (e.g. retracted since): `404 not_found`. |

Removing a like, follow or comment deletes the notification it created.

## Media

With local media (no `LOOP_SUPABASE_SERVICE_KEY`) the server serves the files itself, without
the `/api` prefix, with HTTP range requests (`206 Partial Content`) so players can seek and
prefetch, and `Cache-Control: public, max-age=604800`:

- `GET /media/videos/{file}`
- `GET /media/thumbs/{file}`
- `GET /media/avatars/{file}`

With Supabase Storage (`LOOP_SUPABASE_SERVICE_KEY` set) these routes answer `404`, and DTOs carry
absolute public URLs of the bucket instead:

- `https://<project>.supabase.co/storage/v1/object/public/<bucket>/videos/{file}`
- `https://<project>.supabase.co/storage/v1/object/public/<bucket>/thumbs/{file}`
- `https://<project>.supabase.co/storage/v1/object/public/<bucket>/avatars/{file}`

In production the prefix is `https://qzqkxwtbajifzsdkotud.supabase.co/storage/v1/object/public/loop-media/`.
Supabase serves them without authentication, with range requests (`206 Partial Content`), and with
the same `max-age=604800` caching as the local routes. File names are never reused, so a cached copy
never goes stale. When a video is deleted or an avatar replaced, the server deletes the old object
at once, and the bucket answers its URL with `400` and a JSON body whose `statusCode` is `"404"`
(the local routes answer `404`). Supabase's CDN can still serve a copy it cached before the delete:
on the free plan a delete does not purge the CDN, so such a copy lives until it expires (Smart CDN,
Pro plan and above, invalidates it within about a minute). Clients must not send their Loop token
to these URLs.

## Web

| Method | Path | Response |
|---|---|---|
| GET | `/v/{id}` | `200 text/html`: the public page behind the app's "Copy link" / share action. A small page (inline CSS, no scripts, no assets besides the clip's media) with the author, caption, sound, counts and an HTML5 `<video controls playsinline loop>` of the clip, using the thumbnail as `poster`. Local media URLs are relative to the page (`../media/videos/...`), so they also resolve behind a path prefix; Supabase Storage URLs are absolute. `404` (`ErrorDto`) for unknown ids. Share links are `<baseUrl>v/<id>`. |

## Server configuration

Environment variables (all optional):

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port (binds 0.0.0.0). |
| `LOOP_DATA_DIR` | `./data` | Media files when `LOOP_SUPABASE_SERVICE_KEY` is unset, the SQLite database when `LOOP_DATABASE_URL` is unset, and temporary upload files (`tmp/`). |
| `LOOP_DATABASE_URL` | unset (SQLite) | PostgreSQL connection string (Supabase session pooler, `postgresql://user:password@host:5432/postgres`, or a `jdbc:postgresql://` URL). Secret. Tables live in the schema `loop`, created by `server/supabase/migrations/`; see `server/README.md`. |
| `LOOP_DATABASE_POOL_SIZE` | `5` | Maximum PostgreSQL connections (1-50). |
| `LOOP_SUPABASE_SERVICE_KEY` | unset (local media) | Secret (`sb_secret_...`) or `service_role` key of the Supabase project. Secret. When set, videos, thumbnails and avatars are stored in Supabase Storage and DTOs carry the bucket's public URLs; see `server/README.md`. |
| `LOOP_SUPABASE_URL` | `https://qzqkxwtbajifzsdkotud.supabase.co` | Supabase project URL. |
| `LOOP_SUPABASE_BUCKET` | `loop-media` | Public Storage bucket for media. |
| `LOOP_JWT_SECRET` | dev-only constant (logged warning) | HMAC secret for tokens. |
| `LOOP_SEED` | `true` | Seed demo users, videos (generated with ffmpeg), follows, likes and comments when the database is empty. |
| `LOOP_FFMPEG` / `LOOP_FFPROBE` | `ffmpeg` / `ffprobe` | Tool paths. Without them the server still runs; uploads get no thumbnail and seeding skips media generation. |
