// PageDrop Edge Function: Create Conversion Job
//
// Validates rate limits, storage quotas, and file size before
// creating a job in the queue. Called by the Android app via
// Supabase client SDK.
//
// Deploy as a Supabase Edge Function:
//   supabase functions deploy create-job --no-verify-jwt

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.6";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const supabase = createClient(supabaseUrl, supabaseServiceKey);

const MAX_FILE_SIZE = 100 * 1024 * 1024; // 100 MB

interface CreateJobBody {
  jobType: "convert" | "article";
  targetFormat?: string;
  targetProfile?: string;
  title?: string;
  author?: string;
  url?: string; // only for article jobs
  fileSizeBytes?: number; // for pre-flight check
}

serve(async (req) => {
  // 1. Get authenticated user
  const authHeader = req.headers.get("Authorization")?.replace("Bearer ", "");
  if (!authHeader) {
    return new Response("Unauthorized", { status: 401 });
  }

  const { data: { user }, error: authError } = await supabase.auth.getUser(authHeader);
  if (authError || !user) {
    return new Response("Invalid token", { status: 401 });
  }

  // 2. Parse body
  const body: CreateJobBody = await req.json();
  const jobType = body.jobType;

  if (!["convert", "article"].includes(jobType)) {
    return new Response("Invalid jobType", { status: 400 });
  }

  // 3. Check rate limit for today
  const today = new Date().toISOString().slice(0, 10);
  const { data: profile } = await supabase
    .from("profiles")
    .select("daily_job_limit")
    .eq("id", user.id)
    .single();

  const dailyLimit = profile?.daily_job_limit ?? 20;

  const { count: todayCount } = await supabase
    .from("rate_limits")
    .select("id", { count: "exact", head: true })
    .eq("user_id", user.id)
    .eq("date", today);

  if ((todayCount ?? 0) >= dailyLimit) {
    return new Response(
      JSON.stringify({ error: "Daily job limit reached" }),
      { status: 429, headers: { "Content-Type": "application/json" } }
    );
  }

  // 4. Check storage quota (only for convert jobs that upload files)
  if (jobType === "convert") {
    const { data: storageUsage } = await supabase
      .from("storage_usage")
      .select("bytes_used")
      .eq("user_id", user.id)
      .single();

    const { data: profileQuota } = await supabase
      .from("profiles")
      .select("storage_quota_bytes")
      .eq("id", user.id)
      .single();

    const used = storageUsage?.bytes_used ?? 0;
    const quota = profileQuota?.storage_quota_bytes ?? 500 * 1024 * 1024;
    const estimatedSize = body.fileSizeBytes ?? 0;

    if (used + estimatedSize > quota) {
      return new Response(
        JSON.stringify({ error: "Storage quota exceeded" }),
        { status: 403, headers: { "Content-Type": "application/json" } }
      );
    }
  }

  // 5. Validate file size
  if (body.fileSizeBytes && body.fileSizeBytes > MAX_FILE_SIZE) {
    return new Response(
      JSON.stringify({ error: "File exceeds maximum size of 100MB" }),
      { status: 413, headers: { "Content-Type": "application/json" } }
    );
  }

  // 6. Create the job
  const { data: job, error: insertError } = await supabase
    .from("jobs")
    .insert({
      user_id: user.id,
      job_type: jobType,
      target_format: body.targetFormat ?? "mobi",
      target_profile: body.targetProfile ?? "kindle-stock",
      title: body.title ?? null,
      author: body.author ?? null,
      file_size_bytes: body.fileSizeBytes ?? null,
    })
    .select("id, status")
    .single();

  if (insertError) {
    console.error("Failed to insert job:", insertError);
    return new Response(
      JSON.stringify({ error: "Failed to create job" }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }

  // 7. Increment rate limit counter
  await supabase.rpc("increment_rate_limit", { p_user_id: user.id });

  // 8. Return job info to the app
  return new Response(
    JSON.stringify({
      jobId: job.id,
      status: job.status,
    }),
    {
      status: 201,
      headers: {
        "Content-Type": "application/json",
        "Access-Control-Allow-Origin": "*",
      },
    }
  );
});
