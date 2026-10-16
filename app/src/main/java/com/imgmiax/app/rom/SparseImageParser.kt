package com.imgmiax.app.rom

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Android Sparse Image Parser and simg2img Unpacker.
 * Conforms to AOSP system/core/libsparse specifications.
 */
object SparseImageParser {

    const val SPARSE_HEADER_MAGIC = 0xED26FF3AL

    const val CHUNK_TYPE_RAW = 0xCAC1
    const val CHUNK_TYPE_FILL = 0xCAC2
    const val CHUNK_TYPE_DONT_CARE = 0xCAC3
    const val CHUNK_TYPE_CRC32 = 0xCAC4

    /**
     * Reads and parses Sparse header from the input stream.
     */
    fun parseHeader(input: InputStream): SparseHeader? {
        val buffer = ByteArray(28)
        val read = input.read(buffer)
        if (read < 28) return null

        val bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
        val magic = bb.int.toLong() and 0xFFFFFFFFL
        if (magic != SPARSE_HEADER_MAGIC) {
            return null
        }

        val major = bb.short.toInt() and 0xFFFF
        val minor = bb.short.toInt() and 0xFFFF
        val fileHdrSz = bb.short.toInt() and 0xFFFF
        val chunkHdrSz = bb.short.toInt() and 0xFFFF
        val blkSz = bb.int.toLong() and 0xFFFFFFFFL
        val totalBlks = bb.int.toLong() and 0xFFFFFFFFL
        val totalChunks = bb.int.toLong() and 0xFFFFFFFFL
        val checksum = bb.int.toLong() and 0xFFFFFFFFL

        return SparseHeader(
            magic = magic,
            majorVersion = major,
            minorVersion = minor,
            fileHeaderSize = fileHdrSz,
            chunkHeaderSize = chunkHdrSz,
            blockSize = blkSz,
            totalBlocks = totalBlks,
            totalChunks = totalChunks,
            imageChecksum = checksum
        )
    }

    /**
     * Unpacks Android Sparse stream to Raw stream (simg2img).
     * Uses streaming I/O with cancellation support to prevent OOM.
     */
    suspend fun unpackSparseToRaw(
        input: InputStream,
        output: OutputStream,
        onProgress: (percent: Int, currentBlocks: Long, totalBlocks: Long) -> Unit,
        isCancelled: () -> Boolean = { false }
    ): Boolean = withContext(Dispatchers.IO) {
        val header = parseHeader(input) ?: return@withContext false

        // Skip any remaining bytes in file header if fileHeaderSize > 28
        if (header.fileHeaderSize > 28) {
            val toSkip = (header.fileHeaderSize - 28).toLong()
            var skipped = 0L
            while (skipped < toSkip) {
                val s = input.skip(toSkip - skipped)
                if (s <= 0) break
                skipped += s
            }
        }

        val chunkHeaderBytes = ByteArray(12)
        val buffer = ByteArray(64 * 1024) // 64KB transfer buffer
        var processedBlocks = 0L

        for (i in 0 until header.totalChunks) {
            if (isCancelled()) return@withContext false

            var readHdr = 0
            while (readHdr < 12) {
                val r = input.read(chunkHeaderBytes, readHdr, 12 - readHdr)
                if (r < 0) break
                readHdr += r
            }
            if (readHdr < 12) break

            val bb = ByteBuffer.wrap(chunkHeaderBytes).order(ByteOrder.LITTLE_ENDIAN)
            val chunkType = bb.short.toInt() and 0xFFFF
            val reserved = bb.short.toInt() and 0xFFFF
            val chunkSizeBlocks = bb.int.toLong() and 0xFFFFFFFFL
            val totalSz = bb.int.toLong() and 0xFFFFFFFFL

            // Extra bytes in chunk header if chunkHeaderSize > 12
            if (header.chunkHeaderSize > 12) {
                val extraToSkip = (header.chunkHeaderSize - 12).toLong()
                var sk = 0L
                while (sk < extraToSkip) {
                    val s = input.skip(extraToSkip - sk)
                    if (s <= 0) break
                    sk += s
                }
            }

            val dataBytesToRead = chunkSizeBlocks * header.blockSize

            when (chunkType) {
                CHUNK_TYPE_RAW -> {
                    var remaining = dataBytesToRead
                    while (remaining > 0) {
                        if (isCancelled()) return@withContext false
                        val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                        val r = input.read(buffer, 0, toRead)
                        if (r < 0) throw IllegalStateException("Unexpected EOF in RAW chunk")
                        output.write(buffer, 0, r)
                        remaining -= r
                    }
                }
                CHUNK_TYPE_FILL -> {
                    val fillPattern = ByteArray(4)
                    var pRead = 0
                    while (pRead < 4) {
                        val r = input.read(fillPattern, pRead, 4 - pRead)
                        if (r < 0) break
                        pRead += r
                    }
                    val fillBuffer = ByteArray(4096)
                    for (idx in fillBuffer.indices) {
                        fillBuffer[idx] = fillPattern[idx % 4]
                    }
                    var remaining = dataBytesToRead
                    while (remaining > 0) {
                        if (isCancelled()) return@withContext false
                        val toWrite = minOf(remaining, fillBuffer.size.toLong()).toInt()
                        output.write(fillBuffer, 0, toWrite)
                        remaining -= toWrite
                    }
                }
                CHUNK_TYPE_DONT_CARE -> {
                    // Fill with zeros for raw flat output
                    val zeroBuffer = ByteArray(4096)
                    var remaining = dataBytesToRead
                    while (remaining > 0) {
                        if (isCancelled()) return@withContext false
                        val toWrite = minOf(remaining, zeroBuffer.size.toLong()).toInt()
                        output.write(zeroBuffer, 0, toWrite)
                        remaining -= toWrite
                    }
                }
                CHUNK_TYPE_CRC32 -> {
                    // Skip 4 bytes CRC
                    input.skip(4)
                }
                else -> {
                    // Unknown chunk type: skip totalSz - chunkHeaderSize
                    val toSkip = totalSz - header.chunkHeaderSize
                    if (toSkip > 0) {
                        var sk = 0L
                        while (sk < toSkip) {
                            val s = input.skip(toSkip - sk)
                            if (s <= 0) break
                            sk += s
                        }
                    }
                }
            }

            processedBlocks += chunkSizeBlocks
            val percent = if (header.totalBlocks > 0) {
                ((processedBlocks * 100) / header.totalBlocks).toInt().coerceIn(0, 100)
            } else 0
            onProgress(percent, processedBlocks, header.totalBlocks)
        }

        output.flush()
        return@withContext true
    }
}
