package app.pagedrop.ui.tools.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pagedrop.data.KindleSettings
import app.pagedrop.tools.dashboard.DeviceCommandRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val kindleSettings: KindleSettings
) : ViewModel() {

    data class DashboardState(
        val isLoading: Boolean = false,
        val deviceStatus: DeviceCommandRunner.DeviceStatus? = null,
        val lastResult: DeviceCommandRunner.CommandResult? = null,
        val error: String? = null
    )

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    val repairRecipes = DeviceCommandRunner.repairRecipes

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val status = DeviceCommandRunner.getDeviceStatus(
                    host = kindleSettings.host,
                    port = kindleSettings.port,
                    user = kindleSettings.username,
                    pass = kindleSettings.password
                )
                _state.update { it.copy(isLoading = false, deviceStatus = status) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to fetch device status: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun triggerRescan() {
        viewModelScope.launch {
            val result = DeviceCommandRunner.triggerRescan(
                kindleSettings.host, kindleSettings.port,
                kindleSettings.username, kindleSettings.password
            )
            _state.update { it.copy(lastResult = result, error = result.stderr.ifBlank { null }) }
        }
    }

    fun reboot() {
        viewModelScope.launch {
            DeviceCommandRunner.rebootDevice(
                kindleSettings.host, kindleSettings.port,
                kindleSettings.username, kindleSettings.password
            )
        }
    }

    fun runRepair(recipe: DeviceCommandRunner.RepairRecipe) {
        viewModelScope.launch {
            val result = DeviceCommandRunner.runRepair(
                recipe = recipe,
                host = kindleSettings.host,
                port = kindleSettings.port,
                user = kindleSettings.username,
                pass = kindleSettings.password
            )
            _state.update { it.copy(lastResult = result, error = result.stderr.ifBlank { null }) }
        }
    }
}
