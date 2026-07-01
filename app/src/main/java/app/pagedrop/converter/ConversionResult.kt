package app.pagedrop.converter

data class ConversionResult(
    val success: Boolean,
    val coverBytes: ByteArray? = null,
    val kindleUuid: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConversionResult) return false
        return success == other.success &&
                coverBytes.contentEquals(other.coverBytes) &&
                kindleUuid == other.kindleUuid
    }

    override fun hashCode(): Int {
        var result = success.hashCode()
        result = 31 * result + (coverBytes?.contentHashCode() ?: 0)
        result = 31 * result + (kindleUuid?.hashCode() ?: 0)
        return result
    }
}
