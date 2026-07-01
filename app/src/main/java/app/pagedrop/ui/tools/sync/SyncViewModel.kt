package app.pagedrop.ui.tools.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pagedrop.data.KindleSettings
import app.pagedrop.tools.sync.KoreaderSyncBroker
import app.pagedrop.tools.sync.KoreaderSyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val kindleSettings: KindleSettings
) : ViewModel() {

    data class SyncState(
        val isScanning: Boolean = false,
        val results: List<KoreaderSyncResult> = emptyList(),
        val error: String? = null
    )

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    fun scanSdrFolders() {
        viewModelScope.launch {
            _state.update { it.copy(isScanning = true, error = null) }
            try {
                val result = KoreaderSyncBroker.scanSdrFolders(
                    host = kindleSettings.host,
                    port = kindleSettings.port,
                    user = kindleSettings.username,
                    pass = kindleSettings.password
                )
                if (result.isSuccess) {
                    _state.update {
                        it.copy(
                            isScanning = false,
                            results = result.getOrDefault(emptyList())
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isScanning = false,
                            error = result.exceptionOrNull()?.localizedMessage ?: "Scan failed"
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isScanning = false,
                        error = e.localizedMessage ?: "Scan failed"
                    )
                }
            }
        }
    }
}
