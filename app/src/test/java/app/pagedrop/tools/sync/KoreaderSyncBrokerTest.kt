package app.pagedrop.tools.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KoreaderSyncBrokerTest {

    @Test
    fun parseMetadata_basicFields() {
        val lua = """
            ["title"] = "Gravity's Rainbow",
            ["authors"] = "Thomas Pynchon",
            ["language"] = "en",
            ["stats"] = { ["current_page"] = "142", ["total_pages"] = "776", },
        """.trimIndent()

        val meta = KoreaderSyncBroker.parseMetadata(lua)
        assertNotNull(meta)
        assertEquals("Gravity's Rainbow", meta!!.title)
        assertEquals("Thomas Pynchon", meta.authors)
        assertEquals("en", meta.language)
        assertEquals(142, meta.lastPosition)
        assertEquals(776, meta.totalPages)
    }

    @Test
    fun parseMetadata_percentFinished() {
        val lua = """
            ["title"] = "Short Read",
            ["summary"] = { ["percent_finished"] = "0.73", },
        """.trimIndent()

        val meta = KoreaderSyncBroker.parseMetadata(lua)
        assertNotNull(meta)
        assertEquals(0.73f, meta!!.percentFinished)
    }

    @Test
    fun parseMetadata_minimalData() {
        val lua = """["title"] = "Minimal","""
        val meta = KoreaderSyncBroker.parseMetadata(lua)
        assertNotNull(meta)
        assertEquals("Minimal", meta!!.title)
        assertNull(meta.authors)
        assertNull(meta.lastPosition)
    }

    @Test
    fun parseHighlights_single() {
        val lua = """
            ["bookmarks"] = {
                { ["text"] = "The sky above the port was the color of television", ["chapter"] = "Chapter 1", ["page"] = "3", },
            }
        """.trimIndent()

        val highlights = KoreaderSyncBroker.parseHighlights(lua)
        assertEquals(1, highlights.size)
        assertEquals("The sky above the port was the color of television", highlights[0].text)
        assertEquals("Chapter 1", highlights[0].chapter)
        assertEquals(3, highlights[0].position)
    }

    @Test
    fun parseHighlights_multiple() {
        val lua = """
            ["bookmarks"] = {
                { ["text"] = "First highlight", ["page"] = "10" },
                { ["text"] = "Second highlight", ["page"] = "45" },
                { ["text"] = "Third highlight", ["note"] = "Great passage", ["page"] = "90" },
            }
        """.trimIndent()

        val highlights = KoreaderSyncBroker.parseHighlights(lua)
        assertEquals(3, highlights.size)
        assertEquals("First highlight", highlights[0].text)
        assertEquals(10, highlights[0].position)
        assertEquals("Third highlight", highlights[2].text)
        assertEquals("Great passage", highlights[2].note)
    }

    @Test
    fun parseHighlights_noBookmarks_returnsEmpty() {
        val lua = """["something"] = "else","""
        val highlights = KoreaderSyncBroker.parseHighlights(lua)
        assertTrue(highlights.isEmpty())
    }

    @Test
    fun parseHighlights_skipsEntriesWithoutText() {
        val lua = """
            ["bookmarks"] = {
                { ["page"] = "5" },
                { ["text"] = "This one works", },
            }
        """.trimIndent()

        val highlights = KoreaderSyncBroker.parseHighlights(lua)
        assertEquals(1, highlights.size)
        assertEquals("This one works", highlights[0].text)
    }
}
