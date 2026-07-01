package app.pagedrop.server

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PageDropApiClient @Inject constructor(
    private val serverSettings: ServerSettings,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PageDropApiClient"
        private const val POLL_INTERVAL_MS = 2000L
        private const val MAX_POLL_RETRIES = 45
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    val isAvailable: Boolean get() = serverSettings.isConfigured

    private val baseUrl: String? get() = serverSettings.supabaseUrl.takeIf { it.isNotBlank() }
    private val anonKey: String? get() = serverSettings.supabaseAnonKey.takeIf { it.isNotBlank() }

    private fun headers() = mapOf(
        "apikey" to (anonKey ?: ""),
        "Authorization" to "Bearer ${anonKey ?: ""}",
        "Content-Type" to "application/json"
    )

    suspend fun signInAnonymously(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl}/auth/v1/signup" ?: return@withContext Result.failure(IllegalStateException("Not configured"))
            // For anonymous, use the magic link approach
            Result.success(anonKey ?: "")
        } catch (e: Exception) {
            Log.e(TAG, "Auth failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun createConvertJob(
        uri: Uri,
        targetFormat: String = "mobi",
        title: String? = null,
        author: String? = null
    ): JobResponse? = withContext(Dispatchers.IO) {
        try {
            val base = baseUrl ?: return@withContext null
            val key = anonKey ?: return@withContext null

            // 1. Upload source to Supabase Storage
            val sourceBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            val fileName = uri.lastPathSegment ?: "upload_${System.currentTimeMillis()}"
            val storagePath = "$fileName"

            val uploadUrl = "$base/storage/v1/object/sources/$storagePath"
            val uploadReq = Request.Builder()
                .url(uploadUrl)
                .header("apikey", key)
                .header("Authorization", "Bearer $key")
                .put(sourceBytes.toRequestBody("application/octet-stream".toMediaType()))
                .build()
            client.newCall(uploadReq).execute().use { if (!it.isSuccessful) return@withContext null }

            // 2. Call Edge Function to create the job
            val body = buildJsonObject {
                put("jobType", "convert")
                put("targetFormat", targetFormat)
                title?.let { put("title", it) }
                author?.let { put("author", it) }
                put("fileSizeBytes", sourceBytes.size.toLong())
            }.toString()

            val fnUrl = "$base/functions/v1/create-job"
            val fnReq = Request.Builder()
                .url(fnUrl)
                .header("apikey", key)
                .header("Authorization", "Bearer $key")
                .post(body.toRequestBody(JSON_MEDIA))
                .build()

            val fnResponse = client.newCall(fnReq).execute()
            if (!fnResponse.isSuccessful) return@withContext null
            val responseBody = fnResponse.body?.string() ?: return@withContext null
            json.decodeFromString<JobResponse>(responseBody)
        } catch (e: Exception) {
            Log.e(TAG, "Create job failed: ${e.message}", e)
            null
        }
    }

    suspend fun createArticleJob(url: String): JobResponse? = withContext(Dispatchers.IO) {
        try {
            val base = baseUrl ?: return@withContext null
            val key = anonKey ?: return@withContext null

            val body = buildJsonObject {
                put("jobType", "article")
                put("url", url)
                put("targetFormat", "mobi")
            }.toString()

            val fnUrl = "$base/functions/v1/create-job"
            val req = Request.Builder()
                .url(fnUrl)
                .header("apikey", key)
                .header("Authorization", "Bearer $key")
                .post(body.toRequestBody(JSON_MEDIA))
                .build()

            val response = client.newCall(req).execute()
            if (!response.isSuccessful) return@withContext null
            json.decodeFromString<JobResponse>(response.body?.string() ?: return@withContext null)
        } catch (e: Exception) {
            Log.e(TAG, "Create article job failed: ${e.message}", e)
            null
        }
    }

    suspend fun pollJob(jobId: String): JobStatus? = withContext(Dispatchers.IO) {
        try {
            val base = baseUrl ?: return@withContext null
            val key = anonKey ?: return@withContext null
            var retries = 0

            while (retries < MAX_POLL_RETRIES) {
                delay(POLL_INTERVAL_MS)
                val url = "$base/rest/v1/jobs?id=eq.$jobId&select=*"
                val req = Request.Builder()
                    .url(url)
                    .header("apikey", key)
                    .header("Authorization", "Bearer $key")
                    .get()
                    .build()

                val response = client.newCall(req).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    val results = json.decodeFromString<List<JobStatus>>(body)
                    val status = results.firstOrNull() ?: continue
                    if (status.status in listOf("complete", "failed")) return@withContext status
                }
                retries++
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Poll failed: ${e.message}", e)
            null
        }
    }

    suspend fun downloadArtifact(storagePath: String, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val base = baseUrl ?: return@withContext false
            val key = anonKey ?: return@withContext false

            val url = "$base/storage/v1/object/artifacts/$storagePath"
            val req = Request.Builder()
                .url(url)
                .header("apikey", key)
                .header("Authorization", "Bearer $key")
                .get()
                .build()

            val response = client.newCall(req).execute()
            if (!response.isSuccessful) return@withContext false
            response.body?.byteStream()?.use { input ->
                outputFile.outputStream().use { output -> input.copyTo(output) }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            false
        }
    }

    suspend fun shouldUseServer(format: String): Boolean {
        if (!serverSettings.isConfigured) return false
        val nativeFormats = setOf("AZW3", "MOBI", "PRC")
        return format.uppercase() !in nativeFormats
    }
}
