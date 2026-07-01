package app.pagedrop.server

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
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

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private val OCTET_MEDIA = "application/octet-stream".toMediaType()

    val isAvailable: Boolean get() = serverSettings.isConfigured

    suspend fun checkHealth(): HealthResponse? = withContext(Dispatchers.IO) {
        try {
            val req = buildGetRequest("/v1/health")
            val response = client.newCall(req).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let { json.decodeFromString<HealthResponse>(it) }
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Health check failed: ${e.message}")
            null
        }
    }

    suspend fun shouldUseServer(format: String): Boolean {
        if (!serverSettings.isConfigured) return false
        val nativeFormats = setOf("AZW3", "MOBI", "PRC")
        if (format.uppercase() in nativeFormats) return false
        val health = checkHealth() ?: return false
        return health.ok && health.workers["calibreCli"] == true
    }

    suspend fun convertBook(uri: Uri, outputFormat: String = "mobi"): ServerJob = withContext(Dispatchers.IO) {
        val sourceFile = copyUriToTemp(uri)
            ?: throw IllegalStateException("Failed to copy URI to temp file")

        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("targetProfile", "kindle-stock")
                .addFormDataPart("outputFormat", outputFormat)
                .addFormDataPart("includeCover", "true")
                .addFormDataPart(
                    "file",
                    sourceFile.name,
                    sourceFile.asRequestBody(OCTET_MEDIA)
                )
                .build()

            val req = buildPostRequest("/v1/convert", body = requestBody)
                ?: error("Not configured")

            val response = client.newCall(req).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("Convert failed: ${response.code} ${response.message}")
            }

            val job = response.body?.string()?.let {
                json.decodeFromString<JobResponse>(it)
            } ?: throw IllegalStateException("Empty response")

            val coverDir = File(context.filesDir, "covers")
            if (!coverDir.exists()) coverDir.mkdirs()

            ServerJob(
                jobId = job.jobId,
                artifactFile = File(context.cacheDir, "pagedrop_dl_${job.jobId}.$outputFormat"),
                coverFile = File(coverDir, "${job.jobId}_cover.jpg")
            )
        } finally {
            sourceFile.delete()
        }
    }

    suspend fun pollJob(jobId: String): JobStatus? = withContext(Dispatchers.IO) {
        var retries = 0
        while (retries < MAX_POLL_RETRIES) {
            delay(POLL_INTERVAL_MS)
            try {
                val req = buildGetRequest("/v1/jobs/$jobId")
                val response = client.newCall(req).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    val status = json.decodeFromString<JobStatus>(body)
                    if (status.status != "queued") return@withContext status
                }
            } catch (_: Exception) { }
            retries++
        }
        null
    }

    suspend fun downloadArtifact(artifactId: String, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = buildGetRequest("/v1/artifacts/$artifactId")
            val response = client.newCall(req).execute()
            if (response.isSuccessful) {
                response.body?.byteStream()?.use { input ->
                    outputFile.outputStream().use { output -> input.copyTo(output) }
                }
                true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Artifact download failed: ${e.message}", e)
            false
        }
    }

    suspend fun downloadCover(coverId: String, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = buildGetRequest("/v1/artifacts/$coverId")
            val response = client.newCall(req).execute()
            if (response.isSuccessful) {
                response.body?.byteStream()?.use { input ->
                    outputFile.outputStream().use { output -> input.copyTo(output) }
                }
                true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Cover download failed: ${e.message}", e)
            false
        }
    }

    suspend fun convertArticle(url: String): ServerJob = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            ArticleConvertRequest.serializer(),
            ArticleConvertRequest(url = url)
        ).toRequestBody(JSON_MEDIA)

        val req = buildPostRequest("/v1/articles/convert", body = body)
            ?: error("Not configured")

        val response = client.newCall(req).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("Article convert failed: ${response.code}")
        }

        val job = response.body?.string()?.let {
            json.decodeFromString<JobResponse>(it)
        } ?: throw IllegalStateException("Empty response")

        val coverDir = File(context.filesDir, "covers")
        if (!coverDir.exists()) coverDir.mkdirs()

        ServerJob(
            jobId = job.jobId,
            artifactFile = File(context.cacheDir, "pagedrop_article_${job.jobId}.mobi"),
            coverFile = File(coverDir, "${job.jobId}_cover.jpg")
        )
    }

    private fun buildGetRequest(path: String): Request {
        val url = serverSettings.serverUrl.trimEnd('/') + path
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${serverSettings.apiToken}")
            .get()
            .build()
    }

    private fun buildPostRequest(
        path: String,
        body: okhttp3.RequestBody = "".toRequestBody(null)
    ): Request? {
        if (!serverSettings.isConfigured) return null
        val url = serverSettings.serverUrl.trimEnd('/') + path
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${serverSettings.apiToken}")
            .post(body)
            .build()
    }

    private fun copyUriToTemp(uri: Uri): File? {
        return try {
            val ext = uri.lastPathSegment?.substringAfterLast(".", "") ?: "tmp"
            val tempFile = File(context.cacheDir, "pagedrop_upload_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy URI: ${e.message}", e)
            null
        }
    }
}

data class ServerJob(
    val jobId: String,
    val artifactFile: File,
    val coverFile: File
)
