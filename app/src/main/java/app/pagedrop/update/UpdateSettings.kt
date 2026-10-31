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
    }
}
