package app.pagedrop.tools.fonts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FontCatalogTest {

    @Test
    fun catalog_containsExpectedFonts() {
        val ids = FontCatalog.items.map { it.id }
        assertTrue(ids.contains("atkinson_hyperlegible"))
        assertTrue(ids.contains("literata"))
        assertTrue(ids.contains("chareink6"))
        assertTrue(ids.contains("open_dyslexic"))
        assertTrue(ids.contains("bitter"))
        assertTrue(ids.contains("bookerly"))
    }

    @Test
    fun allFonts_haveNames() {
        FontCatalog.items.forEach { font ->
            assertTrue(font.name.isNotBlank())
            assertTrue(font.license.isNotBlank())
        }
    }

    @Test
    fun downloadableFonts_haveUrls() {
        val downloadable = FontCatalog.items.filter { it.url.isNotBlank() }
        assertTrue(downloadable.size >= 4)
        downloadable.forEach { font ->
            assertTrue(font.targetDirectories.isNotEmpty())
        }
    }

    @Test
    fun bookerly_isListedButNotDownloadable() {
        val bookerly = FontCatalog.items.find { it.id == "bookerly" }
        assertNotNull(bookerly)
        assertEquals("", bookerly!!.url)
        assertTrue(bookerly.targetDirectories.isEmpty())
    }

    @Test
    fun targetDirectories_areValidPaths() {
        FontCatalog.items.forEach { font ->
            font.targetDirectories.forEach { dir ->
                assertTrue(dir.startsWith("/mnt/us"))
            }
        }
    }
}
