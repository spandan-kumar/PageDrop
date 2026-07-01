package app.pagedrop.server

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("pagedrop_server", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    var supabaseUrl: String
        get() = prefs.getString("supabase_url", "https://bzvvpwykluklvvjyzytn.supabase.co") ?: ""
        set(value) = prefs.edit().putString("supabase_url", value).apply()

    var supabaseAnonKey: String
        get() = prefs.getString("supabase_anon_key", "sb_publishable_JFKy2UrHhnIxZyXDDDUoAQ_JW5ApwbR") ?: ""
        set(value) = prefs.edit().putString("supabase_anon_key", value).apply()

    val isConfigured: Boolean
        get() = enabled && supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
}
