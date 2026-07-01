package app.pagedrop.server

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
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

    private val supabase: SupabaseClient? by lazy {
        if (!serverSettings.isConfigured) null
        else createSupabaseClient(
            supabaseUrl = serverSettings.supabaseUrl,
            supabaseKey = serverSettings.supabaseAnonKey
        ) {
            install(Auth)
            install(Functions)
        }
    }

    val isAvailable: Boolean get() = serverSettings.isConfigured

    suspend fun signInAnonymously(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val client = supabase ?: return@withContext Result.failure(IllegalStateException("Not configured"))
            val session = client.auth.signInAnonymously()
            Result.success(session.accessToken)
        } catch (e: Exception) {
            Log.e(TAG, "Auth failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val client = supabase ?: return@withContext Result.failure(IllegalStateException("Not configured"))
            val session = client.auth.signInWith(Email(email, password))
            Result.success(session.accessToken)
        } catch (e: Exception) {
            Log.e(TAG, "Email auth failed: ${e.message}", e)
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
            val client = supabase ?: return@withContext null
            val token = client.auth.currentAccessTokenOrNull() ?: return@withContext null
            val userId = client.auth.currentUserOrNull()?.id ?: return@withContext null

            val sourceBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            val fileName = uri.lastPathSegment ?: "upload_${System.currentTimeMillis()}"
            val storagePath = "$userId/sources/$fileName"
            client.storage.from("sources").upload(storagePath, sourceBytes)

            val body = buildJsonObject {
                put("jobType", "convert")
                put("targetFormat", targetFormat)
                title?.let { put("title", it) }
                author?.let { put("author", it) }
                put("fileSizeBytes", sourceBytes.size.toLong())
            }

            val response = client.functions.invoke("create-job", body = body.toString())
            json.decodeFromString<JobResponse>(response.data)
        } catch (e: Exception) {
            Log.e(TAG, "Create job failed: ${e.message}", e)
            null
        }
    }

    suspend fun createArticleJob(url: String): JobResponse? = withContext(Dispatchers.IO) {
        try {
            val client = supabase ?: return@withContext null
            val body = buildJsonObject {
                put("jobType", "article")
                put("url", url)
                put("targetFormat", "mobi")
            }
            val response = client.functions.invoke("create-job", body = body.toString())
            json.decodeFromString<JobResponse>(response.data)
        } catch (e: Exception) {
            Log.e(TAG, "Create article job failed: ${e.message}", e)
            null
        }
    }

    suspend fun pollJob(jobId: String): JobStatus? = withContext(Dispatchers.IO) {
        try {
            val client = supabase ?: return@withContext null
            var retries = 0
            while (retries < MAX_POLL_RETRIES) {
                delay(POLL_INTERVAL_MS)
                val results = client.postgrest["jobs"]
                    .select { eq("id", jobId) }
                    .decodeList<JobStatus>()
                val status = results.firstOrNull() ?: continue
                if (status.status in listOf("complete", "failed")) return@withContext status
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
            val client = supabase ?: return@withContext false
            val bytes = client.storage.from("artifacts").download(storagePath)
            outputFile.outputStream().use { it.write(bytes) }
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
