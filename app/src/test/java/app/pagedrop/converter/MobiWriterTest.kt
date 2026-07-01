package app.pagedrop.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MobiWriterTest {

    @Test
    fun mobiWriter_generatesKindleUuid() {
        val html = "<html><body><p>Test content</p></body></html>"
        val writer = MobiWriter(
            title = "Test Book",
            author = "Test Author",
            htmlContent = html.toByteArray(Charsets.UTF_8)
        )

        val outputFile = File.createTempFile("test", ".mobi")
        try {
            writer.write(outputFile)
            val uuid = writer.kindleUuid
            assertNotNull(uuid)
            assertTrue(uuid.isNotEmpty())
            assertTrue(uuid.length in 32..38) // UUID format
        } finally {
            outputFile.delete()
        }
    }

    @Test
    fun mobiWriter_producesValidPdbHeader() {
        val html = "<html><body><h1>Title</h1></body></html>"
        val writer = MobiWriter(
            title = "Test",
            author = "Author",
            htmlContent = html.toByteArray(Charsets.UTF_8)
        )

        val outputFile = File.createTempFile("test2", ".mobi")
        try {
            writer.write(outputFile)
            val bytes = outputFile.readBytes()
            // PDB name starts at offset 0, "BOOKMOBI" at offset 60
            assertTrue(bytes.size > 70)
            assertEquals('B'.code.toByte(), bytes[60])
            assertEquals('O'.code.toByte(), bytes[61])
            assertEquals('O'.code.toByte(), bytes[62])
            assertEquals('K'.code.toByte(), bytes[63])
        } finally {
            outputFile.delete()
        }
    }

    @Test
    fun mobiWriter_withCoverImage_producesLargerFile() {
        val html = "<html><body><p>Content</p></body></html>"
        val cover = ByteArray(1000) { i -> (i % 256).toByte() }
        val writer = MobiWriter(
            title = "Cover Test",
            author = "Author",
            htmlContent = html.toByteArray(Charsets.UTF_8),
            coverImage = cover
        )

        val outputFile = File.createTempFile("test_cover", ".mobi")
        try {
            writer.write(outputFile)
            assertTrue(outputFile.length() > 1000)
        } finally {
            outputFile.delete()
        }
    }
}
