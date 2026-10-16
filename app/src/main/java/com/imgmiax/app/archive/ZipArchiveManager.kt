package com.imgmiax.app.archive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object ZipArchiveManager {

    /**
     * Lists all entries inside a ZIP stream safely without extracting.
     */
    suspend fun listEntries(inputStream: InputStream): List<ArchiveItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<ArchiveItem>()
        val zis = ZipInputStream(inputStream)
        var entry: ZipEntry? = zis.nextEntry
        while (entry != null) {
            val name = entry.name.trimStart('/')
            val isDir = entry.isDirectory || entry.name.endsWith("/")
            items.add(
                ArchiveItem(
                    name = File(name).name.ifEmpty { name },
                    path = name,
                    isDirectory = isDir,
                    uncompressedSize = entry.size,
                    compressedSize = entry.compressedSize,
                    crc = entry.crc,
                    modifiedTime = entry.time
                )
            )
            zis.closeEntry()
            entry = zis.nextEntry
        }
        items
    }

    /**
     * Safely extracts all files from a ZIP stream to a target directory.
     * Enforces Path Traversal (Zip Slip) security protection.
     */
    suspend fun extractAll(
        inputStream: InputStream,
        targetDir: File,
        onProgress: (extractedFiles: Int, currentFileName: String) -> Unit,
        isCancelled: () -> Boolean = { false }
    ): Boolean = withContext(Dispatchers.IO) {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val canonicalDest = targetDir.canonicalPath
        val zis = ZipInputStream(inputStream)
        var count = 0
        val buffer = ByteArray(32 * 1024)

        var entry: ZipEntry? = zis.nextEntry
        while (entry != null) {
            if (isCancelled()) return@withContext false

            val outFile = File(targetDir, entry.name)
            val canonicalTarget = outFile.canonicalPath

            // Zip Slip protection: Ensure target file is inside canonical targetDir
            if (!canonicalTarget.startsWith(canonicalDest + File.separator) && canonicalTarget != canonicalDest) {
                throw SecurityException("محاولة استخراج غير آمنة خارج المجلد المحدد: ${entry.name}")
            }

            if (entry.isDirectory) {
                outFile.mkdirs()
            } else {
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { fos ->
                    var r: Int
                    while (zis.read(buffer).also { r = it } > 0) {
                        if (isCancelled()) return@withContext false
                        fos.write(buffer, 0, r)
                    }
                }
            }

            count++
            onProgress(count, entry.name)
            zis.closeEntry()
            entry = zis.nextEntry
        }

        true
    }
}
