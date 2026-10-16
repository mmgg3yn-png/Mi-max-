package com.imgmiax.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
import com.imgmiax.app.R
import com.imgmiax.app.settings.PreferencesManager

class SettingsFragment : Fragment() {

    private lateinit var prefs: PreferencesManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_settings, container, false)
        prefs = PreferencesManager(requireContext())

        val rgTheme = v.findViewById<RadioGroup>(R.id.rgTheme)
        val switchFileSize = v.findViewById<SwitchMaterial>(R.id.switchShowFileSize)
        val switchHidden = v.findViewById<SwitchMaterial>(R.id.switchShowHidden)
        val tvAppVersion = v.findViewById<TextView>(R.id.tvAppVersion)

        tvAppVersion.text = "IMGmiax v1.0.0 (Build 100)"

        when (prefs.themeMode) {
            "dark" -> rgTheme.check(R.id.rbDark)
            "light" -> rgTheme.check(R.id.rbLight)
            else -> rgTheme.check(R.id.rbSystem)
        }

        rgTheme.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbDark -> {
                    prefs.themeMode = "dark"
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                }
                R.id.rbLight -> {
                    prefs.themeMode = "light"
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
                R.id.rbSystem -> {
                    prefs.themeMode = "system"
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
            }
        }

        switchFileSize.isChecked = prefs.showFileSizes
        switchFileSize.setOnCheckedChangeListener { _, isChecked ->
            prefs.showFileSizes = isChecked
        }

        switchHidden.isChecked = prefs.showHiddenFiles
        switchHidden.setOnCheckedChangeListener { _, isChecked ->
            prefs.showHiddenFiles = isChecked
        }

        return v
    }
}
