package app.pagedrop.ui.tools.fonts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pagedrop.data.KindleSettings
import app.pagedrop.tools.fonts.FontCatalog
import app.pagedrop.tools.fonts.FontInstaller
import app.pagedrop.tools.fonts.FontItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FontsViewModel @Inject constructor(
    application: Application,
    private val kindleSettings: KindleSettings
) : AndroidViewModel(application) {

    data class FontState(
        val font: FontItem,
        val isInstalled: Boolean = false,
        val isInstalling: Boolean = false,
        val error: String? = null
    )

    data class FontsUiState(
        val fonts: List<FontState> = FontCatalog.items.map { FontState(font = it) }
    )

    private val _state = MutableStateFlow(FontsUiState())
    val state: StateFlow<FontsUiState> = _state.asStateFlow()

    fun installFont(font: FontItem) {
        viewModelScope.launch {
            val current = _state.value.fonts.toMutableList()
            val index = current.indexOfFirst { it.font.id == font.id }
            if (index < 0) return@launch

            current[index] = current[index].copy(isInstalling = true, error = null)
            _state.value = FontsUiState(fonts = current)

            try {
                val cacheDir = File(getApplication<Application>().cacheDir, "fonts")
                cacheDir.mkdirs()

                val downloadResult = FontInstaller.downloadAndValidate(font, cacheDir)
                if (downloadResult.isFailure) {
                    val msg = downloadResult.exceptionOrNull()?.localizedMessage ?: "Download failed"
                    current[index] = current[index].copy(isInstalling = false, error = msg)
                    _state.value = FontsUiState(fonts = current)
                    return@launch
                }

                val fontFile = downloadResult.getOrThrow()

                for (targetDir in font.targetDirectories) {
                    FontInstaller.installToKindle(
                        fontFile = fontFile,
                        targetPath = targetDir,
                        host = kindleSettings.host,
                        port = kindleSettings.port,
                        user = kindleSettings.username,
                        pass = kindleSettings.password
                    )
                }

                current[index] = current[index].copy(isInstalling = false, isInstalled = true)
                _state.value = FontsUiState(fonts = current)
            } catch (e: Exception) {
                current[index] = current[index].copy(
                    isInstalling = false,
                    error = e.localizedMessage ?: "Install failed"
                )
                _state.value = FontsUiState(fonts = current)
            }
        }
    }
}
