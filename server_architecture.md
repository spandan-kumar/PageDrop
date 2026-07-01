# PageDrop Server Architecture

## Overview

The PageDrop backend is an optional conversion worker. The app works fully in
Direct Mode without it. When configured, the backend handles heavy ebook
conversion (Calibre CLI), article extraction, and URL-to-ebook pipelines.

This doc covers how to build it for **multi-user, public deployment** —
auth, rate limits, storage quotas, and a sustainable free-tier model.

---

## 1. Auth Strategy

### Options

#### A. Supabase Auth (Recommended)
Supabase handles auth out of the box: email/password, Google, GitHub, magic
links. Row Level Security (RLS) on Postgres means users can only see their own
jobs and artifacts. The free tier includes 50,000 monthly active users and
50MB DB — plenty for launch.

**Pros:**
- No auth code to write or maintain
- RLS enforces isolation at the database level — can't accidentally leak data
- Built-in rate limiting via Postgres triggers and Supabase Edge Functions
- Users can sign up themselves — zero ops for you
- Self-hostable if you want to move off Supabase later (it's just Postgres)

**Cons:**
- Dependency on Supabase (though self-hostable)
- Free tier has 50K MAU limit — fine for launch, need paid plan if you grow

#### B. Firebase Auth
Firebase Auth is mature and well-integrated with Google Cloud. Same features as
Supabase Auth but locks you into Google Cloud if you ever need to scale beyond
the free tier. Spark plan is free but Firebase has historically been expensive
at scale.

**Pros:**
- Battle-tested, excellent SDKs
- Free tier (10K MAU)

**Cons:**
- Proprietary — no self-hosting option
- Gets expensive fast beyond the free tier
- No SQL database — Firestore is document-based, worse for relational data

#### C. Self-Managed JWT (FastAPI + SQLite/SQLAlchemy)
Roll your own auth with FastAPI Users or similar. Password hashing with
bcrypt/argon2, JWT tokens, everything runs on your Oracle micro.

**Pros:**
- No external dependencies — everything on your box
- Full control over user model, rate limits, quotas

**Cons:**
- You write and maintain auth code (password reset, email verification, etc.)
- Account recovery is on you
- Must handle security yourself (token rotation, breach detection)

**Verdict: Supabase Auth wins.** It's free, zero-maintenance, self-hostable,
and RLS makes data isolation trivial. If the project grows and you want off
Supabase, you can self-host their stack or migrate to raw Postgres auth.

---

## 2. Backend Architecture

### Two-Component Architecture

```
┌──────────────┐       HTTPS        ┌──────────────────────────┐
│    Android    │ ────────────────── │   Supabase (API layer)   │
│    App        │                   │   Auth · DB · Storage    │
└──────────────┘                    └───────────┬──────────────┘
                                                │
                                       polls for pending jobs
                                                │
                                        ┌───────▼──────┐
                                        │  PageDrop    │
                                        │  Worker      │
                                        │  (Oracle)    │
                                        │  Calibre CLI │
                                        └──────────────┘
```

**Supabase handles:**
- Auth (signup, login, tokens)
- REST API (job creation, polling, artifact serving)
- Database (users, jobs, artifacts, rate limit counters)
- File Storage (uploaded source files, conversion artifacts)
- Row Level Security (user isolation)
- Edge Functions (lightweight validation, rate limiting)

**PageDrop Worker (Oracle free tier) handles:**
- Polling Supabase for pending jobs assigned to it
- Running Calibre CLI (`ebook-convert`)
- Running article extraction (Trafilatura/Mozilla Readability)
- Uploading results back to Supabase Storage
- Cleaning up temp files

### Why Two Components?

The Oracle micro (1GB RAM, 1/8 OCPU) can't handle everything. Offloading
auth, API, DB, and storage to Supabase means the worker only needs to run
when there's actual conversion work. The worker can even be a simple
CronJob that polls every N seconds — no need to keep a server running 24/7,
which keeps the Oracle instance well within free-tier limits.

### Alternative: Monolithic (All on Oracle)

One FastAPI server on Oracle handles auth, API, queue, Calibre, everything.
Uses SQLite for DB.

**Pros:** Simpler, no external dependencies, everything on one box.
**Cons:** 1/8 OCPU is tight for serving API requests + running Calibre
conversions simultaneously. If a conversion spikes CPU, API latency suffers
for all users. SQLite doesn't handle concurrent writes well — multi-user
job creation while a conversion is running can lock.

**Verdict: Two-component is better for multi-user.** Supabase absorbs the
API traffic; Oracle only burns CPU during actual conversions.

---

## 3. Rate Limiting & Quotas

### Per-User Limits (enforced by Supabase RLS + Edge Functions)

| Limit | Value | Rationale |
|-------|-------|-----------|
| Concurrent jobs | 1 per user | Prevents one user from flooding the queue |
| Max file size | 100 MB | Reasonable for ebooks; larger files timeout |
| Daily job count | 20 per user (free) | Controls server cost; can raise for paid tiers |
| Artifact TTL | 24 hours | Disk/cost management |
| Storage quota | 500 MB per user | Free tier; paid tiers get more |

### Implementation Approach

**Supabase Edge Function** on job creation:
1. Check user's daily job count from `rate_limits` table
2. Check user's current storage usage from `storage_quotas` table
3. Validate file size
4. If all pass, create job record and return job ID

**Worker (Oracle)** pulls next eligible job:
1. Query `jobs` table for `status = 'pending'` ordered by `created_at`
2. Process conversion
3. Upload result to Supabase Storage
4. Update job status to `complete`

### Database Schema (Supabase Postgres)

```sql
-- Users are managed by Supabase Auth (auth.users).
-- We extend with a profiles table.

create table public.profiles (
  id uuid primary key references auth.users(id),
  tier text not null default 'free',
  created_at timestamptz not null default now()
);

create table public.jobs (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id),
  status text not null default 'pending',
  job_type text not null, -- 'convert', 'article'
  source_file_path text,
  target_format text not null default 'mobi',
  target_profile text not null default 'kindle-stock',
  artifact_path text,
  cover_path text,
  title text,
  author text,
  warnings jsonb default '[]',
  logs jsonb default '[]',
  created_at timestamptz not null default now(),
  completed_at timestamptz
);

-- RLS: users can only see their own jobs
alter table public.jobs enable row level security;
create policy "Users can CRUD their own jobs"
  on public.jobs for all
  using (auth.uid() = user_id);

create table public.rate_limits (
  user_id uuid primary key references auth.users(id),
  date date not null default current_date,
  job_count int not null default 0
);

create table public.storage_quotas (
  user_id uuid primary key references auth.users(id),
  bytes_used bigint not null default 0
);
```

---

## 4. Deployment Options

### Option A: Supabase + Oracle Worker (Recommended)

| Component | Platform | Cost |
|-----------|----------|------|
| Auth | Supabase Free | $0 |
| Database | Supabase Free (500MB) | $0 |
| File Storage | Supabase Free (1GB) | $0 |
| API | Supabase (via client SDK) | $0 |
| Worker | Oracle Cloud Free Tier (4 OCPU, 24GB ARM) | $0 |

The Oracle free tier gives you an Ampere A1 instance with up to 4 OCPUs and
24GB RAM — you just need to stay under 3,000 OCPU-hours and 18,000 GB-hours
per month, which one conversion worker won't approach.

**Bandwidth:** Supabase free tier gives 5GB/month egress. Each conversion
artifact is roughly 1-5MB. At 5MB/artifact, that's 1,000 conversions/month
before hitting the limit. At $25/month for Pro plan (250GB egress, 100GB
storage), you'd scale comfortably.

### Option B: Everything on Oracle (Monolithic Supabase Self-Host)

Self-host Supabase on the Oracle free tier using Docker Compose. Same
architecture but everything on one box.

**Pros:** Zero external dependencies, no vendor lock-in.
**Cons:** The Oracle micro (1GB RAM) can barely run Supabase's stack
(Kong, GoTrue, PostgREST, Realtime, Postgres) — it needs ~2GB minimum.
The ARM Ampere A1 (up to 24GB) can handle it, but you're managing
infrastructure. More ops work for you.

### Option C: Fly.io / Railway

Deploy the FastAPI worker + Postgres on Fly.io or Railway. Both have free
tiers that include a small Postgres instance.

**Pros:** No cloud account needed, simple deployment.
**Cons:** Fly.io free tier is 3 shared-CPU VMs with 256MB RAM — too tight
for Calibre. Paid plans start at ~$15-20/month. You'd need a separate
solution for auth.

### Option D: Cloudflare Workers + R2 + Oracle Worker

Use Cloudflare Workers for the API layer (auth, rate limiting), Cloudflare
R2 for file storage (free tier: 10GB storage, 1M operations/month), and
Oracle for the Calibre worker.

**Pros:** R2 has zero egress fees — great for serving artifacts.
**Cons:** More moving parts. Workers have a 30s CPU limit — can't handle
conversion directly, still need the Oracle worker.

### Recommendation: Option A (Supabase + Oracle Worker)

Start with Supabase free tier for auth/API/storage and an Oracle Ampere A1
for the worker. If the project grows:

1. **Supabase Free → Pro ($25/mo):** 100GB storage, 250GB egress, 100K MAU
2. **Oracle Free → Paid:** The Ampere A1 is already generous at free tier
3. **Future:** Add Cloudflare CDN in front of artifact downloads

This keeps your monthly cost at **$0** for launch and predictable at
**$25-50/mo** as you grow.

---

## 5. Monetization Path

### Free Tier
- 20 conversions/day
- 500MB storage
- Standard queue priority
- Max 100MB per file
- 24-hour artifact retention

### Paid Tier ($3-5/month)
- Unlimited conversions
- 5GB storage
- Priority queue
- Max 500MB per file
- 7-day artifact retention
- Concurrent job processing

### How to implement

Add a `tier` column to `public.profiles`. Edge Functions check the tier
before allowing job creation. Stripe webhooks update the tier on payment.
Supabase has built-in Stripe integration via `supabase/payments`.

The Android app checks `GET /v1/health` to determine limits. The server's
`limits` field already supports returning `maxUploadBytes`,
`maxConcurrentJobs`, etc. Just extend it:

```json
{
  "limits": {
    "maxUploadBytes": 104857600,
    "maxConcurrentJobs": 1,
    "dailyJobLimit": 20,
    "storageQuotaBytes": 524288000,
    "tier": "free"
  }
}
```

---

## 6. Worker Design (Oracle)

### Simple Polling Worker

```
loop:
  sleep 5s
  GET supabase/rest/v1/jobs?status=eq.pending&order=created_at.asc&limit=1
  if job:
    download source_file from storage
    run ebook-convert
    upload artifact to storage
    PATCH job status = complete
```

No need for Celery, Redis, or complex queue infrastructure. Supabase's
Postgres is the queue. RLS ensures the worker uses a service_role key
that bypasses per-user policies.

### Dockerfile

```dockerfile
FROM python:3.12-slim-bookworm
RUN apt-get update && apt-get install -y calibre
COPY requirements.txt .
RUN pip install -r requirements.txt
COPY worker/ /worker
CMD ["python", "/worker/poll.py"]
```

### Resource Profile

- **Idle:** ~50MB RAM
- **Converting EPUB→MOBI:** ~200-400MB RAM, one CPU core for 2-10s
- **Converting PDF→MOBI:** ~300-600MB RAM, one CPU core for 10-60s
- **Cold start (Calibre import):** ~3-5s first conversion

The Oracle Ampere A1 with 4 OCPUs and 24GB RAM handles this easily, even
with multiple users. The Supabase free tier's 5GB monthly egress is the
real limit — roughly 1,000 conversions/month.

---

## 7. Recommended Next Steps

1. **Create Supabase project** (free) — set up auth providers
2. **Build the database schema** — profiles, jobs, rate_limits, storage_quotas
3. **Write the worker** — Python script that polls Supabase, runs Calibre,
   uploads results
4. **Provision Oracle Ampere A1** — install Docker, build worker image
5. **Add Edge Functions** — rate limiting + quota checks on job creation
6. **Ship `docker-compose.yml`** — so anyone can self-host their own worker
7. **Extend Android app** — update the `PageDropApiClient` to send user's
   auth token from Supabase SDK

The app already has the API client wired. The main work is the backend.
