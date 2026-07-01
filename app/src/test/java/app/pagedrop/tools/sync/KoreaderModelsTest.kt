package app.pagedrop.tools.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KoreaderModelsTest {

    @Test
    fun bookMetadata_constructor() {
        val meta = KoreaderBookMetadata(
            title = "Test",
            authors = "Author",
            language = "en",
            series = "Series", description = "A book",
            lastPosition = 42, percentFinished = 0.5f, totalPages = 200
        )
        assertEquals("Test", meta.title)
        assertEquals("Author", meta.authors)
        assertEquals(42, meta.lastPosition)
        assertEquals(200, meta.totalPages)
    }

    @Test
    fun bookMetadata_nullFields() {
        val meta = KoreaderBookMetadata(
            title = "T", authors = null, language = null,
            series = null, description = null,
            lastPosition = null, percentFinished = null, totalPages = null
        )
        assertNull(meta.authors)
        assertNull(meta.percentFinished)
    }

    @Test
    fun highlight_constructor() {
        val h = KoreaderHighlight(
            text = "Highlighted text", chapter = "Ch1",
            position = 10, timestamp = 1000L,
            note = "My note", chapterProgress = 0.3f
        )
        assertEquals("Highlighted text", h.text)
        assertEquals("Ch1", h.chapter)
        assertEquals(10, h.position)
        assertEquals("My note", h.note)
    }

    @Test
    fun highlight_nullFields() {
        val h = KoreaderHighlight(
            text = "Text", chapter = null, position = null,
            timestamp = null, note = null, chapterProgress = null
        )
        assertNull(h.chapter)
        assertNull(h.note)
    }

    @Test
    fun syncResult_withHighlights() {
        val highlights = listOf(
            KoreaderHighlight("Text 1", "Ch1", 1, null, null, null),
            KoreaderHighlight("Text 2", "Ch2", 5, null, null, null)
        )
        val result = KoreaderSyncResult(
            bookPath = "/mnt/us/documents/book.pdf",
            metadata = null,
            highlights = highlights
        )
        assertEquals(2, result.highlights.size)
        assertTrue(result.bookPath.contains("book.pdf"))
    }

    @Test
    fun syncResult_withError() {
        val result = KoreaderSyncResult(
            bookPath = "/path/book.sdr",
            metadata = null, highlights = emptyList(),
            hasError = true
        )
        assertTrue(result.hasError)
    }
}
