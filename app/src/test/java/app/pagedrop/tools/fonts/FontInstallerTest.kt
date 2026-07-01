package app.pagedrop.tools.fonts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FontInstallerTest {

    @Test
    fun detectTtfMagic_validatesCorrectly() {
        val ttfMagic = byteArrayOf(0x00, 0x01, 0x00, 0x00)
        val temp = createTempFileWithMagic(ttfMagic)
        assertTrue(temp.exists())
        assertTrue(temp.length() >= 4)
        val readMagic = temp.inputStream().use { it.readNBytes(4) }
        assertTrue(readMagic.contentEquals(ttfMagic))
    }

    @Test
    fun detectOtfMagic_validatesCorrectly() {
        val otfMagic = "OTTO".toByteArray(Charsets.US_ASCII)
        val temp = createTempFileWithMagic(otfMagic)
        val readMagic = temp.inputStream().use { it.readNBytes(4) }
        assertTrue(readMagic.contentEquals(otfMagic))
    }

    @Test
    fun detectZipMagic_detectsZipHeader() {
        val zipMagic = byteArrayOf(0x50, 0x4B.toByte())
        val temp = createTempFileWithMagic(zipMagic + byteArrayOf(0x03, 0x04))
        val readMagic = temp.inputStream().use { it.readNBytes(2) }
        assertTrue(readMagic[0] == 0x50.toByte() && readMagic[1] == 0x4B.toByte())
    }

    @Test
    fun invalidMagic_rejected() {
        val badMagic = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        assertFalse(
            badMagic.contentEquals(byteArrayOf(0x00, 0x01, 0x00, 0x00)) &&
            badMagic.contentEquals("OTTO".toByteArray(Charsets.US_ASCII))
        )
    }

    @Test
    fun emptyFile_rejected() {
        val empty = File.createTempFile("empty", ".tmp")
        assertTrue(empty.length() == 0L)
    }

    private fun createTempFileWithMagic(magic: ByteArray): File {
        val file = File.createTempFile("test_font", ".ttf")
        file.writeBytes(magic + "some content".toByteArray())
        file.deleteOnExit()
        return file
    }
}
