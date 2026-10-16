package com.imgmiax.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.imgmiax.app.MainActivity
import com.imgmiax.app.R

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        val mainActivity = activity as? MainActivity

        view.findViewById<MaterialCardView>(R.id.cardFileManager).setOnClickListener {
            mainActivity?.navigateTo(FileManagerFragment(), getString(R.string.nav_files))
        }

        view.findViewById<MaterialCardView>(R.id.cardRomTools).setOnClickListener {
            mainActivity?.navigateTo(RomToolsFragment(), getString(R.string.nav_rom))
        }

        view.findViewById<MaterialCardView>(R.id.cardArchive).setOnClickListener {
            mainActivity?.navigateTo(ArchiveManagerFragment(), getString(R.string.nav_archive))
        }

        view.findViewById<MaterialCardView>(R.id.cardApkTools).setOnClickListener {
            mainActivity?.navigateTo(ApkToolsFragment(), getString(R.string.nav_apk))
        }

        view.findViewById<MaterialCardView>(R.id.cardHexViewer).setOnClickListener {
            mainActivity?.navigateTo(HexViewerFragment(), getString(R.string.nav_hex))
        }

        view.findViewById<MaterialCardView>(R.id.cardTextEditor).setOnClickListener {
            mainActivity?.navigateTo(TextEditorFragment(), getString(R.string.nav_editor))
        }

        view.findViewById<MaterialCardView>(R.id.cardDeveloper).setOnClickListener {
            mainActivity?.navigateTo(DeveloperFragment(), getString(R.string.nav_developer))
        }

        return view
    }
}
