# Supabase live check

A checklist for verifying the Loop server against the real Supabase project **loop** (ref
`qzqkxwtbajifzsdkotud`, eu-central-1, API URL `https://qzqkxwtbajifzsdkotud.supabase.co`). It is
written for a new Claude Code session that has the secrets in its environment, but a person can
follow it the same way. Background (what is stored where, configuration, migrations):
`server/README.md`, section "Supabase".

To use it in a new session, paste:

> In /home/user/tiktok (branch loop-app), follow docs/supabase-live-check.md step by step against
> the Supabase project qzqkxwtbajifzsdkotud. Never print, log or commit LOOP_DATABASE_URL,
> LOOP_SUPABASE_SERVICE_KEY or anything derived from them. Do not change git state. Stop every
> process you start. Report the results of every step, including failures, and do not stand in for
> a step that cannot run with made-up results.

## Ground rules

- **Secrets.** `LOOP_DATABASE_URL` (contains the database password) and `LOOP_SUPABASE_SERVICE_KEY`
  must never appear in output, files, commits or chat. Do not run `env`, `printenv`, `set -x`,
  `echo $LOOP_...` or `cat /proc/*/environ`; check for presence with the preflight below. They are
  set in the environment's settings (cloud environment menu in the session's title bar, Edit,
  environment variables), never pasted into the conversation.
- **Production.** This is the real project. Every write below is listed in the cleanup steps; do not
  run other DDL or deletes. Schema changes go through `server/supabase/migrations/`, never ad hoc.
- **Processes.** Stop the server at the end (step 7).

## Prerequisites

| What | Check |
|---|---|
| `LOOP_DATABASE_URL`: session pooler URI, `postgresql://postgres.qzqkxwtbajifzsdkotud:<password>@aws-<n>-eu-central-1.pooler.supabase.com:5432/postgres` (a `jdbc:postgresql://` URL works too) | preflight |
| `LOOP_SUPABASE_SERVICE_KEY`: secret key `sb_secret_...` or legacy `service_role` key | preflight |
| HTTPS to `qzqkxwtbajifzsdkotud.supabase.co` (allowed domain) | preflight |
| Direct TCP to the pooler host on port 5432. An allowed domain alone is not enough: the sandbox proxy carries HTTPS, not database connections | preflight |
| Supabase MCP tools connected to project `qzqkxwtbajifzsdkotud` (load them with ToolSearch query `supabase`) | `list_migrations` |
| JDK 17+, `ffmpeg`/`ffprobe` (demo clips and thumbnails), `curl`, `jq`, `python3` | `which java ffmpeg ffprobe curl jq python3` |

Optional: `LOOP_SUPABASE_URL` (defaults to the project URL above) and `LOOP_SUPABASE_BUCKET`
(defaults to `loop-media`).

## 0. Preflight

Every block below starts in the repository root and sources `/tmp/loop-live/env.sh`, because a
Claude Code session runs each command in a new shell. The file holds no secret values: the key is
expanded from the environment when `storage` runs, and reaches curl through a pipe (`-H @<(...)`,
bash), so it is not in curl's command line either, where `ps` would show it. Nothing in this step
prints a secret.

```sh
cd /home/user/tiktok
mkdir -p /tmp/loop-live
cat > /tmp/loop-live/env.sh <<'EOF'
SUPA="${LOOP_SUPABASE_URL:-https://qzqkxwtbajifzsdkotud.supabase.co}"
BUCKET="${LOOP_SUPABASE_BUCKET:-loop-media}"
BASE=http://127.0.0.1:8080
# Storage API with the service key, which only ever travels in these headers.
storage() {
  curl -sS -H @<(printf 'Authorization: Bearer %s\napikey: %s\n' "$LOOP_SUPABASE_SERVICE_KEY" "$LOOP_SUPABASE_SERVICE_KEY") \
    -H 'Content-Type: application/json' "$@"
}
EOF
. /tmp/loop-live/env.sh

for v in LOOP_DATABASE_URL LOOP_SUPABASE_SERVICE_KEY; do
  if [ -n "${!v:-}" ]; then echo "$v is set"; else echo "$v is MISSING"; fi
done

# host:port of the database (no credentials)
DB_HOSTPORT=$(python3 -I -c '
import os
url = os.environ["LOOP_DATABASE_URL"].strip()
rest = url.split("://", 1)[1]
if url.lower().startswith("jdbc:"):
    hostport = rest.split("/", 1)[0].split("?", 1)[0]
else:
    hostport = rest.rsplit("@", 1)[-1].split("/", 1)[0].split("?", 1)[0]
print(hostport if ":" in hostport else hostport + ":5432")')
echo "database at $DB_HOSTPORT"
timeout 10 bash -c "exec 3<>/dev/tcp/${DB_HOSTPORT%:*}/${DB_HOSTPORT##*:}" \
  && echo "database port reachable" || echo "database port NOT reachable"

# the server's own startup check: key accepted, bucket exists
storage -o /dev/null -w 'storage bucket check: HTTP %{http_code}\n' "$SUPA/storage/v1/bucket/$BUCKET"
```

Expected: both variables set, the database host is `aws-<n>-eu-central-1.pooler.supabase.com:5432`,
the port is reachable, the bucket check answers `HTTP 200`.

- Port not reachable: the environment does not allow direct TCP to the pooler. Report it and
  stop; the server cannot start against Supabase from here.
- Bucket check `HTTP 000` with `CONNECT tunnel failed, response 403`, or another connect or TLS
  error: `qzqkxwtbajifzsdkotud.supabase.co` is not an allowed domain. `400`, `401` or `403` with a
  JSON body from Supabase: the key is wrong (a publishable key, or another project's) or the bucket
  is missing. The body holds no key and names the reason:
  `storage "$SUPA/storage/v1/bucket/$BUCKET" | jq -c '{statusCode, error, message}'`.
  `Bucket not found` with a bucket that exists (`execute_sql`:
  `select id, public, file_size_limit from storage.buckets;`) means a key that cannot see it.
- Port 6543 or host `db.qzqkxwtbajifzsdkotud.supabase.co`: the wrong connection string (transaction
  pooler, or the IPv6-only direct connection). Use Connect, Session pooler.

Then, with the Supabase MCP (`project_id` `qzqkxwtbajifzsdkotud`):

1. `list_migrations` shows exactly the files in `server/supabase/migrations/`:
   `20261018090945 loop_schema` and `20261018092325 comments_author_id_index`. If the project has
   fewer, stop and report; do not apply anything unless the user asked for it.
2. `execute_sql`: `select count(*) from loop.users;` is `0` for a first seeding. If it is not,
   the server skips seeding (it logs "Database already has accounts; demo seeding skipped." or
   "Demo data was seeded before; demo seeding skipped."), and the counts of steps 2 and 3 differ
   from the tables below; either check the existing data instead or clean up first (step 6.3)
   when the user agrees.

## 1. Build and start the server against Supabase, with seeding

```sh
cd /home/user/tiktok && ./gradlew -p server installDist
```

Start the server as a background job (Claude Code: this block as one Bash call with
`run_in_background`; a terminal: run it in a second window). `exec` keeps the shell's PID, so the
PID file names the server's JVM:

```sh
cd /home/user/tiktok
echo $$ > /tmp/loop-live/server.pid
LOOP_SEED=true PORT=8080 LOOP_DATA_DIR=/tmp/loop-live/data LOOP_JWT_SECRET="$(head -c 32 /dev/urandom | base64)" \
  exec server/build/install/loop-server/bin/loop-server > /tmp/loop-live/server.log 2>&1
```

Then wait for the end of seeding, for the server to exit, or ten minutes (give the command a
timeout of at least that):

```sh
for i in $(seq 1 200); do
  grep -qE 'Demo seeding (finished|failed)|demo seeding skipped' /tmp/loop-live/server.log 2>/dev/null && break
  kill -0 "$(cat /tmp/loop-live/server.pid)" 2>/dev/null || { echo "server exited"; break; }
  sleep 3
done
grep -E 'Loop (database|media)|[Ss]eeding|Generating|Demo clip|WARN|ERROR' /tmp/loop-live/server.log
```

Seeding takes about a minute of rendering with 4 CPUs plus the upload time. If the server
exited, the end of the log names the reason (see the table below).

Expected log lines, in this order:

```
Loop database: PostgreSQL aws-<n>-eu-central-1.pooler.supabase.com:5432/postgres (user postgres.qzqkxwtbajifzsdkotud, sslmode require, schema loop, pool 5)
Loop media: Supabase Storage https://qzqkxwtbajifzsdkotud.supabase.co, bucket loop-media
Seeding demo data into the empty database.
Generating 24 demo clips with ffmpeg (<n> at a time)...
Demo clip 1/24 ... (24 lines)
Demo seeding finished in <s> s: 8 accounts (password 'loop1234'), 24 new videos.
```

The seeder renders the clips locally and uploads 54 objects (about 31 MB). There must be no
`WARN` about the bucket's size limit or MIME types and no `ERROR`.

Make sure the log holds no secret (prints a count only):

```sh
python3 -I - /tmp/loop-live/server.log <<'EOF'
import os, sys, urllib.parse
log = open(sys.argv[1], encoding="utf-8", errors="replace").read()
url = os.environ["LOOP_DATABASE_URL"].strip()
secrets = {os.environ["LOOP_SUPABASE_SERVICE_KEY"].strip()}
if url.lower().startswith("jdbc:"):
    query = urllib.parse.parse_qs(urllib.parse.urlsplit(url[5:]).query)
    secrets.update(query.get("password", []))
else:
    userinfo = url.split("://", 1)[1].rsplit("@", 1)[0]
    if ":" in userinfo:
        password = userinfo.split(":", 1)[1]
        secrets.update({password, urllib.parse.unquote(password)})
secrets.discard("")
print("secret occurrences in the log:", sum(log.count(s) for s in secrets))
EOF
```

Expected: `secret occurrences in the log: 0`.

Startup errors and their meaning:

| Message | Cause |
|---|---|
| `The PostgreSQL database is not ready for Loop: ...` | migrations missing or incomplete (compare `list_migrations` with `server/supabase/migrations/`) |
| `password authentication failed` | wrong password in `LOOP_DATABASE_URL`. Do not retry blindly: Supabase bans an IP after two failed logins in a row (30 minutes; Dashboard, Database settings, Banned IPs, Unban) |
| `Tenant or user not found` | the user must be `postgres.qzqkxwtbajifzsdkotud` (session pooler string), not `postgres` |
| `Supabase Storage rejected LOOP_SUPABASE_SERVICE_KEY` | wrong key |
| `Supabase Storage has no bucket "..."` / `... is not public` | bucket missing or private (`LOOP_SUPABASE_BUCKET`, Storage settings) |
| `Supabase Storage at ... is not reachable` | network (allowed domains) |

## 2. Rows (SQL through the Supabase MCP)

`execute_sql`:

```sql
select 'users' as t, count(*) from loop.users
union all select 'sounds', count(*) from loop.sounds
union all select 'videos', count(*) from loop.videos
union all select 'hashtags', count(*) from loop.hashtags
union all select 'video_hashtags', count(*) from loop.video_hashtags
union all select 'follows', count(*) from loop.follows
union all select 'video_likes', count(*) from loop.video_likes
union all select 'bookmarks', count(*) from loop.bookmarks
union all select 'comments', count(*) from loop.comments
union all select 'comment_likes', count(*) from loop.comment_likes
union all select 'view_history', count(*) from loop.view_history
union all select 'not_interested', count(*) from loop.not_interested
union all select 'notifications', count(*) from loop.notifications
union all select 'meta', count(*) from loop.meta
union all select 'videos with thumbnail', count(*) from loop.videos where thumb_file is not null
union all select 'users with avatar', count(*) from loop.users where avatar_file is not null;
```

Expected after a first seeding. The seeder uses fixed random seeds, so these are exact; they were
measured with the same code on PostgreSQL, twice:

| t | count |
|---|---|
| users | 8 |
| sounds | 13 |
| videos | 24 |
| hashtags | 36 |
| video_hashtags | 60 |
| follows | 29 |
| video_likes | 111 |
| bookmarks | 32 |
| comments | 108 |
| comment_likes | 123 |
| view_history | 77 |
| not_interested | 0 |
| notifications | 394 |
| meta | 2 |
| videos with thumbnail | 24 |
| users with avatar | 6 |

Two of the eight demo accounts have no generated avatar (the app shows their colour). Without
ffmpeg the server creates the accounts and follows but no videos, and logs a warning saying so.

Also check (one `execute_sql` call per statement; a call shows one result):

```sql
select "key", case when "key" = 'demo_seed' then "value" else '<set>' end as value from loop.meta order by 1;
-- demo_seed = done, token_key = <set>

select count(*) from pg_tables where schemaname = 'public';
-- 0: nothing of Loop's outside the schema "loop"

select r.rolname, has_schema_privilege(r.oid, 'loop'::regnamespace, 'USAGE') as usage,
  (select count(*) from information_schema.role_table_grants g where g.table_schema = 'loop' and g.grantee = r.rolname) as table_grants
from pg_roles r where r.rolname in ('anon', 'authenticated');
-- usage false, table_grants 0 for both

select id, public, file_size_limit, allowed_mime_types,
  (select count(*) from pg_policies where schemaname = 'storage') as storage_policies
from storage.buckets;
-- one row: loop-media, true, 52428800, {video/mp4,video/webm,video/3gpp,video/quicktime,image/jpeg,image/png,image/webp}, 0
```

`get_advisors` (security and performance) should report nothing but the INFO lints described in
`server/README.md`: `rls_enabled_no_policy` for the 14 tables (intended), and `unused_index`
while the statistics are young. Anything else is a finding to report.

## 3. Storage objects and public URLs

`execute_sql`:

```sql
select split_part(name, '/', 1) as folder, count(*) as objects,
       pg_size_pretty(sum((metadata->>'size')::bigint)) as size,
       string_agg(distinct metadata->>'mimetype', ', ') as types,
       string_agg(distinct metadata->>'cacheControl', ', ') as cache_control
from storage.objects where bucket_id = 'loop-media' group by 1 order by 1;
```

Expected: `avatars` 6 (`image/jpeg`), `thumbs` 24 (`image/jpeg`), `videos` 24 (`video/mp4`), about
31 MB in all, cache control `max-age=604800`.

Every row has its object (all three counts 0):

```sql
select 'videos without object' as check, count(*) from loop.videos v where not exists
  (select from storage.objects o where o.bucket_id = 'loop-media' and o.name = 'videos/' || v.video_file)
union all
select 'thumbnails without object', count(*) from loop.videos v where v.thumb_file is not null and not exists
  (select from storage.objects o where o.bucket_id = 'loop-media' and o.name = 'thumbs/' || v.thumb_file)
union all
select 'avatars without object', count(*) from loop.users u where u.avatar_file is not null and not exists
  (select from storage.objects o where o.bucket_id = 'loop-media' and o.name = 'avatars/' || u.avatar_file);
```

The public URLs the API hands out answer a Range request with `206`, without any key:

```sh
. /tmp/loop-live/env.sh
curl -sS "$BASE/api/feed/for-you?limit=6" \
  | jq -r '.items[] | .videoUrl, .thumbnailUrl, (.author.avatarUrl // empty)' | sort -u \
  | while read -r url; do
      printf '%s  %s\n' "$(curl -sS -o /dev/null -r 0-1023 -w '%{http_code} %{content_type} %{size_download}B' "$url")" "$url"
    done
curl -sS -o /dev/null -D - -r 0-1023 "$SUPA/storage/v1/object/public/$BUCKET/videos/demo-01.mp4" \
  | grep -iE '^(HTTP|content-type|content-range|cache-control|accept-ranges)'
curl -sS -o /dev/null -w 'local /media route: HTTP %{http_code}\n' "$BASE/media/videos/demo-01.mp4"
```

Expected: every URL starts with
`https://qzqkxwtbajifzsdkotud.supabase.co/storage/v1/object/public/loop-media/` and answers
`206 video/mp4 1024B` (videos) or `206 image/jpeg ...` (thumbnails, avatars); the header dump shows
`206`, `content-range: bytes 0-1023/<size>` and `cache-control: max-age=604800`; the server's own
`/media/...` route answers `404` (media is not served locally in this mode).

## 4. End-to-end pass through the API

Uploads, avatar replacement, likes, comments, search and deletes against the running server, with
the media checked at its public URLs. It creates one account `livecheck.<timestamp>`; step 6.1
removes it, together with every other account created from now on.

First record when the checks start writing (epoch milliseconds, the unit of `created_at`):

```sh
date +%s%3N > /tmp/loop-live/check-start && cat /tmp/loop-live/check-start
```

Then:

```sh
BASE=http://127.0.0.1:8080 bash <<'EOF'
set -euo pipefail
WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
NAME="livecheck.$(date +%s)"
fail() { echo "FAIL: $*" >&2; exit 1; }
# A unique query string makes the CDN ask the bucket instead of answering from its cache.
range_status() { curl -sS -o /dev/null -w '%{http_code}' -r 0-1023 "$1?cacheNonce=$(date +%s%N)"; }

ffmpeg -nostdin -loglevel error -f lavfi -i testsrc2=s=540x960:r=30:d=3 -f lavfi -i sine=frequency=440:d=3 \
  -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest -movflags +faststart "$WORK/clip.mp4"
ffmpeg -nostdin -loglevel error -f lavfi -i color=c=0x6C5CE7:s=256x256 -frames:v 1 "$WORK/avatar1.jpg"
ffmpeg -nostdin -loglevel error -f lavfi -i color=c=0x00B894:s=256x256 -frames:v 1 "$WORK/avatar2.jpg"

TOKEN=$(curl -sSf -X POST "$BASE/api/auth/register" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$NAME\",\"displayName\":\"Live check\",\"password\":\"livecheck-pass\"}" | jq -r .token)
AUTH=(-H "Authorization: Bearer $TOKEN")
echo "registered $NAME"

VIDEO=$(curl -sSf "${AUTH[@]}" -F 'caption=Live check #livecheck' -F "video=@$WORK/clip.mp4;type=video/mp4" "$BASE/api/videos")
VIDEO_ID=$(jq -r .id <<<"$VIDEO"); VIDEO_URL=$(jq -r .videoUrl <<<"$VIDEO"); THUMB_URL=$(jq -r .thumbnailUrl <<<"$VIDEO")
echo "uploaded video $VIDEO_ID: $VIDEO_URL"
[[ $VIDEO_URL == https://*/storage/v1/object/public/* ]] || fail "videoUrl is not a Storage public URL"
[[ $(range_status "$VIDEO_URL") == 206 ]] || fail "video Range request did not return 206"
[[ $(range_status "$THUMB_URL") =~ ^20[06]$ ]] || fail "thumbnail is not readable"

AVATAR1=$(curl -sSf "${AUTH[@]}" -F "avatar=@$WORK/avatar1.jpg;type=image/jpeg" "$BASE/api/me/avatar" | jq -r .avatarUrl)
AVATAR2=$(curl -sSf "${AUTH[@]}" -F "avatar=@$WORK/avatar2.jpg;type=image/jpeg" "$BASE/api/me/avatar" | jq -r .avatarUrl)
[[ $(range_status "$AVATAR2") =~ ^20[06]$ ]] || fail "new avatar is not readable"
[[ $(range_status "$AVATAR1") =~ ^(400|404)$ ]] || fail "replaced avatar was not deleted from the bucket"
echo "avatar replaced: $AVATAR2"

curl -sSf -X PUT "${AUTH[@]}" "$BASE/api/videos/$VIDEO_ID/like" | jq -c .
curl -sSf "${AUTH[@]}" -H 'Content-Type: application/json' -d '{"text":"first!"}' "$BASE/api/videos/$VIDEO_ID/comments" | jq -c '{id, text}'
curl -sSf "$BASE/api/videos/$VIDEO_ID" | jq -c '{id, likeCount, commentCount, hashtags}'
curl -sSf "$BASE/api/search?q=livecheck" | jq -c '{users: [.users[].username], hashtags: [.hashtags[].name]}'
curl -sSf -o /dev/null -w 'share page %{http_code}\n' "$BASE/v/$VIDEO_ID"

curl -sSf -X DELETE "${AUTH[@]}" -o /dev/null -w 'delete video %{http_code}\n' "$BASE/api/videos/$VIDEO_ID"
[[ $(range_status "$VIDEO_URL") =~ ^(400|404)$ ]] || fail "deleted video is still in the bucket"
[[ $(range_status "$THUMB_URL") =~ ^(400|404)$ ]] || fail "deleted thumbnail is still in the bucket"
echo "video and thumbnail deleted from the bucket"
echo "PASS: end-to-end pass for $NAME"
EOF
```

Expected: ends with `PASS`; the video shows `likeCount 1`, `commentCount 1`, hashtag `livecheck`,
and the share page answers `200`. (Supabase answers a missing public object with `400` and a JSON
body whose `statusCode` is `404`, hence `400|404`. The checks add a unique `cacheNonce` so that
the bucket answers, not the CDN: on the free plan a delete does not purge the CDN, so the plain
URL of the video, fetched once before the delete, can keep answering `206` from its cache; see
Media in `docs/api.md`.) Afterwards the row counts of step 2 are the same plus one user and that
user's avatar object; the `livecheck` hashtag and the upload's sound are gone with the video.

## 5. JVM test suites

1. The app's JVM end-to-end suite against the running server, if the repository has it
   (`app/src/test/java/com/ndizigiye/loop/e2e/`: Robolectric and Compose UI tests that drive the
   app's features against a live server and check the server's state through its API; they only
   run when the Gradle property `loop.e2e.baseUrl` is set). It needs the Android SDK, as any build
   of `:app` does.

   ```sh
   cd /home/user/tiktok && ls app/src/test/java/com/ndizigiye/loop/e2e/ \
     && ./gradlew :app:testDebugUnitTest -Ploop.e2e.baseUrl=http://127.0.0.1:8080/
   ```

   Expected: no failures (`app/build/test-results/testDebugUnitTest/*.xml`; count the e2e classes
   separately from the plain unit tests, which run in the same task). Against Supabase every media
   URL the app loads is an absolute bucket URL, so this is the check that the app plays and uploads
   with Storage. The suite creates accounts, uploads and comments in the production project; step
   6.1 removes them. If the directory does not exist, say so in the report; the end-to-end pass of
   step 4 is then the only API-level check.

2. The whole server suite, as in development:

   ```sh
   ./gradlew -p server test
   ```

   It runs on SQLite with the in-process Storage double (`FakeSupabaseStorage`) and never touches
   Supabase, even with the `LOOP_*` variables set (tests build their own configuration). Expected:
   90 tests, 5 skipped (the PostgreSQL-only ones), no failures
   (`server/build/test-results/test/*.xml`).

3. The server suite on the Supabase Postgres server (`testPostgres`). Every test creates its own
   scratch database `loop_test_<uuid>` through the session pooler, applies
   `server/supabase/migrations/`, runs, and drops the database; the `postgres` database with the
   live `loop` schema is never touched. Run one class first; it shows whether the pooler accepts
   the scratch databases:

   ```sh
   LOOP_TEST_DATABASE_URL="$LOOP_DATABASE_URL" ./gradlew -p server testPostgres --tests 'com.ndizigiye.loop.server.AuthTest'
   LOOP_TEST_DATABASE_URL="$LOOP_DATABASE_URL" ./gradlew -p server testPostgres
   ```

   Expected: 90 tests, none skipped, no failures (`server/build/test-results/testPostgres/*.xml`);
   it takes several minutes because every test creates and drops a database over the network. If
   the first command fails to connect to the scratch database, report it and skip the full run.

   One test (`PostgresTest`, "connection errors never contain the password") logs in once with a
   wrong password on purpose. Supabase bans an IP after two failed logins in a row, so never
   start the suite again right after a run that failed on authentication; if connections are
   refused afterwards, unban the IP (Dashboard, Database settings, Banned IPs) or wait 30 minutes.

   Afterwards no scratch database may remain:

   ```sql
   select datname from pg_database where datname like 'loop_test_%';
   ```

   Drop leftovers of an interrupted run with `drop database "<name>" with (force);` (psql or the
   dashboard's SQL editor; `drop database` cannot run inside a transaction).

## 6. Clean up

Storage objects are removed through the Storage API (the `storage` function of `env.sh`) or the
dashboard, never with SQL on `storage.objects`: that would orphan the files.

### 6.1 What the checks created (always)

Every account created since `/tmp/loop-live/check-start` (step 4) is a test account: the
`livecheck.*` account of step 4 and those of the app's end-to-end suite. The demo accounts are
older (the seeder dates them weeks back). In the statements below, replace `<check-start>` with the
number in that file.

1. `execute_sql`, and review the list: if a real person signed up during the check, stop and ask
   before going on.

   ```sql
   select id, username, created_at from loop.users where created_at >= <check-start> order by id;
   ```

2. `execute_sql`, then write the resulting JSON array to `/tmp/loop-live/paths.json`:

   ```sql
   select coalesce(json_agg(path), '[]') from (
     select 'avatars/' || avatar_file as path from loop.users where created_at >= <check-start> and avatar_file is not null
     union all
     select 'videos/' || v.video_file from loop.videos v join loop.users u on u.id = v.author_id where u.created_at >= <check-start>
     union all
     select 'thumbs/' || v.thumb_file from loop.videos v join loop.users u on u.id = v.author_id
       where u.created_at >= <check-start> and v.thumb_file is not null
   ) p;
   ```

3. Delete those objects (prints how many were deleted; skip this when the array is `[]`):

   ```sh
   . /tmp/loop-live/env.sh
   storage -X DELETE "$SUPA/storage/v1/object/$BUCKET" -d "{\"prefixes\": $(cat /tmp/loop-live/paths.json)}" | jq length
   ```

4. `execute_sql`. Deleting the accounts cascades to their videos, likes, bookmarks, comments,
   follows, watch history and notifications; the last two statements do what deleting a video
   through the API does (drop sounds and hashtags no video uses any more):

   ```sql
   delete from loop.users where created_at >= <check-start>;
   delete from loop.sounds s where not exists (select from loop.videos v where v.sound_id = s.id);
   delete from loop.hashtags h where not exists (select from loop.video_hashtags vh where vh.hashtag_id = h.id);
   ```

The counts of steps 2 and 3 are back to the seeded values. (View and share counters of demo videos
keep what the checks added; they are not in those tables.)

### 6.2 Remove the demo accounts, keep everything else

For when real accounts exist next to the demo data. Demo media has fixed names
(`videos/demo-NN.mp4`, `thumbs/demo-NN.jpg`, `avatars/demo-<user>.jpg`).

1. `execute_sql`, result to `/tmp/loop-live/paths.json`:

   ```sql
   select coalesce(json_agg(name order by name), '[]') from storage.objects
   where bucket_id = 'loop-media' and name ~ '^(videos|thumbs|avatars)/demo-';
   ```

2. Delete them, as in 6.1 step 3 (54 for a full demo set).
3. `execute_sql`:

   ```sql
   delete from loop.users where username in ('mira.makes', 'tobi_trails', 'nightowl.beats', 'pixel.pantry',
     'lena.lifts', 'quietcoder', 'dot.and.line', 'fieldnotes.ari');
   delete from loop.sounds s where not exists (select from loop.videos v where v.sound_id = s.id);
   delete from loop.hashtags h where not exists (select from loop.video_hashtags vh where vh.hashtag_id = h.id);
   ```

   Videos of other accounts that use a demo sound keep it (its author becomes empty). `loop.meta`
   keeps `demo_seed = done`, so the server does not seed again.

### 6.3 Empty everything (back to a fresh project)

Only when the user wants all Loop data gone. Stop the server first (step 7).

1. `execute_sql`:

   ```sql
   truncate loop.notifications, loop.comment_likes, loop.comments, loop.view_history, loop.not_interested,
     loop.bookmarks, loop.video_likes, loop.follows, loop.video_hashtags, loop.hashtags, loop.videos,
     loop.sounds, loop.users, loop.meta restart identity;
   ```

   This keeps the schema and the migration history. It also removes the token key, so every
   token issued so far stops working.

2. Empty the bucket, keeping the bucket itself:

   ```sh
   . /tmp/loop-live/env.sh
   storage -X POST "$SUPA/storage/v1/bucket/$BUCKET/empty" -d '{}'
   ```

   or in the dashboard: Storage, `loop-media`, bucket menu, Empty bucket.
3. Check: the counts of step 2 are all 0 and
   `select count(*) from storage.objects where bucket_id = 'loop-media'` is 0.

`LOOP_SEED` defaults to `true`: the next server start on an empty database seeds again. Start it
with `LOOP_SEED=false` to keep production empty.

## 7. Stop and report

```sh
kill "$(cat /tmp/loop-live/server.pid)"; sleep 3
kill -0 "$(cat /tmp/loop-live/server.pid)" 2>/dev/null && echo "still running" || echo "server stopped"
```

When the server has stopped and no cleanup step still needs the files, `rm -rf /tmp/loop-live`.

Report: the preflight results; the startup lines (database host and user are not secret); the
counts of steps 2 and 3 against the expected tables; the Range results; the end-to-end result; the
test runs of step 5 (tests, skipped, failures, or why one did not run); the advisors; which cleanup
ran and its checks. Never include the connection string, the password or the key.
