# PageDrop Conversion Worker
#
# Polls Supabase for pending conversion jobs, runs Calibre CLI,
# uploads results back to Supabase Storage.
#
# Designed for Oracle Ampere A1 (ARM) or any Linux box with Docker.

import asyncio
import hashlib
import json
import os
import subprocess
import tempfile
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

import httpx

# ── Config ──────────────────────────────────────────────────────────────────

SUPABASE_URL = os.environ["SUPABASE_URL"]
SUPABASE_SERVICE_KEY = os.environ["SUPABASE_SERVICE_KEY"]  # service_role key

POLL_INTERVAL = int(os.environ.get("POLL_INTERVAL", "5"))  # seconds
MAX_CONCURRENT = int(os.environ.get("MAX_CONCURRENT", "1"))
JOB_TIMEOUT = int(os.environ.get("JOB_TIMEOUT", "300"))    # 5 minutes
ARTIFACT_DIR = Path("/tmp/pagedrop-artifacts")

HEADERS = {
    "apikey": SUPABASE_SERVICE_KEY,
    "Authorization": f"Bearer {SUPABASE_SERVICE_KEY}",
    "Content-Type": "application/json",
    "Prefer": "return=representation",
}

# ── Supabase REST helpers ───────────────────────────────────────────────────


def supabase_get(path: str, params: dict = None):
    url = f"{SUPABASE_URL}/rest/v1/{path.lstrip('/')}"
    with httpx.Client() as c:
        r = c.get(url, headers=HEADERS, params=params)
        r.raise_for_status()
        return r.json()


def supabase_patch(path: str, data: dict):
    url = f"{SUPABASE_URL}/rest/v1/{path.lstrip('/')}"
    headers = {**HEADERS, "Prefer": "return=minimal"}
    with httpx.Client() as c:
        r = c.patch(url, headers=headers, json=data)
        r.raise_for_status()


def supabase_upload(bucket: str, storage_path: str, file_bytes: bytes, content_type: str = "application/octet-stream"):
    """Upload a file to Supabase Storage using the service role."""
    url = f"{SUPABASE_URL}/storage/v1/object/{bucket}/{storage_path}"
    headers = {
        "apikey": SUPABASE_SERVICE_KEY,
        "Authorization": f"Bearer {SUPABASE_SERVICE_KEY}",
        "Content-Type": content_type,
    }
    with httpx.Client() as c:
        r = c.put(url, headers=headers, content=file_bytes)
        r.raise_for_status()
    return storage_path


def supabase_download(bucket: str, storage_path: str) -> bytes:
    """Download a file from Supabase Storage."""
    url = f"{SUPABASE_URL}/storage/v1/object/{bucket}/{storage_path}"
    headers = {
        "apikey": SUPABASE_SERVICE_KEY,
        "Authorization": f"Bearer {SUPABASE_SERVICE_KEY}",
    }
    with httpx.Client() as c:
        r = c.get(url, headers=headers)
        r.raise_for_status()
        return r.content


# ── Conversion ──────────────────────────────────────────────────────────────


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    h.update(path.read_bytes())
    return h.hexdigest()


async def run_calibre(input_path: Path, output_path: Path, timeout: int = JOB_TIMEOUT) -> tuple[int, str, str]:
    """Run ebook-convert, return (returncode, stdout, stderr)."""
    cmd = ["ebook-convert", str(input_path), str(output_path)]
    proc = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    try:
        stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=timeout)
        return proc.returncode or 0, stdout.decode(), stderr.decode()
    except asyncio.TimeoutError:
        proc.kill()
        return -1, "", "Timed out"


async def extract_cover(ebook_path: Path, output_path: Path) -> bool:
    """Extract cover image using ebook-meta."""
    cmd = ["ebook-meta", str(ebook_path), "--get-cover", str(output_path)]
    proc = await asyncio.create_subprocess_exec(*cmd, stdout=asyncio.PIPE, stderr=asyncio.PIPE)
    stdout, stderr = await proc.communicate()
    return output_path.exists() and output_path.stat().st_size > 0


async def process_job(job: dict) -> dict:
    """Process a single conversion job. Returns updated job fields."""
    job_id = job["id"]
    user_id = job["user_id"]
    source_path = job.get("source_storage_path")
    target_fmt = job.get("target_format", "mobi")

    log_lines = []
    warnings = []

    def log(msg):
        log_lines.append(f"[{datetime.now(timezone.utc).isoformat()}] {msg}")

    log(f"Starting job {job_id} ({job.get('job_type')})")

    try:
        # 1. Create temp workspace
        workdir = Path(tempfile.mkdtemp(prefix=f"pagedrop_{job_id}_"))
        source_file = workdir / f"source.{Path(source_path or 'source').suffix or 'epub'}"
        output_file = workdir / f"output.{target_fmt}"
        cover_file = workdir / "cover.jpg"

        # 2. Download source from Supabase Storage
        log(f"Downloading source: {source_path}")
        source_bytes = supabase_download("sources", source_path)
        source_file.write_bytes(source_bytes)
        fingerprint = sha256_file(source_file)

        # 3. Run Calibre conversion
        log(f"Converting to {target_fmt}...")
        rc, sout, serr = await run_calibre(source_file, output_file)

        if rc != 0:
            log(f"Conversion failed (exit {rc})")
            return {
                "status": "failed",
                "error_message": serr[:2000] if serr else f"Exit code {rc}",
                "logs": json.dumps(log_lines),
                "source_fingerprint": fingerprint,
                "completed_at": datetime.now(timezone.utc).isoformat(),
            }

        log(f"Conversion done: {output_file.stat().st_size} bytes")

        # 4. Extract cover
        cover_storage_path = None
        if await extract_cover(output_file, cover_file):
            log(f"Cover extracted: {cover_file.stat().st_size} bytes")
            cover_storage_path = supabase_upload(
                "artifacts", f"{user_id}/{job_id}/cover.jpg", cover_file.read_bytes(), "image/jpeg"
            )
        else:
            log("No cover extracted")

        # 5. Upload result to Storage
        result_bytes = output_file.read_bytes()
        result_storage_path = supabase_upload(
            "artifacts", f"{user_id}/{job_id}/output.{target_fmt}", result_bytes
        )

        log(f"Artifact uploaded: {result_storage_path}")

        return {
            "status": "complete",
            "result_storage_path": result_storage_path,
            "cover_storage_path": cover_storage_path,
            "source_fingerprint": fingerprint,
            "warnings": json.dumps(warnings),
            "logs": json.dumps(log_lines),
            "completed_at": datetime.now(timezone.utc).isoformat(),
        }

    except Exception as e:
        log(f"Error: {e}")
        return {
            "status": "failed",
            "error_message": str(e)[:2000],
            "logs": json.dumps(log_lines),
            "completed_at": datetime.now(timezone.utc).isoformat(),
        }
    finally:
        # Cleanup temp files
        import shutil
        try:
            shutil.rmtree(workdir)
        except Exception:
            pass


async def poll_loop():
    """Main loop: poll Supabase for pending jobs, process them."""
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)

    semaphore = asyncio.Semaphore(MAX_CONCURRENT)

    async def claim_and_process():
        """Atomically claim a pending job and process it."""
        async with semaphore:
            # Find next pending job
            params = {
                "status": "eq.pending",
                "order": "created_at.asc",
                "limit": "1",
            }
            jobs = supabase_get("/jobs", params=params)
            if not jobs:
                return

            job = jobs[0]
            job_id = job["id"]

            # Claim it atomically
            supabase_patch(f"/jobs?id=eq.{job_id}&status=eq.pending", {
                "status": "processing",
                "started_at": datetime.now(timezone.utc).isoformat(),
            })

            result = await process_job(job)

            # Update job with result
            supabase_patch(f"/jobs?id=eq.{job_id}", result)

    while True:
        try:
            await claim_and_process()
        except Exception as e:
            print(f"[worker] Error in poll cycle: {e}")
        await asyncio.sleep(POLL_INTERVAL)


def main():
    print(f"[worker] Starting PageDrop Worker")
    print(f"[worker] Supabase: {SUPABASE_URL}")
    print(f"[worker] Poll interval: {POLL_INTERVAL}s")
    print(f"[worker] Max concurrent: {MAX_CONCURRENT}")
    print(f"[worker] Job timeout: {JOB_TIMEOUT}s")

    asyncio.run(poll_loop())


if __name__ == "__main__":
    main()
