package com.imgmiax.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.imgmiax.app.R
import com.imgmiax.app.hex.HexEngine
import com.imgmiax.app.utils.FileUtils
import kotlinx.coroutines.launch

class HexViewerFragment : Fragment() {

    private lateinit var btnSelectHexFile: Button
    private lateinit var btnPrevChunk: Button
    private lateinit var btnNextChunk: Button
    private lateinit var btnJumpOffset: Button
    private lateinit var etJumpOffset: EditText
    private lateinit var tvHexDump: TextView
    private lateinit var tvHexStatus: TextView

    private var currentUri: Uri? = null
    private var currentOffset: Long = 0L
    private var totalFileSize: Long = 0L
    private val chunkSize = 512

    private val selectHexFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data?.data != null) {
            val uri = res.data!!.data!!
            currentUri = uri
            currentOffset = 0L
            initFile(uri)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_hex_viewer, container, false)
        btnSelectHexFile = v.findViewById(R.id.btnSelectHexFile)
        btnPrevChunk = v.findViewById(R.id.btnHexPrev)
        btnNextChunk = v.findViewById(R.id.btnHexNext)
        btnJumpOffset = v.findViewById(R.id.btnHexJump)
        etJumpOffset = v.findViewById(R.id.etHexOffset)
        tvHexDump = v.findViewById(R.id.tvHexContent)
        tvHexStatus = v.findViewById(R.id.tvHexStatus)

        btnSelectHexFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            selectHexFileLauncher.launch(intent)
        }

        btnPrevChunk.setOnClickListener {
            if (currentOffset >= chunkSize) {
                currentOffset -= chunkSize
                loadChunk()
            }
        }

        btnNextChunk.setOnClickListener {
            if (currentOffset + chunkSize < totalFileSize) {
                currentOffset += chunkSize
                loadChunk()
            }
        }

        btnJumpOffset.setOnClickListener {
            val str = etJumpOffset.text.toString().trim()
            try {
                val offset = if (str.startsWith("0x", ignoreCase = true)) {
                    str.substring(2).toLong(16)
                } else {
                    str.toLong()
                }
                currentOffset = offset.coerceIn(0L, maxOf(0L, totalFileSize - 1))
                loadChunk()
            } catch (e: Exception) {
                Toast.makeText(context, "إزاحة غير صالحة", Toast.LENGTH_SHORT).show()
            }
        }

        return v
    }

    private fun initFile(uri: Uri) {
        val ctx = context ?: return
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "r")
        totalFileSize = pfd?.statSize ?: 0L
        pfd?.close()
        loadChunk()
    }

    private fun loadChunk() {
        val ctx = context ?: return
        val uri = currentUri ?: return
        lifecycleScope.launch {
            try {
                val chunk = HexEngine.readChunk(
                    inputSupplier = { ctx.contentResolver.openInputStream(uri)!! },
                    offset = currentOffset,
                    chunkSize = chunkSize,
                    totalSize = totalFileSize
                )

                val sb = StringBuilder()
                sb.append("الإزاحة (Offset)  00 01 02 03 04 05 06 07  08 09 0A 0B 0C 0D 0E 0F  | ASCII\n")
                sb.append("---------------------------------------------------------------------------\n")

                chunk.lines.forEach { line ->
                    sb.append(String.format("%08X: ", line.offset))
                    sb.append(line.hexString).append(" | ").append(line.asciiString).append("\n")
                }

                tvHexDump.text = sb.toString()
                tvHexStatus.text = "الإزاحة الحالية: 0x${java.lang.Long.toHexString(currentOffset).uppercase()} (${FileUtils.formatFileSize(currentOffset)} من ${FileUtils.formatFileSize(totalFileSize)})"
            } catch (e: Exception) {
                tvHexDump.text = "خطأ في قراءة البيانات الست عشرية: ${e.message}"
            }
        }
    }
}
