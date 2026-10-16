package com.imgmiax.app.rom

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Android Logical Partitions (LP) Metadata Parser (super.img).
 * Follows AOSP system/core/fs_mgr/liblp/include/liblp/metadata_format.h
 */
object SuperImageParser {

    const val LP_METADATA_GEOMETRY_MAGIC = 0x616c4430L // "0Dal"
    const val LP_METADATA_HEADER_MAGIC = 0x414C5030L   // "0PLA"

    fun parse(input: InputStream): SuperMetadataInfo? {
        val buffer = ByteArray(8192)
        var total = 0
        while (total < buffer.size) {
            val r = input.read(buffer, total, buffer.size - total)
            if (r < 0) break
            total += r
        }
        if (total < 4096) return null

        val bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
        val geomMagic = bb.int.toLong() and 0xFFFFFFFFL

        if (geomMagic != LP_METADATA_GEOMETRY_MAGIC) {
            return null
        }

        val geomStructSize = bb.int
        bb.position(bb.position() + 32) // sha256
        val metadataMaxSize = bb.int.toLong() and 0xFFFFFFFFL
        val metadataSlotCount = bb.int
        val logicalBlockSize = bb.int

        // Check header at 4096 offset
        bb.position(4096)
        val hdrMagic = bb.int.toLong() and 0xFFFFFFFFL
        if (hdrMagic != LP_METADATA_HEADER_MAGIC) {
            return SuperMetadataInfo(
                geometryMagic = geomMagic,
                headerMagic = hdrMagic,
                majorVersion = 1,
                minorVersion = 0,
                metadataHeaderSize = 0,
                partitions = emptyList(),
                blockDeviceSize = 0
            )
        }

        val major = bb.short.toInt() and 0xFFFF
        val minor = bb.short.toInt() and 0xFFFF
        val hdrSize = bb.int.toLong() and 0xFFFFFFFFL
        bb.position(bb.position() + 32) // header sha256
        val tablesSize = bb.int.toLong() and 0xFFFFFFFFL
        bb.position(bb.position() + 32) // tables sha256

        // Partitions descriptor
        val partitionsOffset = bb.int
        val numPartitions = bb.int
        val partitionEntrySize = bb.int

        // Extents descriptor
        val extentsOffset = bb.int
        val numExtents = bb.int

        // Groups descriptor
        val groupsOffset = bb.int
        val numGroups = bb.int

        // Devices descriptor
        val devicesOffset = bb.int
        val numDevices = bb.int

        val partitionsList = mutableListOf<LpPartitionInfo>()

        // Read partition entries if available in buffer
        val baseTablesOffset = 4096 + hdrSize.toInt()
        val pTableStart = baseTablesOffset + partitionsOffset

        if (pTableStart + (numPartitions * partitionEntrySize) <= total) {
            for (idx in 0 until minOf(numPartitions, 64)) {
                val entryPos = pTableStart + (idx * partitionEntrySize)
                bb.position(entryPos)
                val nameBytes = ByteArray(36)
                bb.get(nameBytes)
                val pName = String(nameBytes).trim { it <= ' ' || it == '\u0000' }
                val attributes = bb.int.toLong() and 0xFFFFFFFFL
                val firstExtentIndex = bb.int.toLong() and 0xFFFFFFFFL
                val extCount = bb.int.toLong() and 0xFFFFFFFFL
                val groupIndex = bb.int

                if (pName.isNotEmpty()) {
                    partitionsList.add(
                        LpPartitionInfo(
                            name = pName,
                            groupName = "group_$groupIndex",
                            attributes = attributes,
                            firstExtentIndex = firstExtentIndex,
                            numExtents = extCount,
                            sizeBytes = extCount * 512 * 8 // Approximate sector blocks
                        )
                    )
                }
            }
        }

        return SuperMetadataInfo(
            geometryMagic = geomMagic,
            headerMagic = hdrMagic,
            majorVersion = major,
            minorVersion = minor,
            metadataHeaderSize = hdrSize,
            partitions = partitionsList,
            blockDeviceSize = metadataMaxSize
        )
    }
}
