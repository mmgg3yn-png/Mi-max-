package com.imgmiax.app.rom

enum class RomImageFormat(val displayName: String, val extension: String) {
    SPARSE("Android Sparse Image", "img"),
    BOOT_IMAGE("Android Boot Image (Kernel/Ramdisk)", "img"),
    VBMETA("Android Verified Boot (VBMeta)", "img"),
    SUPER_LP("Android Dynamic Partitions (super.img)", "img"),
    EXT4("Linux Extended 4 Filesystem (ext4)", "img"),
    EROFS("Enhanced Read-Only Filesystem (EROFS)", "img"),
    F2FS("Flash-Friendly Filesystem (F2FS)", "img"),
    SQUASHFS("SquashFS Compressed Filesystem", "img"),
    RAW_UNKNOWN("Raw Partition Image / Unknown", "img"),
    EMPTY_OR_CORRUPT("Empty or Corrupted File", "")
}

data class RomDetectionResult(
    val format: RomImageFormat,
    val details: Map<String, String>,
    val headerDescription: String,
    val isSparse: Boolean = false,
    val canExtract: Boolean = false,
    val canConvertToRaw: Boolean = false,
    val warningMessage: String? = null
)

data class SparseHeader(
    val magic: Long,
    val majorVersion: Int,
    val minorVersion: Int,
    val fileHeaderSize: Int,
    val chunkHeaderSize: Int,
    val blockSize: Long,
    val totalBlocks: Long,
    val totalChunks: Long,
    val imageChecksum: Long
) {
    val uncompressedSizeBytes: Long
        get() = totalBlocks * blockSize
}

data class SparseChunkHeader(
    val chunkType: Int,
    val reserved: Int,
    val chunkSizeBlocks: Long,
    val totalSize: Long
)

data class BootImageHeaderInfo(
    val headerVersion: Int,
    val kernelSize: Long,
    val ramdiskSize: Long,
    val secondSize: Long = 0,
    val dtbSize: Long = 0,
    val pageSize: Long = 2048,
    val osVersion: String = "",
    val osPatchLevel: String = "",
    val cmdline: String = "",
    val extraCmdline: String = "",
    val name: String = "",
    val recoveryDtboSize: Long = 0,
    val headerSize: Long = 0
)

data class LpPartitionInfo(
    val name: String,
    val groupName: String,
    val attributes: Long,
    val firstExtentIndex: Long,
    val numExtents: Long,
    val sizeBytes: Long
)

data class SuperMetadataInfo(
    val geometryMagic: Long,
    val headerMagic: Long,
    val majorVersion: Int,
    val minorVersion: Int,
    val metadataHeaderSize: Long,
    val partitions: List<LpPartitionInfo>,
    val blockDeviceSize: Long
)
