-- Loop server schema for Supabase Postgres.
--
-- Every table of the Ktor server lives in the private schema "loop", never in "public". The
-- server connects as "postgres" through the session pooler (LOOP_DATABASE_URL) and does all
-- reads and writes itself; the Android app never talks to Supabase. "loop" is not one of the
-- Data API's exposed schemas, row level security is on for every table without any policy,
-- and anon/authenticated have no privileges, so the publishable key reaches nothing here.
--
-- The section between the "generated" markers is the output of
--   LOOP_DATABASE_URL=<any PostgreSQL> ./gradlew -p server printPostgresSchema
-- i.e. Exposed's PostgreSQL DDL for server/src/main/kotlin/com/ndizigiye/loop/server/db/Tables.kt
-- plus row level security for each table. The server checks at startup that the database
-- matches Tables.kt and refuses to start otherwise; it never creates tables in PostgreSQL.
-- When Tables.kt changes, add a new migration with the difference.

create schema if not exists loop;

-- The generated statements use unqualified names; they resolve to "loop" until the reset below.
set search_path to loop;

-- generated:begin
CREATE TABLE IF NOT EXISTS meta ("key" VARCHAR(64) PRIMARY KEY, "value" TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS users (id BIGSERIAL PRIMARY KEY, username VARCHAR(24) NOT NULL, display_name VARCHAR(40) NOT NULL, bio VARCHAR(160) DEFAULT '' NOT NULL, password_hash VARCHAR(128) NOT NULL, password_salt VARCHAR(64) NOT NULL, avatar_file VARCHAR(128) NULL, avatar_color VARCHAR(7) NOT NULL, verified BOOLEAN DEFAULT FALSE NOT NULL, created_at BIGINT NOT NULL);
ALTER TABLE users ADD CONSTRAINT users_username_unique UNIQUE (username);
CREATE TABLE IF NOT EXISTS sounds (id BIGSERIAL PRIMARY KEY, title VARCHAR(200) NOT NULL, author_name VARCHAR(100) NOT NULL, author_id BIGINT NULL, created_at BIGINT NOT NULL, CONSTRAINT fk_sounds_author_id__id FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE SET NULL ON UPDATE RESTRICT);
CREATE INDEX sounds_author_id ON sounds (author_id);
CREATE TABLE IF NOT EXISTS videos (id BIGSERIAL PRIMARY KEY, author_id BIGINT NOT NULL, caption VARCHAR(300) NOT NULL, video_file VARCHAR(128) NOT NULL, thumb_file VARCHAR(128) NULL, width INT NOT NULL, height INT NOT NULL, duration_ms BIGINT NOT NULL, sound_id BIGINT NOT NULL, share_count BIGINT DEFAULT 0 NOT NULL, view_count BIGINT DEFAULT 0 NOT NULL, completion_count BIGINT DEFAULT 0 NOT NULL, created_at BIGINT NOT NULL, CONSTRAINT fk_videos_author_id__id FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT, CONSTRAINT fk_videos_sound_id__id FOREIGN KEY (sound_id) REFERENCES sounds(id) ON DELETE RESTRICT ON UPDATE RESTRICT);
CREATE INDEX videos_sound_id ON videos (sound_id);
CREATE INDEX videos_author_id_created_at ON videos (author_id, created_at);
CREATE TABLE IF NOT EXISTS hashtags (id BIGSERIAL PRIMARY KEY, "name" VARCHAR(100) NOT NULL);
ALTER TABLE hashtags ADD CONSTRAINT hashtags_name_unique UNIQUE ("name");
CREATE TABLE IF NOT EXISTS video_hashtags (video_id BIGINT, hashtag_id BIGINT, "position" INT NOT NULL, CONSTRAINT pk_video_hashtags PRIMARY KEY (video_id, hashtag_id), CONSTRAINT fk_video_hashtags_video_id__id FOREIGN KEY (video_id) REFERENCES videos(id) ON DELETE CASCADE ON UPDATE RESTRICT, CONSTRAINT fk_video_hashtags_hashtag_id__id FOREIGN KEY (hashtag_id) REFERENCES hashtags(id) ON DELETE RESTRICT ON UPDATE RESTRICT);
CREATE INDEX video_hashtags_hashtag_id ON video_hashtags (hashtag_id);
CREATE TABLE IF NOT EXISTS follows (follower_id BIGINT, followee_id BIGINT, created_at BIGINT NOT NULL, CONSTRAINT pk_follows PRIMARY KEY (follower_id, followee_id), CONSTRAINT fk_follows_follower_id__id FOREIGN KEY (follower_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT, CONSTRAINT fk_follows_followee_id__id FOREIGN KEY (followee_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT);
CREATE INDEX follows_followee_id ON follows (followee_id);
CREATE TABLE IF NOT EXISTS video_likes (user_id BIGINT, video_id BIGINT, created_at BIGINT NOT NULL, CONSTRAINT pk_video_likes PRIMARY KEY (user_id, video_id), CONSTRAINT fk_video_likes_user_id__id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT, CONSTRAINT fk_video_likes_video_id__id FOREIGN KEY (video_id) REFERENCES videos(id) ON DELETE CASCADE ON UPDATE RESTRICT);
CREATE INDEX video_likes_video_id ON video_likes (video_id);
CREATE TABLE IF NOT EXISTS bookmarks (user_id BIGINT, video_id BIGINT, created_at BIGINT NOT NULL, CONSTRAINT pk_bookmarks PRIMARY KEY (user_id, video_id), CONSTRAINT fk_bookmarks_user_id__id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT, CONSTRAINT fk_bookmarks_video_id__id FOREIGN KEY (video_id) REFERENCES videos(id) ON DELETE CASCADE ON UPDATE RESTRICT);
CREATE INDEX bookmarks_video_id ON bookmarks (video_id);
CREATE TABLE IF NOT EXISTS "comments" (id BIGSERIAL PRIMARY KEY, video_id BIGINT NOT NULL, author_id BIGINT NOT NULL, parent_id BIGINT NULL, "text" VARCHAR(300) NOT NULL, created_at BIGINT NOT NULL);
CREATE INDEX comments_parent_id ON "comments" (parent_id);
CREATE INDEX comments_video_id_parent_id ON "comments" (video_id, parent_id);
CREATE TABLE IF NOT EXISTS comment_likes (user_id BIGINT, comment_id BIGINT, created_at BIGINT NOT NULL, CONSTRAINT pk_comment_likes PRIMARY KEY (user_id, comment_id));
CREATE INDEX comment_likes_comment_id ON comment_likes (comment_id);
CREATE TABLE IF NOT EXISTS view_history (user_id BIGINT, video_id BIGINT, watched_ms BIGINT NOT NULL, completed BOOLEAN NOT NULL, viewed_at BIGINT NOT NULL, CONSTRAINT pk_view_history PRIMARY KEY (user_id, video_id), CONSTRAINT fk_view_history_user_id__id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT, CONSTRAINT fk_view_history_video_id__id FOREIGN KEY (video_id) REFERENCES videos(id) ON DELETE CASCADE ON UPDATE RESTRICT);
CREATE INDEX view_history_video_id ON view_history (video_id);
CREATE TABLE IF NOT EXISTS not_interested (user_id BIGINT, video_id BIGINT, created_at BIGINT NOT NULL, CONSTRAINT pk_not_interested PRIMARY KEY (user_id, video_id), CONSTRAINT fk_not_interested_user_id__id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT, CONSTRAINT fk_not_interested_video_id__id FOREIGN KEY (video_id) REFERENCES videos(id) ON DELETE CASCADE ON UPDATE RESTRICT);
CREATE INDEX not_interested_video_id ON not_interested (video_id);
CREATE TABLE IF NOT EXISTS notifications (id BIGSERIAL PRIMARY KEY, recipient_id BIGINT NOT NULL, actor_id BIGINT NOT NULL, "type" VARCHAR(16) NOT NULL, video_id BIGINT NULL, comment_id BIGINT NULL, "text" VARCHAR(400) NULL, "read" BOOLEAN DEFAULT FALSE NOT NULL, created_at BIGINT NOT NULL);
CREATE INDEX notifications_actor_id ON notifications (actor_id);
CREATE INDEX notifications_video_id ON notifications (video_id);
CREATE INDEX notifications_comment_id ON notifications (comment_id);
CREATE INDEX notifications_recipient_id_created_at ON notifications (recipient_id, created_at);
ALTER TABLE "comments" ADD CONSTRAINT fk_comments_video_id__id FOREIGN KEY (video_id) REFERENCES videos(id) ON DELETE CASCADE ON UPDATE RESTRICT;
ALTER TABLE "comments" ADD CONSTRAINT fk_comments_author_id__id FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT;
ALTER TABLE "comments" ADD CONSTRAINT fk_comments_parent_id__id FOREIGN KEY (parent_id) REFERENCES "comments"(id) ON DELETE CASCADE ON UPDATE RESTRICT;
ALTER TABLE comment_likes ADD CONSTRAINT fk_comment_likes_user_id__id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT;
ALTER TABLE comment_likes ADD CONSTRAINT fk_comment_likes_comment_id__id FOREIGN KEY (comment_id) REFERENCES "comments"(id) ON DELETE CASCADE ON UPDATE RESTRICT;
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_recipient_id__id FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT;
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_actor_id__id FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT;
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_video_id__id FOREIGN KEY (video_id) REFERENCES videos(id) ON DELETE CASCADE ON UPDATE RESTRICT;
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_comment_id__id FOREIGN KEY (comment_id) REFERENCES "comments"(id) ON DELETE CASCADE ON UPDATE RESTRICT;
ALTER TABLE meta ENABLE ROW LEVEL SECURITY;
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE sounds ENABLE ROW LEVEL SECURITY;
ALTER TABLE videos ENABLE ROW LEVEL SECURITY;
ALTER TABLE hashtags ENABLE ROW LEVEL SECURITY;
ALTER TABLE video_hashtags ENABLE ROW LEVEL SECURITY;
ALTER TABLE follows ENABLE ROW LEVEL SECURITY;
ALTER TABLE video_likes ENABLE ROW LEVEL SECURITY;
ALTER TABLE bookmarks ENABLE ROW LEVEL SECURITY;
ALTER TABLE "comments" ENABLE ROW LEVEL SECURITY;
ALTER TABLE comment_likes ENABLE ROW LEVEL SECURITY;
ALTER TABLE view_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE not_interested ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
-- generated:end

reset search_path;

-- Privileges: nothing for PUBLIC or the Data API roles (anon, authenticated); everything for
-- the server's role (postgres) and service_role, also on tables and sequences that later
-- migrations create. Roles that do not exist are skipped, so the file also applies to a plain
-- PostgreSQL server (the server's PostgreSQL test suite runs it).
revoke all on schema loop from public;
revoke all on all tables in schema loop from public;
revoke all on all sequences in schema loop from public;

do $$
declare
  role_name text;
begin
  foreach role_name in array array['anon', 'authenticated'] loop
    if exists (select from pg_catalog.pg_roles where rolname = role_name) then
      execute format('revoke all on schema loop from %I', role_name);
      execute format('revoke all on all tables in schema loop from %I', role_name);
      execute format('revoke all on all sequences in schema loop from %I', role_name);
      execute format('alter default privileges in schema loop revoke all on tables from %I', role_name);
      execute format('alter default privileges in schema loop revoke all on sequences from %I', role_name);
    end if;
  end loop;
  foreach role_name in array array['postgres', 'service_role'] loop
    if exists (select from pg_catalog.pg_roles where rolname = role_name) then
      execute format('grant usage on schema loop to %I', role_name);
      execute format('grant all on all tables in schema loop to %I', role_name);
      execute format('grant all on all sequences in schema loop to %I', role_name);
      execute format('alter default privileges in schema loop grant all on tables to %I', role_name);
      execute format('alter default privileges in schema loop grant all on sequences to %I', role_name);
    end if;
  end loop;
end
$$;
