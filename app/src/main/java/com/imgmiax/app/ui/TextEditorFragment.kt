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
import com.imgmiax.app.editor.TextFileHandler
import com.imgmiax.app.utils.OperationLogger
import kotlinx.coroutines.launch

class TextEditorFragment : Fragment() {

    private lateinit var btnOpen: Button
    private lateinit var btnSave: Button
    private lateinit var etContent: EditText
    private lateinit var tvStatus: TextView
    private var currentUri: Uri? = null

    private val openTextLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data?.data != null) {
            val uri = res.data!!.data!!
            currentUri = uri
            loadText(uri)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_text_editor, container, false)
        btnOpen = v.findViewById(R.id.btnOpenText)
        btnSave = v.findViewById(R.id.btnSaveText)
        etContent = v.findViewById(R.id.etTextContent)
        tvStatus = v.findViewById(R.id.tvEditorStatus)

        btnOpen.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            openTextLauncher.launch(intent)
        }

        btnSave.setOnClickListener {
            val uri = currentUri
            val ctx = context
            if (uri != null && ctx != null) {
                lifecycleScope.launch {
                    try {
                        val out = ctx.contentResolver.openOutputStream(uri)
                        if (out != null) {
                            TextFileHandler.writeText(out, etContent.text.toString())
                            Toast.makeText(ctx, "تم حفظ الملف بنجاح!", Toast.LENGTH_SHORT).show()
                            OperationLogger.log("حفظ ملف نصي", "تم حفظ التعديلات في ${uri.lastPathSegment}", true)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "فشل في حفظ الملف: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(ctx, "يرجى فتح ملف أولاً للحفظ", Toast.LENGTH_SHORT).show()
            }
        }

        return v
    }

    private fun loadText(uri: Uri) {
        val ctx = context ?: return
        lifecycleScope.launch {
            try {
                val pfd = ctx.contentResolver.openFileDescriptor(uri, "r")
                val sz = pfd?.statSize ?: 0L
                pfd?.close()

                val stream = ctx.contentResolver.openInputStream(uri)
                if (stream != null) {
                    val (text, isTruncated) = TextFileHandler.readText(stream, sz)
                    etContent.setText(text)
                    tvStatus.text = "الملف: ${uri.lastPathSegment} ${if (isTruncated) "(تم اقتطاع الملف لكبر حجمه)" else ""}"
                    OperationLogger.log("فتح ملف نصي", "تم فتح ${uri.lastPathSegment}", true)
                }
            } catch (e: Exception) {
                Toast.makeText(ctx, "خطأ في قراءة الملف: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
