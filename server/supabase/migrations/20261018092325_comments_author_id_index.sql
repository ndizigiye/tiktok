-- Index for the foreign key comments.author_id, the only foreign key of the "loop" schema without
-- a covering index (Supabase performance advisor "unindexed_foreign_keys", lint 0001). Deleting a
-- user cascades to their comments, which otherwise scans the whole comments table.
--
-- The statement is the difference in the output of
--   LOOP_DATABASE_URL=<any PostgreSQL> ./gradlew -p server printPostgresSchema
-- after Comments.authorId got .index() in Tables.kt, qualified with the schema. The server's
-- startup check expects it, so apply this migration before starting a server that has it.

CREATE INDEX comments_author_id ON loop."comments" (author_id);
