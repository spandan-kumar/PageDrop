package app.pagedrop.update

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The update switch (default on) and what the last check found. Only used by builds with BuildConfig.SELF_UPDATE. */
@Singleton
class UpdateSettings internal constructor(private val prefs: SharedPreferences) {
    @Inject
    constructor(@ApplicationContext context: Context) :
        this(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        _enabled.value = on
    }

    private val _dismissed = MutableStateFlow(prefs.getString(KEY_DISMISSED, null))
    /** The version whose "allow updates" card the user dismissed with "Not now"; null if none. */
    val dismissedVersion: StateFlow<String?> = _dismissed.asStateFlow()

    fun dismissCardFor(version: String) {
        prefs.edit().putString(KEY_DISMISSED, version).apply()
        _dismissed.value = version
    }

    /** A newer version found while installs weren't allowed; kept so the card survives a restart. */
    var permissionVersion: String?
        get() = prefs.getString(KEY_PERMISSION, null)
        set(value) {
            val e = prefs.edit()
            if (value == null) e.remove(KEY_PERMISSION) else e.putString(KEY_PERMISSION, value)
            e.apply()
        }

    private val _announced = MutableStateFlow(prefs.getString(KEY_ANNOUNCED, null))
    /** Set after an update: the version name to show "Updated to x" for, until dismissed. */
    val announcedVersion: StateFlow<String?> = _announced.asStateFlow()

    fun dismissAnnouncement() {
        prefs.edit().remove(KEY_ANNOUNCED).apply()
        _announced.value = null
    }

    /**
     * Call on each launch. Remembers the running version code; when it is higher than the last one seen, an
     * update happened and "Updated to [versionName]" is announced once. A first launch (nothing stored) only records.
     */
    fun noteLaunch(versionCode: Long, versionName: String) {
        val last = prefs.getLong(KEY_SEEN_CODE, 0L)
        val e = prefs.edit().putLong(KEY_SEEN_CODE, versionCode)
        if (last in 1 until versionCode) {
            e.putString(KEY_ANNOUNCED, versionName)
            _announced.value = versionName
        }
        e.apply()
    }

    /** Epoch millis of the last check that reached GitHub, or 0. */
    var lastCheckedMs: Long
        get() = prefs.getLong(KEY_CHECKED, 0L)
        set(value) = prefs.edit().putLong(KEY_CHECKED, value).apply()

    /** Version of a downloaded, verified APK waiting in the cache, and the hash it was verified against. */
    val readyVersion: String? get() = prefs.getString(KEY_READY, null)
    val readySha256: String? get() = prefs.getString(KEY_READY_SHA, null)

    fun setReady(version: String, sha256: String) {
        prefs.edit().putString(KEY_READY, version).putString(KEY_READY_SHA, sha256).apply()
    }

    fun clearReady() {
        prefs.edit().remove(KEY_READY).remove(KEY_READY_SHA).apply()
    }

    /** The last download or install failure, in plain words, until the next successful check. */
    var lastError: String?
        get() = prefs.getString(KEY_ERROR, null)
        set(value) = prefs.edit().putString(KEY_ERROR, value).apply()

    companion object {
        const val PREFS = "pagedrop_update"
        private const val KEY_ENABLED = "auto_update"
        private const val KEY_CHECKED = "last_checked_ms"
        private const val KEY_READY = "ready_version"
        private const val KEY_READY_SHA = "ready_sha256"
        private const val KEY_ERROR = "last_error"
        private const val KEY_DISMISSED = "dismissed_card_version"
        private const val KEY_PERMISSION = "permission_version"
        private const val KEY_ANNOUNCED = "announced_version"
        private const val KEY_SEEN_CODE = "last_seen_version_code"
    }
}
