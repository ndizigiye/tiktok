# Loop

Loop is an original short-video app for Android, with its own backend. It has
the familiar interaction model: a full-screen vertical feed you swipe through,
likes (including double tap), comments with replies, sharing, discovery and
search, in-app camera recording with segments, uploads, an inbox and creator
profiles.

Loop is not a copy of any existing app. It has its own name, design, code and
backend, and it contains no code, assets or branding taken from another
product. Where it follows another app's behaviour (for example how far ahead
videos are preloaded), the observation is recorded in
[docs/feature-analysis.md](docs/feature-analysis.md) and the implementation was
written from that description.

## What's in the repository

| Path | Contents |
|---|---|
| `app/` | Android app: Kotlin, Jetpack Compose, Media3 (playback with a 3-player pool and buffer-gated prefetch), CameraX (recording), Retrofit/OkHttp, Coil |
| `server/` | Ktor server: REST API, JWT auth, feed ranking, notifications, demo seeding with generated clips. Stores rows in SQLite, or in PostgreSQL / Supabase, and media on local disk or in Supabase Storage |
| `shared/` | API DTOs and small helpers used by both (caption parsing, count formatting) |
| `scripts/` | `e2e-server.sh` (seeded server for the end-to-end tests), `device-screenshots.sh` |
| `docs/` | [api.md](docs/api.md) (REST contract), [architecture.md](docs/architecture.md), [feature-analysis.md](docs/feature-analysis.md) and [rea-playbook.md](docs/rea-playbook.md) (the REA investigation), [supabase-live-check.md](docs/supabase-live-check.md) |

## Run it

Requirements: JDK 17+ (Android Studio's bundled JDK works), the Android SDK
(platform 35), and `ffmpeg`/`ffprobe` on the PATH for thumbnails and demo clips.

1. **Start the server** from the repository root:

   ```sh
   ./gradlew -p server run
   ```

   It listens on port 8080, keeps its data in `server/data`, and on first start
   seeds 8 demo creators and 24 generated clips. All demo accounts use the
   password `loop1234` (for example `mira.makes`). See
   [server/README.md](server/README.md) for every setting.

2. **Run the app**: open the repository in Android Studio and run the `app`
   configuration on an emulator. The app's default server address,
   `http://10.0.2.2:8080/`, is the emulator's route to your computer. On a
   phone, set your computer's LAN address under Me → menu → Settings → Server.

### Storing the data in Supabase

The server keeps all rows in the `loop` schema of a Supabase Postgres database
and all media in the Storage bucket `loop-media` when two environment
variables are set:

- `LOOP_DATABASE_URL`: the session pooler connection string;
- `LOOP_SUPABASE_SERVICE_KEY`: a secret API key, used by the server only.

The schema migrations are in `server/supabase/migrations/`. The tables have
row-level security turned on and no grants to the public roles, so only the
Loop server can read or write them. The Android app never talks to Supabase
directly. Setup steps are in [server/README.md](server/README.md#supabase);
[docs/supabase-live-check.md](docs/supabase-live-check.md) is the checklist for
the first run against a live project.

## Tests

| Command | What it runs |
|---|---|
| `./gradlew :app:testDebugUnitTest` | App unit tests: the data layer against MockWebServer, interceptors, the session store, the paginator |
| `./gradlew -p server test` | Server integration tests on SQLite |
| `LOOP_TEST_DATABASE_URL=… ./gradlew -p server testPostgres` | The same server tests on PostgreSQL, using the Supabase migrations |
| `scripts/e2e-server.sh start`, then `./gradlew :app:testDebugUnitTest -Ploop.e2e.baseUrl=<printed URL>` | 35 end-to-end UI tests (Robolectric + Compose) driving the real app against a live seeded server and checking the server's data after each action |
| `./gradlew :app:connectedDebugAndroidTest` | Device tests: real playback, swiping, sign-up, camera recording, background upload, deep links, picture-in-picture, server-down recovery |

Every feature in [docs/architecture.md](docs/architecture.md) is covered by
the end-to-end tests above. The device setup, including the workarounds for
software-only emulators, is described under "Tests" in that document.
