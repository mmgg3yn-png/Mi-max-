package com.imgmiax.app.editor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

object TextFileHandler {

    const val MAX_EDIT_SIZE = 5 * 1024 * 1024L // 5MB limit for smooth in-memory text editor

    suspend fun readText(input: InputStream, length: Long): Pair<String, Boolean> = withContext(Dispatchers.IO) {
        val isTruncated = length > MAX_EDIT_SIZE
        val toRead = if (isTruncated) MAX_EDIT_SIZE.toInt() else length.toInt().coerceAtLeast(0)

        val buffer = ByteArray(toRead)
        var total = 0
        while (total < toRead) {
            val r = input.read(buffer, total, toRead - total)
            if (r < 0) break
            total += r
        }
        val text = String(buffer, 0, total, Charsets.UTF_8)
        Pair(text, isTruncated)
    }

    suspend fun writeText(output: OutputStream, text: String): Boolean = withContext(Dispatchers.IO) {
        output.use {
            it.write(text.toByteArray(Charsets.UTF_8))
            it.flush()
        }
        true
    }
}
