package app.pagedrop.transfer.thumbnails

import org.junit.Assert.assertEquals
import org.junit.Test

class KindleThumbnailGeneratorTest {

    @Test
    fun thumbnailName_producesCorrectPattern() {
        val name = KindleThumbnailGenerator.thumbnailName("abc-123-def")
        assertEquals("thumbnail_abc-123-def_EBOK_portrait.jpg", name)
    }

    @Test
    fun thumbnailName_handlesShortUuid() {
        val name = KindleThumbnailGenerator.thumbnailName("ff")
        assertEquals("thumbnail_ff_EBOK_portrait.jpg", name)
    }

    @Test
    fun thumbnailName_handlesComplexUuid() {
        val name = KindleThumbnailGenerator.thumbnailName("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        assertEquals("thumbnail_a1b2c3d4-e5f6-7890-abcd-ef1234567890_EBOK_portrait.jpg", name)
    }
}
