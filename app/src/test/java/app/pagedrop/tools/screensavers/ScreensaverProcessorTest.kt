package app.pagedrop.tools.screensavers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreensaverProcessorTest {

    @Test
    fun processingResult_equality() {
        val a = ScreensaverProcessor.ProcessingResult(
            bytes = byteArrayOf(1, 2, 3),
            width = 758, height = 1024,
            originalWidth = 1000, originalHeight = 1500
        )
        val b = ScreensaverProcessor.ProcessingResult(
            bytes = byteArrayOf(1, 2, 3),
            width = 758, height = 1024,
            originalWidth = 1000, originalHeight = 1500
        )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun processingResult_differentBytes_notEqual() {
        val a = ScreensaverProcessor.ProcessingResult(
            bytes = byteArrayOf(1), width = 758, height = 1024,
            originalWidth = 1000, originalHeight = 1500
        )
        val b = ScreensaverProcessor.ProcessingResult(
            bytes = byteArrayOf(2), width = 758, height = 1024,
            originalWidth = 1000, originalHeight = 1500
        )
        assertTrue(a != b)
    }

    @Test
    fun processingResult_hasCorrectDimensions() {
        val result = ScreensaverProcessor.ProcessingResult(
            bytes = byteArrayOf(0xFF.toByte()),
            width = 1264, height = 1680,
            originalWidth = 2000, originalHeight = 3000
        )
        assertEquals(1264, result.width)
        assertEquals(1680, result.height)
        assertEquals(2000, result.originalWidth)
        assertEquals(3000, result.originalHeight)
    }
}
