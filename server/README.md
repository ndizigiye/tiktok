# Loop server

Ktor 3 (Netty) + Exposed over SQLite. The REST contract is `docs/api.md`; the DTOs are
shared with the Android app through `:shared`.

## Run

From the repository root:

```sh
./gradlew -p server run      # http://0.0.0.0:8080, data in server/data
./gradlew -p server test     # integration tests (temporary data directories, no seeding)
```

Configuration is read from the environment; everything is optional.

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port (binds 0.0.0.0). |
| `LOOP_DATA_DIR` | `./data` (`server/data` with `gradlew run`) | `loop.db` plus `videos/`, `thumbs/` and `avatars/`. |
| `LOOP_JWT_SECRET` | development constant, with a warning in the log | HMAC secret for tokens. Set it for anything beyond local use. |
| `LOOP_SEED` | `true` | Seed demo data when the database is empty. |
| `LOOP_FFMPEG` / `LOOP_FFPROBE` | `ffmpeg` / `ffprobe` | Tool paths for thumbnails, probing and demo clips. |

The Android emulator reaches the host at `http://10.0.2.2:8080/` (the app's default).
On a device, set the server URL in the app's settings to `http://<your-computer's-LAN-IP>:8080/`.

## Demo data

With `LOOP_SEED=true` and an empty database the server starts answering immediately and
seeds in the background:

- 8 fictional creators, all with the password **`loop1234`**:
  `mira.makes`, `tobi_trails`, `nightowl.beats`, `pixel.pantry`, `lena.lifts`,
  `quietcoder`, `dot.and.line`, `fieldnotes.ari`.
- 24 portrait clips (540x960, 6-12 s, H.264 + AAC, `+faststart`) rendered locally from
  ffmpeg's built-in test sources (`testsrc2`, `mandelbrot`, `life`, `cellauto`,
  `smptehdbars`, `rgbtestsrc`, `gradients`, `geq`, waveforms of the clip's own tone, ...)
  with generated tones as audio, plus thumbnails and gradient avatars. No third-party media.
- Shared sounds, a follow graph, likes, bookmarks, watch history, comments with replies and
  comment likes, view and share counts, and the matching inbox notifications.

Clips render in parallel (one ffmpeg process per CPU) and appear in the feed as they finish;
progress is logged. Seeding is resumable: files that already exist are reused and rows that
already exist are skipped, so an interrupted run completes on the next start. Without ffmpeg
the accounts and follow graph are still created and a warning explains why there are no
videos. To start over, stop the server and delete the data directory.

## Layout

| Package | Contents |
|---|---|
| `server` | `Application.kt` (`main`, `Application.module(config)`), `ServerConfig`, `LoopServices`, `ApiException` |
| `plugins` | JSON, JWT auth (`authenticate("jwt")`, optional for `[opt]` routes), status pages, CORS, logging, partial content |
| `routes` | HTTP routes, thin wrappers over the services |
| `service` | users, videos, comments, discover/search, notifications, uploads, DTO assembly |
| `feed` | For You ranking (`ForYouRanker`, pure) and passes/cursors (`ForYouService`) |
| `db` | Exposed tables and the SQLite wrapper |
| `media` | file storage, multipart uploads, ffmpeg/ffprobe |
| `seed` | demo content and the background seeder |
| `web` | the `/v/{id}` share page |

Notes:

- All database work runs on one dedicated thread (`LoopDatabase.tx`), which serialises
  SQLite writers so concurrent requests never see "database is locked". WAL and foreign keys
  are on; deletes cascade through the schema.
- Counts in DTOs (likes, comments including replies, bookmarks, followers, videos, likes
  received) are computed with grouped aggregate queries per page, so they are correct after
  every mutation. Views, shares and completions are counters on the video row.
- Passwords use PBKDF2-HMAC-SHA256 with a per-user salt. Tokens are signed with the
  configured secret combined with a random key stored in the database, so deleting the
  data directory invalidates old tokens.
- The For You feed ranks every candidate once per pass (cursor = seed + offset), keeps the
  pass in memory so pages stay stable, and starts a new pass when it runs out.
