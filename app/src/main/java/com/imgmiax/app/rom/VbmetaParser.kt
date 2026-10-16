package com.imgmiax.app.rom

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class VbmetaInfo(
    val magic: String,
    val requiredLibavbVersionMajor: Int,
    val requiredLibavbVersionMinor: Int,
    val authenticationDataBlockSize: Long,
    val auxiliaryDataBlockSize: Long,
    val algorithmType: Long,
    val rollbackIndex: Long,
    val flags: Long,
    val releaseString: String
)

object VbmetaParser {
    val AVB_MAGIC = "AVB0".toByteArray(Charsets.US_ASCII)

    fun parse(input: InputStream): VbmetaInfo? {
        val buffer = ByteArray(256)
        val read = input.read(buffer)
        if (read < 128) return null

        for (i in 0 until 4) {
            if (buffer[i] != AVB_MAGIC[i]) return null
        }

        val bb = ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN) // AVB uses big endian
        bb.position(4)
        val major = bb.int
        val minor = bb.int
        val authBlockSz = bb.long
        val auxBlockSz = bb.long
        val algorithm = bb.int.toLong() and 0xFFFFFFFFL
        bb.position(bb.position() + 16) // hash/sig offsets
        val rollback = bb.long
        val flags = bb.int.toLong() and 0xFFFFFFFFL
        bb.position(bb.position() + 4)
        val releaseBytes = ByteArray(48)
        bb.get(releaseBytes)
        val releaseStr = String(releaseBytes).trim { it <= ' ' || it == '\u0000' }

        return VbmetaInfo(
            magic = "AVB0",
            requiredLibavbVersionMajor = major,
            requiredLibavbVersionMinor = minor,
            authenticationDataBlockSize = authBlockSz,
            auxiliaryDataBlockSize = auxBlockSz,
            algorithmType = algorithm,
            rollbackIndex = rollback,
            flags = flags,
            releaseString = releaseStr
        )
    }
}
