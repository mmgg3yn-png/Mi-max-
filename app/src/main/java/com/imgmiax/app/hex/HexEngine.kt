package com.imgmiax.app.hex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

data class HexLine(
    val offset: Long,
    val hexString: String,
    val asciiString: String
)

data class HexChunkResult(
    val startOffset: Long,
    val totalFileSize: Long,
    val lines: List<HexLine>,
    val bytesRead: Int
)

object HexEngine {

    /**
     * Reads a slice of the file at the given offset and formats as Hex + ASCII dump.
     * Avoids loading large files in memory.
     */
    suspend fun readChunk(
        inputSupplier: () -> InputStream,
        offset: Long,
        chunkSize: Int = 512,
        totalSize: Long
    ): HexChunkResult = withContext(Dispatchers.IO) {
        inputSupplier().use { stream ->
            var skipped = 0L
            while (skipped < offset) {
                val s = stream.skip(offset - skipped)
                if (s <= 0) break
                skipped += s
            }

            val buffer = ByteArray(chunkSize)
            var read = 0
            while (read < chunkSize) {
                val r = stream.read(buffer, read, chunkSize - read)
                if (r < 0) break
                read += r
            }

            val lines = mutableListOf<HexLine>()
            val bytesPerLine = 16

            for (i in 0 until read step bytesPerLine) {
                val lineOffset = offset + i
                val lineLen = minOf(bytesPerLine, read - i)

                val hexSb = StringBuilder()
                val asciiSb = StringBuilder()

                for (j in 0 until bytesPerLine) {
                    if (j < lineLen) {
                        val b = buffer[i + j].toInt() and 0xFF
                        hexSb.append(String.format("%02X ", b))
                        if (j == 7) hexSb.append(" ")
                        val char = b.toChar()
                        if (b in 32..126) {
                            asciiSb.append(char)
                        } else {
                            asciiSb.append('.')
                        }
                    } else {
                        hexSb.append("   ")
                        if (j == 7) hexSb.append(" ")
                    }
                }

                lines.add(
                    HexLine(
                        offset = lineOffset,
                        hexString = hexSb.toString(),
                        asciiString = asciiSb.toString()
                    )
                )
            }

            HexChunkResult(
                startOffset = offset,
                totalFileSize = totalSize,
                lines = lines,
                bytesRead = read
            )
        }
    }
}
