package app.pagedrop.ui.tools.dashboard

import app.pagedrop.tools.dashboard.DeviceCommandRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardViewModelTest {

    @Test
    fun initialState() = runTest {
        val state = DashboardViewModel.DashboardState()
        assertEquals(false, state.isLoading)
        assertNull(state.deviceStatus)
        assertNull(state.error)
    }

    @Test
    fun loadingState() = runTest {
        val state = DashboardViewModel.DashboardState(isLoading = true)
        assertEquals(true, state.isLoading)
    }

    @Test
    fun errorState() = runTest {
        val state = DashboardViewModel.DashboardState(error = "Connection failed")
        assertEquals("Connection failed", state.error)
    }

    @Test
    fun withDeviceStatus() = runTest {
        val status = DeviceCommandRunner.DeviceStatus(
            firmwareVersion = "5.14.2",
            serialNumber = null,
            freeSpace = "2.3G/3.0G",
            uptime = "12d",
            batteryLevel = "67%",
            whispernetBlocked = false,
            otaBlocked = true,
            koreaderInstalled = true
        )
        val state = DashboardViewModel.DashboardState(deviceStatus = status)
        assertEquals("5.14.2", state.deviceStatus?.firmwareVersion)
        assertEquals(true, state.deviceStatus?.koreaderInstalled)
        assertEquals(true, state.deviceStatus?.otaBlocked)
    }

    @Test
    fun allRepairRecipesExist() = runTest {
        val recipes = DeviceCommandRunner.repairRecipes
        assertEquals(5, recipes.size)
    }
}
