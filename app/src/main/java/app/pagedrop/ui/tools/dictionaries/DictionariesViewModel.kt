package app.pagedrop.ui.tools.dictionaries

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pagedrop.data.KindleSettings
import app.pagedrop.tools.dictionaries.DictionaryCatalog
import app.pagedrop.tools.dictionaries.DictionaryInstaller
import app.pagedrop.tools.dictionaries.DictionaryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DictionariesViewModel @Inject constructor(
    application: Application,
    private val kindleSettings: KindleSettings
) : AndroidViewModel(application) {

    data class DictState(
        val dict: DictionaryItem,
        val isInstalled: Boolean = false,
        val isInstalling: Boolean = false,
        val error: String? = null
    )

    data class DictUiState(
        val dictionaries: List<DictState> = DictionaryCatalog.items.map { DictState(dict = it) }
    )

    private val _state = MutableStateFlow(DictUiState())
    val state: StateFlow<DictUiState> = _state.asStateFlow()

    fun installDictionary(dict: DictionaryItem) {
        viewModelScope.launch {
            val current = _state.value.dictionaries.toMutableList()
            val index = current.indexOfFirst { it.dict.id == dict.id }
            if (index < 0) return@launch

            current[index] = current[index].copy(isInstalling = true, error = null)
            _state.value = DictUiState(dictionaries = current)

            try {
                val cacheDir = File(getApplication<Application>().cacheDir, "dictionaries")
                cacheDir.mkdirs()

                val extractResult = DictionaryInstaller.downloadAndExtract(dict, cacheDir)
                if (extractResult.isFailure) {
                    val msg = extractResult.exceptionOrNull()?.localizedMessage ?: "Download failed"
                    current[index] = current[index].copy(isInstalling = false, error = msg)
                    _state.value = DictUiState(dictionaries = current)
                    return@launch
                }

                val installResult = DictionaryInstaller.installToKoreader(
                    dictDir = extractResult.getOrThrow(),
                    host = kindleSettings.host,
                    port = kindleSettings.port,
                    user = kindleSettings.username,
                    pass = kindleSettings.password
                )

                if (installResult.isFailure) {
                    val msg = installResult.exceptionOrNull()?.localizedMessage ?: "Install failed"
                    current[index] = current[index].copy(isInstalling = false, error = msg)
                    _state.value = DictUiState(dictionaries = current)
                    return@launch
                }

                current[index] = current[index].copy(isInstalling = false, isInstalled = true)
                _state.value = DictUiState(dictionaries = current)
            } catch (e: Exception) {
                current[index] = current[index].copy(
                    isInstalling = false,
                    error = e.localizedMessage ?: "Install failed"
                )
                _state.value = DictUiState(dictionaries = current)
            }
        }
    }
}
