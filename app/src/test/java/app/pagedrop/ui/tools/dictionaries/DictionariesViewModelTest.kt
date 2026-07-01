package app.pagedrop.ui.tools.dictionaries

import app.pagedrop.tools.dictionaries.DictionaryItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionariesViewModelTest {

    @Test
    fun initialState_listsAllCatalogItems() = runTest {
        val state = DictionariesViewModel.DictUiState()
        assertEquals(3, state.dictionaries.size)
    }

    @Test
    fun dictState_defaults() = runTest {
        val dict = DictionaryItem("id", "Name", "English", "Desc", "stardict", "https://example.com/dict.tar.bz2", "GPL")
        val ds = DictionariesViewModel.DictState(dict = dict)
        assertEquals("Name", ds.dict.name)
        assertFalse(ds.isInstalled)
        assertFalse(ds.isInstalling)
        assertNull(ds.error)
    }

    @Test
    fun dictState_installed() = runTest {
        val dict = DictionaryItem("id", "N", "E", "D", "stardict", "url", "GPL")
        val ds = DictionariesViewModel.DictState(dict = dict, isInstalled = true)
        assertTrue(ds.isInstalled)
    }

    @Test
    fun dictState_installing() = runTest {
        val dict = DictionaryItem("id", "N", "E", "D", "stardict", "url", "GPL")
        val ds = DictionariesViewModel.DictState(dict = dict, isInstalling = true)
        assertTrue(ds.isInstalling)
    }

    @Test
    fun dictState_withError() = runTest {
        val dict = DictionaryItem("id", "N", "E", "D", "stardict", "url", "GPL")
        val ds = DictionariesViewModel.DictState(dict = dict, error = "Extraction failed")
        assertEquals("Extraction failed", ds.error)
    }
}
