package com.imgmiax.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.appbar.MaterialToolbar
import com.imgmiax.app.ui.*

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.topAppBar)
        bottomNav = findViewById(R.id.bottomNavigation)

        setSupportActionBar(toolbar)

        if (savedInstanceState == null) {
            navigateTo(HomeFragment(), getString(R.string.nav_home))
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    navigateTo(HomeFragment(), getString(R.string.nav_home))
                    true
                }
                R.id.nav_files -> {
                    navigateTo(FileManagerFragment(), getString(R.string.nav_files))
                    true
                }
                R.id.nav_rom -> {
                    navigateTo(RomToolsFragment(), getString(R.string.nav_rom))
                    true
                }
                R.id.nav_archive -> {
                    navigateTo(ArchiveManagerFragment(), getString(R.string.nav_archive))
                    true
                }
                R.id.nav_more -> {
                    // Open drawer or submenu
                    showMoreMenuDialog()
                    false
                }
                else -> false
            }
        }
    }

    fun navigateTo(fragment: Fragment, title: String) {
        toolbar.title = title
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun showMoreMenuDialog() {
        val items = arrayOf(
            getString(R.string.nav_apk),
            getString(R.string.nav_editor),
            getString(R.string.nav_hex),
            getString(R.string.nav_logs),
            getString(R.string.nav_settings),
            getString(R.string.nav_developer)
        )

        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
        builder.setTitle(getString(R.string.more_tools))
        builder.setItems(items) { _, which ->
            when (which) {
                0 -> navigateTo(ApkToolsFragment(), getString(R.string.nav_apk))
                1 -> navigateTo(TextEditorFragment(), getString(R.string.nav_editor))
                2 -> navigateTo(HexViewerFragment(), getString(R.string.nav_hex))
                3 -> navigateTo(OperationsLogFragment(), getString(R.string.nav_logs))
                4 -> navigateTo(SettingsFragment(), getString(R.string.nav_settings))
                5 -> navigateTo(DeveloperFragment(), getString(R.string.nav_developer))
            }
        }
        builder.show()
    }
}
