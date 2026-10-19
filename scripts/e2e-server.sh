#!/usr/bin/env bash
# Starts and stops a seeded Loop server for the app's end-to-end tests
# (app/src/test/java/com/ndizigiye/loop/e2e/).
#
#   scripts/e2e-server.sh start [--postgres | --sqlite] [--port N]
#   scripts/e2e-server.sh url
#   scripts/e2e-server.sh stop
#
# start builds the server (installDist), starts it with LOOP_SEED=true and a fresh data
# directory on a free port (from 18080), waits until /health answers and demo seeding has
# finished, and prints the base URL, e.g. http://127.0.0.1:18080/. Then run
#
#   ./gradlew :app:testDebugUnitTest -Ploop.e2e.baseUrl=http://127.0.0.1:18080/
#
# --postgres (the default where a local PostgreSQL cluster is installed, i.e. pg_ctlcluster
# exists) starts that cluster if it is down, creates a throwaway role and database, applies
# server/supabase/migrations/*.sql in name order and points LOOP_DATABASE_URL at it.
# --sqlite keeps the rows in the data directory instead. Media always stay on local disk.
#
# stop kills the server, deletes the data directory, drops the database and role, and stops
# the PostgreSQL cluster again if start started it. A start that fails cleans up the same way.
#
# State lives in $LOOP_E2E_STATE_DIR (default /tmp/claude-0/loop-e2e).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE_DIR="${LOOP_E2E_STATE_DIR:-/tmp/claude-0/loop-e2e}"
STATE_FILE="$STATE_DIR/state.env"
SERVER_BIN="$ROOT/server/build/install/loop-server/bin/loop-server"
PG_VERSION="${LOOP_E2E_PG_VERSION:-16}"
PG_CLUSTER="${LOOP_E2E_PG_CLUSTER:-main}"
PG_PORT="${LOOP_E2E_PG_PORT:-5432}"
DEFAULT_PORT=18080
SEED_TIMEOUT_S="${LOOP_E2E_SEED_TIMEOUT_S:-900}"

log() { echo "e2e-server: $*" >&2; }
die() { log "$*"; exit 1; }

usage() {
  # The comment block at the top of this file.
  awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "${BASH_SOURCE[0]}" >&2
  exit 2
}

# Runs a command as the postgres OS user (peer authentication for admin statements).
as_postgres() {
  if [ "$(id -u)" -eq 0 ]; then
    runuser -u postgres -- "$@"
  else
    sudo -n -u postgres -- "$@"
  fi
}

admin_sql() {
  as_postgres psql -p "$PG_PORT" -X -q -v ON_ERROR_STOP=1 -d postgres -c "$1"
}

cluster_online() {
  pg_lsclusters --no-header 2>/dev/null |
    awk -v v="$PG_VERSION" -v c="$PG_CLUSTER" '$1 == v && $2 == c { print $4 }' | grep -q '^online'
}

port_free() {
  ! (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null
}

free_port() {
  local port=$1
  while ! port_free "$port"; do
    port=$((port + 1))
    [ "$port" -lt $(($1 + 200)) ] || die "no free port found from $1"
  done
  echo "$port"
}

random_token() {
  head -c 16 /dev/urandom | od -An -tx1 | tr -d ' \n'
}

write_state() {
  {
    echo "SERVER_PID=${SERVER_PID:-0}"
    echo "PORT=$PORT"
    echo "BASE_URL=http://127.0.0.1:$PORT/"
    echo "DATA_DIR=$DATA_DIR"
    echo "BACKEND=$BACKEND"
    echo "DB_NAME=${DB_NAME:-}"
    echo "DB_ROLE=${DB_ROLE:-}"
    echo "STARTED_CLUSTER=${STARTED_CLUSTER:-false}"
  } >"$STATE_FILE"
}

start_postgres() {
  command -v pg_ctlcluster >/dev/null || die "--postgres needs a local PostgreSQL cluster (pg_ctlcluster)"
  if ! cluster_online; then
    log "starting PostgreSQL $PG_VERSION/$PG_CLUSTER"
    STARTED_CLUSTER=true
    write_state
    pg_ctlcluster "$PG_VERSION" "$PG_CLUSTER" start
  fi
  local suffix password migration
  suffix="$(random_token | head -c 12)"
  password="$(random_token)"
  DB_NAME="loop_e2e_$suffix"
  DB_ROLE="loop_e2e_$suffix"
  write_state
  admin_sql "CREATE ROLE $DB_ROLE LOGIN PASSWORD '$password'"
  admin_sql "CREATE DATABASE $DB_NAME OWNER $DB_ROLE"
  # The role owns the database and creates the schema, so it may use everything in it.
  for migration in "$ROOT"/server/supabase/migrations/*.sql; do
    log "applying $(basename "$migration")"
    PGPASSWORD="$password" psql -h 127.0.0.1 -p "$PG_PORT" -U "$DB_ROLE" -d "$DB_NAME" \
      -X -q -v ON_ERROR_STOP=1 -f "$migration" >/dev/null
  done
  DATABASE_URL="postgresql://$DB_ROLE:$password@127.0.0.1:$PG_PORT/$DB_NAME"
}

server_exited() {
  tail -n 40 "$STATE_DIR/server.log" >&2
  die "the server exited"
}

wait_until_seeded() {
  local deadline=$((SECONDS + SEED_TIMEOUT_S)) items
  until curl -fsS "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; do
    kill -0 "$SERVER_PID" 2>/dev/null || server_exited
    [ "$SECONDS" -lt "$deadline" ] || die "the server did not answer /health in time"
    sleep 1
  done
  log "server is up, waiting for demo seeding to finish"
  until grep -q "Demo seeding finished" "$STATE_DIR/server.log"; do
    kill -0 "$SERVER_PID" 2>/dev/null || server_exited
    ! grep -q "Demo seeding failed" "$STATE_DIR/server.log" || die "demo seeding failed, see $STATE_DIR/server.log"
    [ "$SECONDS" -lt "$deadline" ] || die "demo seeding did not finish in ${SEED_TIMEOUT_S}s"
    sleep 2
  done
  items=$(curl -fsS "http://127.0.0.1:$PORT/api/feed/for-you?limit=50" | grep -o '"id":' | wc -l)
  [ "$items" -gt 0 ] || die "seeding finished without videos (is ffmpeg installed?)"
  log "$(grep -o 'Demo seeding finished.*' "$STATE_DIR/server.log")"
}

cmd_start() {
  BACKEND=""
  PORT=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --postgres) BACKEND=postgres ;;
      --sqlite) BACKEND=sqlite ;;
      --port) PORT="${2:?--port needs a value}"; shift ;;
      *) usage ;;
    esac
    shift
  done
  if [ -z "$BACKEND" ]; then
    if command -v pg_ctlcluster >/dev/null; then BACKEND=postgres; else BACKEND=sqlite; fi
  fi
  [ ! -f "$STATE_FILE" ] || die "already started ($STATE_FILE exists); run '$0 stop' first"
  PORT="$(free_port "${PORT:-$DEFAULT_PORT}")"

  log "building the server"
  (cd "$ROOT" && ./gradlew -q -p server installDist --console=plain) >&2

  mkdir -p "$STATE_DIR"
  DATA_DIR="$(mktemp -d "$STATE_DIR/data-XXXXXX")"
  DATABASE_URL=""
  SERVER_PID=0
  STARTED_CLUSTER=false
  write_state
  # From here on, a start that fails (an error or die) undoes everything it set up.
  trap 'log "start failed, cleaning up"; cmd_stop' EXIT
  if [ "$BACKEND" = postgres ]; then start_postgres; fi

  log "starting the server on port $PORT ($BACKEND, data in $DATA_DIR)"
  (
    export PORT LOOP_DATA_DIR="$DATA_DIR" LOOP_SEED=true
    unset LOOP_SUPABASE_SERVICE_KEY LOOP_SUPABASE_URL LOOP_SUPABASE_BUCKET
    if [ -n "$DATABASE_URL" ]; then export LOOP_DATABASE_URL="$DATABASE_URL"; else unset LOOP_DATABASE_URL; fi
    exec setsid "$SERVER_BIN" >"$STATE_DIR/server.log" 2>&1 </dev/null
  ) &
  SERVER_PID=$!
  echo "$SERVER_PID" >"$STATE_DIR/server.pid"
  write_state
  wait_until_seeded
  trap - EXIT
  echo "http://127.0.0.1:$PORT/"
}

cmd_url() {
  [ -f "$STATE_FILE" ] || die "not started"
  # shellcheck disable=SC1090
  . "$STATE_FILE"
  echo "$BASE_URL"
}

cmd_stop() {
  if [ ! -f "$STATE_FILE" ]; then
    log "not started"
    return 0
  fi
  # shellcheck disable=SC1090
  . "$STATE_FILE"
  if [ "${SERVER_PID:-0}" -gt 0 ] && kill -0 "$SERVER_PID" 2>/dev/null; then
    log "stopping the server (pid $SERVER_PID)"
    # setsid made the server a process group leader: signal the whole group.
    kill -TERM -- "-$SERVER_PID" 2>/dev/null || kill -TERM "$SERVER_PID" 2>/dev/null || true
    local waited=0
    while kill -0 "$SERVER_PID" 2>/dev/null && [ "$waited" -lt 30 ]; do
      sleep 1
      waited=$((waited + 1))
    done
    kill -KILL -- "-$SERVER_PID" 2>/dev/null || kill -KILL "$SERVER_PID" 2>/dev/null || true
  fi
  if [ -n "${DB_NAME:-}" ]; then
    log "dropping database $DB_NAME"
    admin_sql "DROP DATABASE IF EXISTS $DB_NAME WITH (FORCE)" || true
    admin_sql "DROP ROLE IF EXISTS $DB_ROLE" || true
  fi
  if [ "${STARTED_CLUSTER:-false}" = true ]; then
    log "stopping PostgreSQL $PG_VERSION/$PG_CLUSTER"
    pg_ctlcluster "$PG_VERSION" "$PG_CLUSTER" stop || true
  fi
  case "${DATA_DIR:-}" in
    "$STATE_DIR"/data-*) rm -rf -- "$DATA_DIR" ;;
  esac
  rm -f "$STATE_DIR/server.pid" "$STATE_DIR/server.log" "$STATE_FILE"
  rmdir "$STATE_DIR" 2>/dev/null || true
  log "stopped"
}

case "${1:-}" in
  start) shift; cmd_start "$@" ;;
  stop) cmd_stop ;;
  url) cmd_url ;;
  *) usage ;;
esac
