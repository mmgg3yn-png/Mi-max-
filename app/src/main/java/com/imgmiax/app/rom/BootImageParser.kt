package com.imgmiax.app.rom

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Android Boot Image Parser supporting v0, v1, v2, v3, v4 headers.
 * References: AOSP system/tools/mkbootimg/include/bootimg.h
 */
object BootImageParser {

    private val BOOT_MAGIC = "ANDROID!".toByteArray(Charsets.US_ASCII)

    fun parse(input: InputStream): BootImageHeaderInfo? {
        val buffer = ByteArray(4096)
        var totalRead = 0
        while (totalRead < buffer.size) {
            val r = input.read(buffer, totalRead, buffer.size - totalRead)
            if (r < 0) break
            totalRead += r
        }
        if (totalRead < 64) return null

        for (i in BOOT_MAGIC.indices) {
            if (buffer[i] != BOOT_MAGIC[i]) return null
        }

        val bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(8) // Skip magic

        val kernelSize = bb.int.toLong() and 0xFFFFFFFFL
        val kernelAddr = bb.int.toLong() and 0xFFFFFFFFL
        val ramdiskSize = bb.int.toLong() and 0xFFFFFFFFL
        val ramdiskAddr = bb.int.toLong() and 0xFFFFFFFFL
        val secondSize = bb.int.toLong() and 0xFFFFFFFFL
        val secondAddr = bb.int.toLong() and 0xFFFFFFFFL
        val tagsAddr = bb.int.toLong() and 0xFFFFFFFFL
        val pageSize = bb.int.toLong() and 0xFFFFFFFFL

        val headerVersion = bb.int

        if (headerVersion in 0..2) {
            val osVersion = bb.int
            val nameBytes = ByteArray(16)
            bb.get(nameBytes)
            val name = String(nameBytes).trim { it <= ' ' || it == '\u0000' }

            val cmdlineBytes = ByteArray(512)
            bb.get(cmdlineBytes)
            val cmdline = String(cmdlineBytes).trim { it <= ' ' || it == '\u0000' }

            bb.position(bb.position() + 32) // skip id

            val extraCmdlineBytes = ByteArray(1024)
            bb.get(extraCmdlineBytes)
            val extraCmdline = String(extraCmdlineBytes).trim { it <= ' ' || it == '\u0000' }

            var recoveryDtboSize = 0L
            var headerSize = 0L
            var dtbSize = 0L

            if (headerVersion >= 1 && totalRead >= 1648) {
                recoveryDtboSize = bb.int.toLong() and 0xFFFFFFFFL
                bb.position(bb.position() + 8) // recovery_dtbo_offset
                headerSize = bb.int.toLong() and 0xFFFFFFFFL
            }

            if (headerVersion >= 2 && totalRead >= 1664) {
                dtbSize = bb.int.toLong() and 0xFFFFFFFFL
            }

            val versionMajor = (osVersion shr 25) and 0x7F
            val versionMinor = (osVersion shr 18) and 0x7F
            val versionPatch = (osVersion shr 11) and 0x7F
            val formattedOs = if (osVersion != 0) "$versionMajor.$versionMinor.$versionPatch" else "Unknown"

            return BootImageHeaderInfo(
                headerVersion = headerVersion,
                kernelSize = kernelSize,
                ramdiskSize = ramdiskSize,
                secondSize = secondSize,
                dtbSize = dtbSize,
                pageSize = if (pageSize > 0) pageSize else 2048,
                osVersion = formattedOs,
                cmdline = cmdline,
                extraCmdline = extraCmdline,
                name = name,
                recoveryDtboSize = recoveryDtboSize,
                headerSize = headerSize
            )
        } else if (headerVersion in 3..4) {
            // In v3/v4 layout is shifted
            val osVersion = secondSize.toInt()
            val headerSize = secondAddr
            val cmdlineBytes = ByteArray(1536)
            if (totalRead >= 40 + 1536) {
                bb.position(40)
                bb.get(cmdlineBytes)
            }
            val cmdline = String(cmdlineBytes).trim { it <= ' ' || it == '\u0000' }

            return BootImageHeaderInfo(
                headerVersion = headerVersion,
                kernelSize = kernelSize,
                ramdiskSize = ramdiskSize,
                pageSize = 4096,
                osVersion = "v3/v4 Format",
                cmdline = cmdline,
                headerSize = headerSize
            )
        }

        return BootImageHeaderInfo(
            headerVersion = headerVersion,
            kernelSize = kernelSize,
            ramdiskSize = ramdiskSize,
            pageSize = pageSize
        )
    }
}
