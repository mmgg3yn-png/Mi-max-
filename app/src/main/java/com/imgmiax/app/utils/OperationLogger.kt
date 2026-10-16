package com.imgmiax.app.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val id: Long = System.currentTimeMillis(),
    val timestamp: String,
    val title: String,
    val description: String,
    val isSuccess: Boolean
)

object OperationLogger {
    private val entries = mutableListOf<LogEntry>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss - yyyy/MM/dd", Locale.getDefault())

    fun log(title: String, description: String, isSuccess: Boolean = true) {
        val entry = LogEntry(
            timestamp = dateFormat.format(Date()),
            title = title,
            description = description,
            isSuccess = isSuccess
        )
        synchronized(entries) {
            entries.add(0, entry)
            if (entries.size > 200) {
                entries.removeAt(entries.lastIndex)
            }
        }
    }

    fun getAllLogs(): List<LogEntry> = synchronized(entries) {
        entries.toList()
    }

    fun clear() = synchronized(entries) {
        entries.clear()
    }
}
