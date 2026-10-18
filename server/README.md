# Loop server

Ktor 3 (Netty) + Exposed over SQLite, or over PostgreSQL (Supabase) when `LOOP_DATABASE_URL`
is set. Media (videos, thumbnails, avatars) lives in the data directory, or in a Supabase Storage
bucket when `LOOP_SUPABASE_SERVICE_KEY` is set. The REST contract is `docs/api.md`; the DTOs are
shared with the Android app through `:shared`.

## Run

From the repository root:

```sh
./gradlew -p server run      # http://0.0.0.0:8080, data in server/data
./gradlew -p server test     # integration tests (temporary data directories, no seeding)
LOOP_TEST_DATABASE_URL=... ./gradlew -p server testPostgres   # the same tests on PostgreSQL
```

Configuration is read from the environment; everything is optional.

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port (binds 0.0.0.0). |
| `LOOP_DATA_DIR` | `./data` (`server/data` with `gradlew run`) | `loop.db` (SQLite mode), `videos/`, `thumbs/` and `avatars/` (local media mode) and `tmp/` (uploads and renders in progress, always). |
| `LOOP_DATABASE_URL` | unset: SQLite | PostgreSQL instead of SQLite, see Supabase below. **Secret** (contains the password). |
| `LOOP_DATABASE_POOL_SIZE` | `5` | Maximum PostgreSQL connections (1-50). |
| `LOOP_SUPABASE_SERVICE_KEY` | unset: media on local disk | Media in Supabase Storage instead, see Supabase below. **Secret** (the project's secret / `service_role` key). |
| `LOOP_SUPABASE_URL` | `https://qzqkxwtbajifzsdkotud.supabase.co` | Supabase project URL (only used with the key). |
| `LOOP_SUPABASE_BUCKET` | `loop-media` | Storage bucket for media (only used with the key). |
| `LOOP_JWT_SECRET` | development constant, with a warning in the log | HMAC secret for tokens. Set it for anything beyond local use. |
| `LOOP_SEED` | `true` | Seed demo data when the database is empty. |
| `LOOP_FFMPEG` / `LOOP_FFPROBE` | `ffmpeg` / `ffprobe` | Tool paths for thumbnails, probing and demo clips. |

The Android emulator reaches the host at `http://10.0.2.2:8080/` (the app's default).
On a device, set the server URL in the app's settings to `http://<your-computer's-LAN-IP>:8080/`.

## Supabase

Production data lives in the Supabase project **loop** (ref `qzqkxwtbajifzsdkotud`, region
eu-central-1, free plan, API URL `https://qzqkxwtbajifzsdkotud.supabase.co`). The Ktor server
stays the only backend: it serves the API of `docs/api.md` and is the only client of Supabase. The
Android app never calls Supabase and never sees a key; it only loads media from the bucket's public
URLs, which the server puts in the DTOs.

### What is stored where

| Data | Default | With the Supabase settings |
|---|---|---|
| Rows: accounts, videos, sounds, hashtags, follows, likes, bookmarks, comments, watch history, notifications, `meta` (token key, seeding state) | SQLite, `LOOP_DATA_DIR/loop.db` | `LOOP_DATABASE_URL`: Supabase Postgres, schema `loop` (14 tables, nothing in `public`) |
| Videos, thumbnails, avatars | `LOOP_DATA_DIR/videos`, `thumbs`, `avatars`, served at `/media/...` | `LOOP_SUPABASE_SERVICE_KEY`: Storage bucket `loop-media`, objects `videos/<random>.<ext>`, `thumbs/<random>.jpg`, `avatars/<userId>-<random>.<ext>` (demo media: `videos/demo-NN.mp4`, `thumbs/demo-NN.jpg`, `avatars/demo-<user>.jpg`) |
| Uploads and renders in progress | `LOOP_DATA_DIR/tmp` | the same: ffprobe/ffmpeg work on local files, which are deleted afterwards |

The two settings are independent: Storage works with SQLite, PostgreSQL with local media. With
neither, the server runs exactly as before (SQLite and local disk), which is what
`./gradlew -p server run` and the tests use.

How the project is locked down:

- The schema `loop` is not one of the Data API's exposed schemas, row level security is on for every
  table without any policy, and `anon`, `authenticated` and `PUBLIC` have no privilege on the
  schema, its tables or its sequences. The publishable key therefore reaches nothing. `postgres`
  (the server's login) and `service_role` have every privilege; both bypass row level security.
- The bucket is public: anyone with an object's URL can read it, which is how the app plays media.
  `storage.objects` has no policies, so listing, uploading and deleting need the secret key. Per-file
  limit 50 MB (the free plan's maximum, equal to `ApiLimits.MAX_UPLOAD_BYTES`); allowed MIME types
  `video/mp4`, `video/webm`, `video/3gpp`, `video/quicktime`, `image/jpeg`, `image/png`,
  `image/webp`.

State of the project (applied 2026-10-18; the file names in `supabase/migrations/` are the
versions in the project's migration history):

| Version | Name | Contents |
|---|---|---|
| `20261018090945` | `loop_schema` | schema `loop`: 14 tables with their primary keys, 2 unique constraints, 24 foreign keys and 16 indexes; row level security; privileges |
| `20261018092325` | `comments_author_id_index` | index `comments_author_id`, the one foreign key the first migration left uncovered |

Advisors after both migrations:

- Security: `rls_enabled_no_policy` (INFO) for the 14 tables. Intended: no policy means no access
  for the Data API roles, which have no privileges either; the server's role bypasses row level
  security. See [lint 0008](https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy).
- Performance: `unused_index` (INFO) for all 17 secondary indexes, because the database has served
  no queries yet. They back the foreign-key cascades and the server's lists; check again after real
  use before dropping any. See [lint 0005](https://supabase.com/docs/guides/database/database-linter?lint=0005_unused_index).
  `unindexed_foreign_keys` (`comments.author_id`) was fixed by the second migration.

### Configuration

| Variable | Secret | Where to find the value |
|---|---|---|
| `LOOP_DATABASE_URL` | yes | Dashboard, project **loop**, **Connect** (top bar), connection string, method **Session pooler**, URI: `postgresql://postgres.qzqkxwtbajifzsdkotud:[YOUR-PASSWORD]@aws-<n>-eu-central-1.pooler.supabase.com:5432/postgres`. Put the database password in place of `[YOUR-PASSWORD]`. The dashboard does not show the password; without it, set a new one under **Project Settings, Database, Reset database password** (anything else that used the old password stops working). |
| `LOOP_SUPABASE_SERVICE_KEY` | yes | **Project Settings, API Keys**: a secret key (`sb_secret_...`; create one, for example `loop-server`, and reveal it), or the legacy `service_role` key on the legacy keys tab. Never the publishable or anon key: the server refuses those at startup. |
| `LOOP_SUPABASE_URL` | no | Defaults to `https://qzqkxwtbajifzsdkotud.supabase.co`, the project URL (Connect dialog, or Project Settings, Data API). Set it only for another project. |
| `LOOP_SUPABASE_BUCKET` | no | Defaults to `loop-media` (Storage, Buckets). |
| `LOOP_JWT_SECRET` | yes | Not from Supabase: a long random string of your own. Keep it stable; changing it signs everyone out. |

About the connection string:

- Use the **session pooler** (port 5432, IPv4). Not the transaction pooler (port 6543): the
  server relies on session state (search path, prepared statements). Not the direct connection
  `db.qzqkxwtbajifzsdkotud.supabase.co`: on the free plan it is IPv6 only.
- Percent-encode special characters in the password if you can; unencoded ones work too, because
  the credentials end at the last `@`. A `jdbc:postgresql://` URL (credentials as `user`/`password`
  parameters) is accepted as well.
- Supabase bans an IP for 30 minutes after two failed logins in a row. If the server reports
  "connection refused" after a wrong password, unban the IP under Database settings, Banned IPs.

Secrets go into the environment of the process that runs the server: never into a file in the
repository, never into chat. In a Claude Code cloud environment, add them as environment variables
in the environment's settings (cloud environment menu in the session's title bar, Edit); a new
session picks them up. Check for them without printing them, e.g.
`[ -n "$LOOP_DATABASE_URL" ] && echo set`.

### Network access from a sandbox

| Host | Port | Used for |
|---|---|---|
| `qzqkxwtbajifzsdkotud.supabase.co` | 443, HTTPS | Storage API (uploads, deletes, the startup bucket check) and the public media URLs |
| `aws-<n>-eu-central-1.pooler.supabase.com` (the host of your connection string) | 5432, PostgreSQL protocol over TLS, not HTTP | the database, through the session pooler |
| `api.supabase.com` | 443, HTTPS | only the Supabase CLI (`supabase link`, `supabase migration list`, ...) |

In a Claude Code cloud environment, add the HTTPS hosts under Network access, Allowed domains
(keep "Allow package managers" ticked: Gradle needs Maven Central). The proxy behind that list
carries HTTPS only, not database connections, so the database also needs an environment that may
open TCP connections to the pooler's port 5432 directly; the preflight of
`docs/supabase-live-check.md` tests both. The Storage client (the JDK HTTP client) follows the
JVM's proxy settings (`https.proxyHost`, `http.nonProxyHosts`, as such sandboxes set them in
`JAVA_TOOL_OPTIONS`); JDBC always connects directly. The Supabase MCP tools need none of these
hosts.

### Applying the migrations to a fresh project

Apply every file of `supabase/migrations/` in name order, with one of:

- **Supabase CLI**, from `server/`: `supabase login`, `supabase init` (adds the CLI's
  `supabase/config.toml`; do not commit what it adds), `supabase link --project-ref <ref>` (asks
  for the database password), then `supabase db push`. `supabase migration list`
  compares the files with the project's history; on project `loop` both are already applied.
- **SQL editor** in the dashboard: paste each file, in order, and run it. This does not write the
  migration history; record it afterwards with
  `supabase migration repair --status applied 20261018090945 20261018092325`, so that a later
  `supabase db push` does not apply them again.
- **psql**: `psql "<session pooler URI>" -v ON_ERROR_STOP=1 -f supabase/migrations/<file>` per file
  (psql needs the password percent-encoded); record the history as for the SQL editor.
- **Supabase MCP** (`apply_migration`, name = the part of the file name after the timestamp). It
  records the time of applying as the version, so rename the file to the version `list_migrations`
  reports, as was done for project `loop`.

Then create the bucket: Storage, New bucket, name `loop-media`, Public bucket on, file size limit
50 MB, allowed MIME types as above. Or in the SQL editor:

```sql
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('loop-media', 'loop-media', true, 52428800,
        array['video/mp4', 'video/webm', 'video/3gpp', 'video/quicktime', 'image/jpeg', 'image/png', 'image/webp']);
```

The server checks both at startup and exits with instructions when the schema is missing or
differs from `db/Tables.kt`, or when the bucket is missing or not public.

Schema changes: edit `db/Tables.kt`, print the new DDL with
`LOOP_DATABASE_URL=<any PostgreSQL> ./gradlew -p server printPostgresSchema` (it only reads
dialect details from the connection) and add a new migration with the difference, as
`*_comments_author_id_index.sql` does. The block between `generated:begin` and `generated:end` in
the first migration is that task's output for the original tables. Apply a new migration to the
project before deploying a server that expects it: the startup check refuses to run on an older
schema. `testPostgres` verifies that all migrations together equal the Exposed tables.

### Running the server against Supabase

With `LOOP_DATABASE_URL`, `LOOP_SUPABASE_SERVICE_KEY` and `LOOP_JWT_SECRET` in the environment:

```sh
LOOP_SEED=true ./gradlew -p server run
```

Startup logs (no secrets):

```
Loop database: PostgreSQL aws-<n>-eu-central-1.pooler.supabase.com:5432/postgres (user postgres.qzqkxwtbajifzsdkotud, sslmode require, schema loop, pool 5)
Loop media: Supabase Storage https://qzqkxwtbajifzsdkotud.supabase.co, bucket loop-media
```

On an empty `loop` schema the server seeds in the background (see Demo data): it renders the clips
locally and uploads 54 objects (24 clips, 24 thumbnails, 6 avatars, about 31 MB), then logs
`Demo seeding finished ...`. Seeding only ever runs on a database without accounts. `LOOP_SEED`
defaults to `true`, so start with `LOOP_SEED=false` to keep production free of demo content. Point
the app at the server as usual; it plays media straight from the bucket.

`docs/supabase-live-check.md` is the full check against the real project: row and object counts,
Range requests on public URLs, an end-to-end pass through the API, the JVM test suites, and how to
remove test and demo data.

### Database details

- Credentials go to the driver separately; the JDBC URL, logs, `toString()` and error messages never
  contain the password. Startup logs `Loop database: PostgreSQL <host>/<db> (...)`.
- Remote hosts always get TLS (`sslmode=require`, or a stricter `verify-ca`/`verify-full` from the
  URL); `localhost` keeps the driver default. Every connection uses `currentSchema=loop` and
  `SET search_path TO loop`, since a pooler may drop the startup parameter.
- A HikariCP pool of up to `LOOP_DATABASE_POOL_SIZE` connections (one kept when idle; with the
  session pooler every client connection holds a server connection). Transactions run concurrently
  at READ COMMITTED, one per pooled connection; writes that can race are single statements
  (`INSERT ... ON CONFLICT`, `count = count + 1`), and Exposed retries a transaction that fails with
  a deadlock or serialization error.
- The server never creates or alters tables in PostgreSQL. At startup it compares the database with
  the Exposed tables (`SchemaUtils.statementsRequiredToActualizeScheme`) and exits with a message
  naming the migrations when the schema is missing or incomplete.

### Storage details

- Rows store file names; DTOs carry the public URL
  `<url>/storage/v1/object/public/<bucket>/<path>`, which the app loads directly (no key; the app
  passes absolute URLs through `SessionStore.resolveUrl` unchanged and only sends its own token to
  the Loop server). `/media/*` is not served in this mode.
- Uploads are received into `LOOP_DATA_DIR/tmp`, probed and thumbnailed there with ffprobe/ffmpeg,
  then sent with `POST /storage/v1/object/<bucket>/<path>` (headers `Authorization: Bearer <key>`,
  `apikey: <key>`, the file's `Content-Type`, `Cache-Control: max-age=604800`, `x-upsert: false`;
  the demo seeder uses `x-upsert: true` for its fixed names). The body streams from the file, so a
  50 MB video is never held in memory. The temporary files are deleted afterwards, also when
  anything fails; files a crash left behind are removed at the next start.
- Deleting a video, or replacing an avatar, deletes the old objects with
  `DELETE /storage/v1/object/<bucket>` (`{"prefixes": [...]}`). A failed delete is logged and leaves
  an orphaned object; the request still succeeds. On the free plan a delete does not purge
  Supabase's CDN: a copy the CDN cached earlier stays reachable at the old (unguessable) URL until
  the cached copy expires. Smart CDN (Pro plan and above) invalidates it within about a minute.
- Requests use the JDK HTTP client with a 10 s connect timeout and per-request timeouts. Network
  errors, 429 and 5xx answers (including Storage's 544) are retried twice, after 0.5 s and 2 s.
- Storage's size error (`413`, also when reported as HTTP 400 with `"statusCode": "413"`) becomes
  the API's `413 too_large`. Other Storage errors are logged with their status and message (never
  the key) and answered with `500 internal`.
- Startup checks the bucket (`GET /storage/v1/bucket/<bucket>`) and exits with instructions when the
  key is rejected or the bucket is missing or not public; it warns when the bucket's size limit or
  allowed MIME types would refuse some uploads.
- The key is never logged: `toString()`, startup lines and error messages leave it out, and Storage
  error bodies are redacted before they are logged.

Media is not copied when switching modes: rows keep their file names, and URLs point at the current
store. To move existing local media, upload `data/videos`, `data/thumbs` and `data/avatars` to the
same paths in the bucket (for example with the dashboard).

### Tests

`./gradlew -p server test` never uses Supabase, even with the variables above set: every test
builds its own configuration. Storage tests use `FakeSupabaseStorage` (a JDK `HttpServer` on
127.0.0.1 that implements the endpoints above); `supabaseLoopTest { }` runs a test server against
it. `testPostgres` runs the whole suite on PostgreSQL: `LOOP_TEST_DATABASE_URL` names a role that
may create databases (e.g. `postgresql://loop_test:<password>@localhost:5432/postgres`); every test
creates its own database, applies `supabase/migrations/`, and drops it afterwards. `PostgresTest`
(PostgreSQL only) checks that the migrations match the Exposed tables, row level security,
privileges and the startup errors.

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

Clips render locally in parallel (one ffmpeg process per CPU), go to the media store (the data
directory or Supabase Storage) and appear in the feed as they finish; progress is logged.
Seeding is resumable: rows that already exist are skipped, and a clip or avatar whose row is
missing is rendered again and stored under its fixed name (`videos/demo-01.mp4`,
`avatars/demo-<user>.jpg`), replacing any copy an interrupted run left. Without ffmpeg the
accounts and follow graph are still created and a warning explains why there are no videos.
The seeded rows are the same on every run (fixed random seeds); `docs/supabase-live-check.md` lists
the counts. To start over, stop the server and delete the data directory. With PostgreSQL, empty the
tables instead of dropping the schema, so the migration history stays valid, and with Supabase
Storage also empty the bucket: `docs/supabase-live-check.md` (step 6) has the statements, and also
removes only the demo accounts and their media when real accounts exist.

## Layout

| Package | Contents |
|---|---|
| `server` | `Application.kt` (`main`, `Application.module(config)`), `ServerConfig`, `LoopServices`, `ApiException` |
| `plugins` | JSON, JWT auth (`authenticate("jwt")`, optional for `[opt]` routes), status pages, CORS, logging, partial content |
| `routes` | HTTP routes, thin wrappers over the services |
| `service` | users, videos, comments, discover/search, notifications, uploads, DTO assembly |
| `feed` | For You ranking (`ForYouRanker`, pure) and passes/cursors (`ForYouService`) |
| `paging` | `PageRequest`, opaque cursors (`Cursors`), keyset orders (`KeysetOrder`, `RankedOrder`) and offset paging |
| `db` | Exposed tables, `LoopDatabase` (SQLite or PostgreSQL), `PostgresSettings` (URL parsing), `PostgresSchema` (migration DDL, startup check) |
| `media` | `MediaStore` (`LocalMediaStore`, `SupabaseMediaStore` and its settings), `MediaStaging` (temporary files), multipart uploads, ffmpeg/ffprobe |
| `seed` | demo content and the background seeder |
| `web` | the `/v/{id}` share page |

Notes:

- With SQLite all database work runs on one dedicated thread (`LoopDatabase.tx`), which
  serialises writers so concurrent requests never see "database is locked". WAL and foreign
  keys are on; deletes cascade through the schema. PostgreSQL runs transactions concurrently
  (see above), so the services never rely on that serialisation.
- Text containing U+0000 is rejected (PostgreSQL cannot store it), on both backends.
- Counts in DTOs (likes, comments including replies, bookmarks, followers, videos, likes
  received) are computed with grouped aggregate queries per page, so they are correct after
  every mutation. Views, shares and completions are counters on the video row.
- Passwords use PBKDF2-HMAC-SHA256 with a per-user salt. Tokens are signed with the
  configured secret combined with a random key stored in the database, so deleting the
  database (the data directory, or the `loop` schema) invalidates old tokens.
- Lists page by keyset: the cursor holds the last row's sort values and id (`KeysetOrder`;
  `RankedOrder` for top-level comments, whose like count is an aggregate filtered in `HAVING`),
  so rows deleted between pages never cause a skip or a repeat. Hashtag and sound videos
  (most viewed first, a ranking that changes with every view) use offsets.
- The For You feed ranks every candidate once per pass (cursor = seed + offset), keeps the
  pass in memory so pages stay stable, and starts a new pass when it runs out.
