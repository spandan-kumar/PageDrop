package app.pagedrop.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KindleSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("kindle_settings", Context.MODE_PRIVATE)

    var host: String
        get() = prefs.getString("host", "") ?: ""
        set(value) = prefs.edit().putString("host", value).apply()

    var port: Int
        get() = prefs.getInt("port", 22)
        set(value) = prefs.edit().putInt("port", value).apply()

    var username: String
        get() = prefs.getString("username", "root") ?: "root"
        set(value) = prefs.edit().putString("username", value).apply()

    var password: String
        get() = prefs.getString("password", "") ?: ""
        set(value) = prefs.edit().putString("password", value).apply()

    var targetDirectory: String
        get() = prefs.getString("target_directory", "/mnt/us/documents") ?: "/mnt/us/documents"
        set(value) = prefs.edit().putString("target_directory", value).apply()

    var triggerRescan: Boolean
        get() = prefs.getBoolean("trigger_rescan", true)
        set(value) = prefs.edit().putBoolean("trigger_rescan", value).apply()
}
