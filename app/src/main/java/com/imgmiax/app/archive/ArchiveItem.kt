package com.imgmiax.app.archive

data class ArchiveItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val uncompressedSize: Long,
    val compressedSize: Long,
    val crc: Long,
    val modifiedTime: Long
)
