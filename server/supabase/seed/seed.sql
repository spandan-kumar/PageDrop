-- PageDrop Seed Data for testing
-- Run after the schema migration.

-- Create a test user profile (replace with an actual user ID from auth.users)
-- insert into public.profiles (id, tier) values ('<ACTUAL_USER_ID>', 'pro');

-- Create a test job (for manual worker testing)
-- insert into public.jobs (user_id, job_type, target_format, source_storage_path)
-- values (
--   '<ACTUAL_USER_ID>',
--   'convert',
--   'mobi',
--   '<USER_ID>/test/source.epub'
-- );

-- Grant worker permissions
-- Go to Supabase Dashboard → SQL Editor → run:
--   grant usage on schema public to service_role;
--   grant all on all tables in schema public to service_role;
