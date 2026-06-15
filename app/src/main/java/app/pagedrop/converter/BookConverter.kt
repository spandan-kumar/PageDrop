package app.pagedrop.converter

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Unified entry point for converting any supported ebook format to a Kindle-compatible format.
 *
 * Converts to plain text (.txt) which is natively supported by Kindle for both
 * browser download and reading. This avoids the complexity of MOBI binary generation.
 *
 * Supported: EPUB → TXT, PDF → TXT, TXT → TXT (passthrough copy)
 */
object BookConverter {

    private const val TAG = "BookConverter"
    private val CONVERTIBLE_FORMATS = listOf("EPUB", "PDF", "TXT")

    /**
     * Converts the given input file to a Kindle-compatible text file.
     *
     * @param context Android context (needed for PDF conversion)
     * @param inputFile source file (EPUB, PDF, or TXT)
     * @param outputFile target file (will be created as .txt)
     * @return true if conversion succeeded
     */
    suspend fun convertToKindleFormat(context: Context, inputFile: File, outputFile: File): Boolean {
        val format = inputFile.extension.lowercase()
        Log.d(TAG, "Converting $format → txt: ${inputFile.name} → ${outputFile.name}")

        return when (format) {
            "epub" -> EpubToTextConverter.convert(inputFile, outputFile)
            "pdf" -> PdfToTextConverter.convert(context, inputFile, outputFile)
            "txt" -> {
                // Just copy the file
                inputFile.copyTo(outputFile, overwrite = true)
                true
            }
            else -> false
        }
    }

    /**
     * Checks whether the given format string can be converted.
     */
    fun canConvert(format: String): Boolean = format.uppercase() in CONVERTIBLE_FORMATS

    /**
     * Returns the output format for conversion.
     */
    fun outputFormat(): String = "TXT"

    /**
     * Returns the output extension for conversion.
     */
    fun outputExtension(): String = "txt"
}
