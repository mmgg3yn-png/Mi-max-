package com.imgmiax.app.archive

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking

class ZipSecurityTest {

    @Test(expected = SecurityException::class)
    fun testZipSlipPathTraversalBlocked() = runBlocking {
        // Create an in-memory ZIP with malicious path traversal "../../../evil.sh"
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            val evilEntry = ZipEntry("../../evil_file.txt")
            zos.putNextEntry(evilEntry)
            zos.write("Malicious payload".toByteArray())
            zos.closeEntry()
        }

        val testDir = File(System.getProperty("java.io.tmpdir"), "imgmiax_test_safe_dir")
        testDir.mkdirs()

        try {
            val bais = ByteArrayInputStream(baos.toByteArray())
            ZipArchiveManager.extractAll(bais, testDir, { _, _ -> })
        } finally {
            testDir.deleteRecursively()
        }
    }
}
