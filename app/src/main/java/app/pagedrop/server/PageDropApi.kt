package app.pagedrop.server

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val ok: Boolean,
    val version: String,
    val workers: Map<String, Boolean>,
    val limits: Limits? = null
)

@Serializable
data class Limits(
    val maxUploadBytes: Long,
    val maxConcurrentJobs: Int
)

@Serializable
data class ConvertRequest(
    val targetProfile: String = "kindle-stock",
    val outputFormat: String = "mobi",
    val includeCover: Boolean = true,
    val title: String? = null,
    val author: String? = null
)

@Serializable
data class ArticleConvertRequest(
    val url: String,
    val targetProfile: String = "kindle-stock",
    val outputFormat: String = "mobi",
    val includeCover: Boolean = true
)

@Serializable
data class JobResponse(
    val jobId: String,
    val status: String
)

@Serializable
data class JobStatus(
    val jobId: String,
    val status: String,
    val progress: Int = 0,
    val result: JobResult? = null
)

@Serializable
data class JobResult(
    val artifactId: String,
    val coverId: String? = null,
    val fileName: String,
    val format: String,
    val title: String,
    val author: String,
    val sourceFingerprint: String? = null,
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList()
)
