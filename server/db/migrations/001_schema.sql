-- PageDrop Server Database Schema
-- Run this in your Supabase project's SQL editor.

-- 0. Extensions
create extension if not exists "pgcrypto";

-- 1. Profiles (extends Supabase Auth users)
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  tier text not null default 'free' check (tier in ('free', 'pro')),
  daily_job_limit int not null default 20,
  storage_quota_bytes bigint not null default 524288000,  -- 500 MB
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.profiles enable row level security;

create policy "Users can read own profile"
  on public.profiles for select
  using (auth.uid() = id);

create policy "Users can update own profile"
  on public.profiles for update
  using (auth.uid() = id);

-- Automatically create profile on signup
create or replace function public.handle_new_user()
returns trigger as $$
begin
  insert into public.profiles (id)
  values (new.id);
  return new;
end;
$$ language plpgsql security definer;

create or replace trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- 2. Jobs
create table if not exists public.jobs (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'pending'
    check (status in ('pending', 'processing', 'complete', 'failed')),
  job_type text not null check (job_type in ('convert', 'article')),
  source_storage_path text,                                    -- path in Supabase Storage
  target_format text not null default 'mobi',
  target_profile text not null default 'kindle-stock',
  result_storage_path text,                                    -- path to converted artifact
  cover_storage_path text,                                     -- path to extracted cover
  title text,
  author text,
  source_fingerprint text,                                     -- sha256 of source file
  warnings jsonb not null default '[]',
  logs jsonb not null default '[]',
  error_message text,
  file_size_bytes int,
  created_at timestamptz not null default now(),
  started_at timestamptz,
  completed_at timestamptz
);

create index idx_jobs_status on public.jobs(status);
create index idx_jobs_user_id on public.jobs(user_id);
create index idx_jobs_created_at on public.jobs(created_at);

alter table public.jobs enable row level security;

create policy "Users can insert own jobs"
  on public.jobs for insert
  with check (auth.uid() = user_id);

create policy "Users can read own jobs"
  on public.jobs for select
  using (auth.uid() = user_id);

create policy "Users can update own jobs"
  on public.jobs for update
  using (auth.uid() = user_id);

-- Worker (service_role) bypasses RLS — no policy needed for it

-- 3. Rate Limits (daily counters)
create table if not exists public.rate_limits (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  date date not null default current_date,
  job_count int not null default 1,
  unique (user_id, date)
);

create index idx_rate_limits_user_date on public.rate_limits(user_id, date);

alter table public.rate_limits enable row level security;

create policy "Users can read own rate limits"
  on public.rate_limits for select
  using (auth.uid() = user_id);

-- 4. Storage Quotas (aggregated)
create table if not exists public.storage_usage (
  user_id uuid primary key references auth.users(id) on delete cascade,
  bytes_used bigint not null default 0,
  updated_at timestamptz not null default now()
);

alter table public.storage_usage enable row level security;

create policy "Users can read own storage usage"
  on public.storage_usage for select
  using (auth.uid() = user_id);

-- 5. Storage bucket for artifacts
-- Run this in Supabase Dashboard → Storage:
-- Create bucket "artifacts" with public read access (files are user-isolated by path)
-- Create bucket "sources" with restricted access (only worker can read)

-- 6. Helper: increment rate limit counter
create or replace function public.increment_rate_limit(p_user_id uuid)
returns boolean as $$
declare
  current_count int;
  user_limit int;
begin
  -- Get user's daily limit from profile
  select daily_job_limit into user_limit
  from public.profiles where id = p_user_id;

  -- Insert or update today's counter
  insert into public.rate_limits (user_id, date, job_count)
  values (p_user_id, current_date, 1)
  on conflict (user_id, date)
  do update set job_count = public.rate_limits.job_count + 1
  returning job_count into current_count;

  return current_count <= user_limit;
end;
$$ language plpgsql security definer;
