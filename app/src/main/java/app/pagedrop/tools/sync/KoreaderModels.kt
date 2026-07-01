package app.pagedrop.tools.sync

data class KoreaderBookMetadata(
    val title: String?,
    val authors: String?,
    val language: String?,
    val series: String?,
    val description: String?,
    val lastPosition: Int?,
    val percentFinished: Float?,
    val totalPages: Int?
)

data class KoreaderHighlight(
    val text: String,
    val chapter: String?,
    val position: Int?,
    val timestamp: Long?,
    val note: String?,
    val chapterProgress: Float?
)

data class KoreaderSyncResult(
    val bookPath: String,
    val metadata: KoreaderBookMetadata?,
    val highlights: List<KoreaderHighlight>,
    val hasError: Boolean = false
)
