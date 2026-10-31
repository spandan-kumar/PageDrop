package app.pagedrop.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pagedrop.BuildConfig
import app.pagedrop.update.UpdateManager
import app.pagedrop.update.UpdatePrompts
import app.pagedrop.update.UpdateScheduler
import app.pagedrop.update.UpdateSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Drives the two update cards at the top of the Library (GitHub builds only). */
@HiltViewModel
class UpdatePromptsViewModel @Inject constructor(
    private val settings: UpdateSettings,
    private val manager: UpdateManager,
    private val scheduler: UpdateScheduler,
) : ViewModel() {

    /** The version to offer "Allow updates" for, or null when no card should show. */
    val allowCardVersion: StateFlow<String?> =
        combine(manager.status, settings.enabled, settings.dismissedVersion) { status, enabled, dismissed ->
            UpdatePrompts.allowCardVersion(BuildConfig.SELF_UPDATE, enabled, status, dismissed)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The version just updated to, until dismissed. */
    val updatedTo: StateFlow<String?> = settings.announcedVersion

    fun notNow(version: String) = settings.dismissCardFor(version)

    fun turnOffAutomaticUpdates() {
        settings.setEnabled(false)
        scheduler.sync()
    }

    /** Back from Android's "install unknown apps" screen: if allowed now, download if needed and install. */
    fun onResumed() {
        if (manager.permissionMayHaveChanged()) scheduler.checkNow(installAfter = true)
    }

    fun dismissUpdatedTo() = settings.dismissAnnouncement()
}
