package app.pagedrop.ui.tools.sync

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncViewModelTest {

    @Test
    fun initialState() = runTest {
        val state = SyncViewModel.SyncState()
        assertEquals(false, state.isScanning)
        assertTrue(state.results.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun scanningState() = runTest {
        val state = SyncViewModel.SyncState(isScanning = true)
        assertEquals(true, state.isScanning)
    }

    @Test
    fun errorState() = runTest {
        val state = SyncViewModel.SyncState(isScanning = false, error = "SFTP connection failed")
        assertEquals("SFTP connection failed", state.error)
    }

    @Test
    fun resultsPopulated() = runTest {
        val results = listOf(
            app.pagedrop.tools.sync.KoreaderSyncResult(
                bookPath = "/mnt/us/documents/book.pdf",
                metadata = null, highlights = emptyList()
            )
        )
        val state = SyncViewModel.SyncState(results = results)
        assertEquals(1, state.results.size)
    }
}
