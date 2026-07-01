package app.pagedrop.ui.tools.fonts

import app.pagedrop.tools.fonts.FontItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FontsViewModelTest {

    @Test
    fun initialState_listsAllCatalogItems() = runTest {
        val state = FontsViewModel.FontsUiState()
        assertEquals(6, state.fonts.size)
    }

    @Test
    fun fontState_defaults() = runTest {
        val font = FontItem("id", "Name", "Desc", "https://example.com/font.ttf", "OFL", listOf("/mnt/us/fonts"))
        val fs = FontsViewModel.FontState(font = font)
        assertEquals("Name", fs.font.name)
        assertFalse(fs.isInstalled)
        assertFalse(fs.isInstalling)
        assertNull(fs.error)
    }

    @Test
    fun fontState_installed() = runTest {
        val font = FontItem("id", "N", "D", "url", "OFL", listOf("/test"))
        val fs = FontsViewModel.FontState(font = font, isInstalled = true)
        assertTrue(fs.isInstalled)
    }

    @Test
    fun fontState_installing() = runTest {
        val font = FontItem("id", "N", "D", "url", "OFL", listOf("/test"))
        val fs = FontsViewModel.FontState(font = font, isInstalling = true)
        assertTrue(fs.isInstalling)
    }

    @Test
    fun fontState_withError() = runTest {
        val font = FontItem("id", "N", "D", "url", "OFL", listOf("/test"))
        val fs = FontsViewModel.FontState(font = font, error = "Download failed")
        assertEquals("Download failed", fs.error)
    }
}
