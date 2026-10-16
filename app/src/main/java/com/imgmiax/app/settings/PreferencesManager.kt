package com.imgmiax.app.settings

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("imgmiax_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_THEME = "pref_theme" // "system", "dark", "light"
        const val KEY_SHOW_FILE_SIZE = "pref_show_file_size"
        const val KEY_SHOW_HIDDEN = "pref_show_hidden"
    }

    var themeMode: String
        get() = prefs.getString(KEY_THEME, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var showFileSizes: Boolean
        get() = prefs.getBoolean(KEY_SHOW_FILE_SIZE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_FILE_SIZE, value).apply()

    var showHiddenFiles: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HIDDEN, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_HIDDEN, value).apply()
}
