package com.imgmiax.app.files

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SafFileManager(private val context: Context) {

    suspend fun listFiles(treeUri: Uri, sortOrder: FileSortOrder = FileSortOrder.NAME_ASC): List<FileItem> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val list = mutableListOf<FileItem>()

        root.listFiles().forEach { doc ->
            val name = doc.name ?: "Unnamed"
            val ext = name.substringAfterLast('.', "")
            list.add(
                FileItem(
                    name = name,
                    uri = doc.uri,
                    isDirectory = doc.isDirectory,
                    size = if (doc.isDirectory) 0L else doc.length(),
                    lastModified = doc.lastModified(),
                    mimeType = doc.type ?: "application/octet-stream",
                    extension = ext
                )
            )
        }

        when (sortOrder) {
            FileSortOrder.NAME_ASC -> list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            FileSortOrder.NAME_DESC -> list.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.name.lowercase() })
            FileSortOrder.SIZE_ASC -> list.sortedWith(compareBy({ !it.isDirectory }, { it.size }))
            FileSortOrder.SIZE_DESC -> list.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.size })
            FileSortOrder.DATE_ASC -> list.sortedWith(compareBy({ !it.isDirectory }, { it.lastModified }))
            FileSortOrder.DATE_DESC -> list.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.lastModified })
        }
    }

    suspend fun deleteFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val doc = DocumentFile.fromSingleUri(context, uri) ?: DocumentFile.fromTreeUri(context, uri)
        doc?.delete() ?: false
    }

    suspend fun createFolder(parentUri: Uri, folderName: String): Uri? = withContext(Dispatchers.IO) {
        val parent = DocumentFile.fromTreeUri(context, parentUri) ?: return@withContext null
        parent.createDirectory(folderName)?.uri
    }

    suspend fun createTextFile(parentUri: Uri, fileName: String, content: String): Uri? = withContext(Dispatchers.IO) {
        val parent = DocumentFile.fromTreeUri(context, parentUri) ?: return@withContext null
        val doc = parent.createFile("text/plain", fileName) ?: return@withContext null
        context.contentResolver.openOutputStream(doc.uri)?.use { os ->
            os.write(content.toByteArray(Charsets.UTF_8))
        }
        doc.uri
    }

    suspend fun rename(uri: Uri, newName: String): Boolean = withContext(Dispatchers.IO) {
        val doc = DocumentFile.fromSingleUri(context, uri) ?: DocumentFile.fromTreeUri(context, uri)
        doc?.renameTo(newName) ?: false
    }
}
