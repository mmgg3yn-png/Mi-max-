package com.imgmiax.app.apk

data class ApkDetails(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val permissions: List<String>,
    val nativeArchitectures: List<String>,
    val dexFilesCount: Int,
    val totalEntriesCount: Int,
    val signatureFound: Boolean,
    val signingCertificates: List<String>
)
