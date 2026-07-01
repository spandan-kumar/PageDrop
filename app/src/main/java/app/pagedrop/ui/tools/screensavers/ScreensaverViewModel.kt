package app.pagedrop.ui.tools.screensavers

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pagedrop.data.KindleSettings
import app.pagedrop.tools.screensavers.KindleModel
import app.pagedrop.tools.screensavers.KindleModelRegistry
import app.pagedrop.tools.screensavers.ScreensaverProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScreensaverViewModel @Inject constructor(
    private val kindleSettings: KindleSettings
) : ViewModel() {

    data class ScreensaverState(
        val sourceUri: Uri? = null,
        val imageBytes: ByteArray? = null,
        val sourceFileName: String? = null,
        val selectedModel: KindleModel = KindleModelRegistry.defaultModel(),
        val processedImage: ScreensaverProcessor.ProcessingResult? = null,
        val isProcessing: Boolean = false,
        val isUploading: Boolean = false,
        val error: String? = null,
        val uploadSuccess: String? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ScreensaverState) return false
            return sourceUri == other.sourceUri &&
                    sourceFileName == other.sourceFileName &&
                    selectedModel == other.selectedModel &&
                    processedImage == other.processedImage &&
                    isProcessing == other.isProcessing &&
                    isUploading == other.isUploading &&
                    error == other.error &&
                    uploadSuccess == other.uploadSuccess &&
                    (imageBytes?.contentEquals(other.imageBytes) ?: (other.imageBytes == null))
        }

        override fun hashCode(): Int {
            var result = sourceUri.hashCode()
            result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
            result = 31 * result + (sourceFileName?.hashCode() ?: 0)
            result = 31 * result + selectedModel.hashCode()
            result = 31 * result + (processedImage?.hashCode() ?: 0)
            return result
        }
    }

    private val _state = MutableStateFlow(ScreensaverState())
    val state: StateFlow<ScreensaverState> = _state.asStateFlow()

    fun pickImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(sourceUri = uri, error = null, uploadSuccess = null, processedImage = null) }
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                val fileName = uri.lastPathSegment ?: "screensaver.jpg"
                _state.update { it.copy(imageBytes = bytes, sourceFileName = fileName) }
                processImage()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to read image: ${e.message}") }
            }
        }
    }

    fun selectModel(model: KindleModel) {
        _state.update { it.copy(selectedModel = model, uploadSuccess = null) }
        processImage()
    }

    private fun processImage() {
        val current = _state.value
        val bytes = current.imageBytes ?: return
        val model = current.selectedModel

        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true) }
            try {
                val result = ScreensaverProcessor.processImage(bytes, model)
                _state.update { it.copy(processedImage = result, isProcessing = false, error = null) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isProcessing = false,
                        error = "Processing failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun uploadScreensaver() {
        val current = _state.value
        val result = current.processedImage ?: return
        val fileName = current.sourceFileName ?: "screensaver.jpg"

        viewModelScope.launch {
            _state.update { it.copy(isUploading = true, error = null, uploadSuccess = null) }
            try {
                val uploadResult = ScreensaverProcessor.uploadToKindle(
                    imageBytes = result.bytes,
                    fileName = fileName,
                    host = kindleSettings.host,
                    port = kindleSettings.port,
                    user = kindleSettings.username,
                    pass = kindleSettings.password
                )
                if (uploadResult.isSuccess) {
                    _state.update { it.copy(isUploading = false, uploadSuccess = fileName) }
                } else {
                    _state.update {
                        it.copy(
                            isUploading = false,
                            error = uploadResult.exceptionOrNull()?.localizedMessage ?: "Upload failed"
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isUploading = false, error = e.localizedMessage) }
            }
        }
    }
}
