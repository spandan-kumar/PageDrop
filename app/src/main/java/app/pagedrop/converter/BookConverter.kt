package app.pagedrop.converter

import android.content.Context
import java.io.File

/**
 * Unified entry point for converting any supported ebook format to MOBI.
 *
 * Auto-detects the input format from the file extension and delegates
 * to the appropriate converter:
 * - EPUB → [EpubToMobiConverter]
 * - PDF  → [PdfToMobiConverter]
 * - TXT  → [TxtToMobiConverter]
 */
object BookConverter {

    private val CONVERTIBLE_FORMATS = listOf("EPUB", "PDF", "TXT")

    /**
     * Converts the given input file to MOBI format.
     *
     * @param context Android context (needed for PDF conversion)
     * @param inputFile source file (EPUB, PDF, or TXT)
     * @param outputFile target MOBI file (will be created)
     * @return [ConversionResult] with success status, cover bytes, and Kindle UUID
     */
    suspend fun convertToMobi(context: Context, inputFile: File, outputFile: File): ConversionResult {
        val fallbackTitle = outputFile.nameWithoutExtension
        return when (inputFile.extension.lowercase()) {
            "epub" -> EpubToMobiConverter.convert(inputFile, outputFile, fallbackTitle = fallbackTitle)
            "pdf" -> PdfToMobiConverter.convert(context, inputFile, outputFile)
            "txt" -> TxtToMobiConverter.convert(inputFile, outputFile, title = fallbackTitle)
            else -> ConversionResult(success = false)
        }
    }

    /**
     * Checks whether the given format string can be converted to MOBI.
     *
     * @param format the file format (e.g. "EPUB", "pdf", "TXT")
     * @return true if we have a converter for this format
     */
    fun canConvert(format: String): Boolean = format.uppercase() in CONVERTIBLE_FORMATS
}
