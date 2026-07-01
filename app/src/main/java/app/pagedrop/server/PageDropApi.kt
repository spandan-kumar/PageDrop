package app.pagedrop.server

import kotlinx.serialization.Serializable

@Serializable
data class JobResponse(
    val jobId: String,
    val status: String
)

@Serializable
data class JobStatus(
    val id: String,
    val status: String,
    val job_type: String? = null,
    val target_format: String? = null,
    val target_profile: String? = null,
    val result_storage_path: String? = null,
    val cover_storage_path: String? = null,
    val title: String? = null,
    val author: String? = null,
    val warnings: String? = null,
    val logs: String? = null,
    val error_message: String? = null,
    val created_at: String? = null,
    val completed_at: String? = null
)

@Serializable
data class SessionResponse(
    val accessToken: String
)
