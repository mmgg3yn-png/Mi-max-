package com.imgmiax.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.imgmiax.app.settings.PreferencesManager

class IMGmiaxApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val prefs = PreferencesManager(this)
        when (prefs.themeMode) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
