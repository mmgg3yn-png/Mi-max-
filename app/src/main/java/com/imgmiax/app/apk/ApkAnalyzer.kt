package com.imgmiax.app.apk

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

object ApkAnalyzer {

    suspend fun analyzeApk(context: Context, apkFile: File): ApkDetails = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_PERMISSIONS)
        }

        var appName = apkFile.name
        var packageName = archiveInfo?.packageName ?: "غير متوفر"
        var versionName = archiveInfo?.versionName ?: "غير معروف"
        var versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo?.longVersionCode ?: 0L
        } else {
            @Suppress("DEPRECATION")
            (archiveInfo?.versionCode ?: 0).toLong()
        }

        var minSdk = 0
        var targetSdk = 0
        archiveInfo?.applicationInfo?.let { appInfo ->
            appInfo.sourceDir = apkFile.absolutePath
            appInfo.publicSourceDir = apkFile.absolutePath
            val label = pm.getApplicationLabel(appInfo).toString()
            if (label.isNotEmpty() && !label.startsWith("com.")) {
                appName = label
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                minSdk = appInfo.minSdkVersion
            }
            targetSdk = appInfo.targetSdkVersion
        }

        val permissions = archiveInfo?.requestedPermissions?.toList() ?: emptyList()

        // Inspect internal entries via ZipFile
        var dexCount = 0
        var totalEntries = 0
        val nativeLibs = mutableSetOf<String>()
        var signatureFound = false

        try {
            ZipFile(apkFile).use { zip ->
                totalEntries = zip.size()
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (name.startsWith("classes") && name.endsWith(".dex")) {
                        dexCount++
                    } else if (name.startsWith("lib/")) {
                        val parts = name.split("/")
                        if (parts.size >= 2 && parts[1].isNotEmpty()) {
                            nativeLibs.add(parts[1])
                        }
                    } else if (name.startsWith("META-INF/") && (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"))) {
                        signatureFound = true
                    }
                }
            }
        } catch (_: Exception) {}

        ApkDetails(
            appName = appName,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            permissions = permissions,
            nativeArchitectures = nativeLibs.toList().sorted(),
            dexFilesCount = dexCount,
            totalEntriesCount = totalEntries,
            signatureFound = signatureFound,
            signingCertificates = if (signatureFound) listOf("v1 JAR Signature Scheme") else emptyList()
        )
    }
}
