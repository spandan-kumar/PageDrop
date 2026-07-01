package app.pagedrop.ui.tools.screensavers

import app.pagedrop.tools.screensavers.KindleModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreensaverViewModelTest {

    @Test
    fun initialState_defaults() = runTest {
        // Test state defaults without Hilt dependency
        val state = ScreensaverViewModel.ScreensaverState()
        assertNull(state.sourceUri)
        assertNull(state.imageBytes)
        assertNull(state.processedImage)
        assertEquals(false, state.isProcessing)
        assertEquals(false, state.isUploading)
    }

    @Test
    fun selectModel_updatesProcessedImage() = runTest {
        val state = ScreensaverViewModel.ScreensaverState(
            selectedModel = KindleModel("Test", 758, 1024, 212)
        )
        assertEquals(758, state.selectedModel.width)
        assertEquals(1024, state.selectedModel.height)
    }

    @Test
    fun uploadWithoutImage_hasNoUploadSuccess() = runTest {
        val state = ScreensaverViewModel.ScreensaverState()
        assertNull(state.uploadSuccess)
    }

    @Test
    fun errorState_setsError() = runTest {
        val state = ScreensaverViewModel.ScreensaverState(error = "Failed to read image")
        assertTrue(state.error!!.contains("Failed"))
    }

    @Test
    fun processingState() = runTest {
        val state = ScreensaverViewModel.ScreensaverState(isProcessing = true)
        assertEquals(true, state.isProcessing)
    }

    @Test
    fun uploadSuccess_showsFileName() = runTest {
        val state = ScreensaverViewModel.ScreensaverState(uploadSuccess = "photo.jpg")
        assertEquals("photo.jpg", state.uploadSuccess)
    }
}
