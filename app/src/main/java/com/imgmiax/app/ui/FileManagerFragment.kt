package com.imgmiax.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.imgmiax.app.R
import com.imgmiax.app.files.*
import com.imgmiax.app.utils.FileUtils
import com.imgmiax.app.utils.OperationLogger
import kotlinx.coroutines.launch

class FileManagerFragment : Fragment() {

    private lateinit var btnOpenFolder: Button
    private lateinit var tvCurrentPath: TextView
    private lateinit var rvFiles: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var safManager: SafFileManager
    private var currentTreeUri: Uri? = null

    private val openDocumentTreeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data?.data != null) {
            val treeUri = res.data!!.data!!
            currentTreeUri = treeUri
            loadFiles(treeUri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_file_manager, container, false)
        btnOpenFolder = v.findViewById(R.id.btnOpenFolder)
        tvCurrentPath = v.findViewById(R.id.tvCurrentPath)
        rvFiles = v.findViewById(R.id.rvFileList)
        progressBar = v.findViewById(R.id.progressBarFiles)

        safManager = SafFileManager(requireContext())
        rvFiles.layoutManager = LinearLayoutManager(context)

        btnOpenFolder.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            openDocumentTreeLauncher.launch(intent)
        }

        return v
    }

    private fun loadFiles(treeUri: Uri) {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            tvCurrentPath.text = "المجلد: ${treeUri.path ?: "/"}"
            try {
                val files = safManager.listFiles(treeUri)
                rvFiles.adapter = FileAdapter(files, onFileClick = { file ->
                    Toast.makeText(context, "تم اختيار: ${file.name}", Toast.LENGTH_SHORT).show()
                }, onDeleteClick = { file ->
                    confirmDelete(file)
                })
                OperationLogger.log("استعراض المجلد", "تم قراءة ${files.size} عنصر.", true)
            } catch (e: Exception) {
                Toast.makeText(context, "خطأ في قراءة المجلد: ${e.message}", Toast.LENGTH_SHORT).show()
                OperationLogger.log("مدير الملفات", "فشل قراءة المجلد: ${e.message}", false)
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun confirmDelete(file: FileItem) {
        val ctx = context ?: return
        MaterialAlertDialogBuilder(ctx)
            .setTitle("تأكيد الحذف")
            .setMessage("هل أنت متأكد من رغبتك في حذف ${file.name} نهائيًا؟")
            .setPositiveButton("حذف") { _, _ ->
                lifecycleScope.launch {
                    val ok = safManager.deleteFile(file.uri)
                    if (ok) {
                        Toast.makeText(ctx, "تم حذف ${file.name}", Toast.LENGTH_SHORT).show()
                        OperationLogger.log("حذف ملف", "تم حذف ${file.name}", true)
                        currentTreeUri?.let { loadFiles(it) }
                    } else {
                        Toast.makeText(ctx, "فشل في حذف الملف", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    class FileAdapter(
        private val files: List<FileItem>,
        private val onFileClick: (FileItem) -> Unit,
        private val onDeleteClick: (FileItem) -> Unit
    ) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tvItemName)
            val tvSize: TextView = v.findViewById(R.id.tvItemSize)
            val btnDelete: View = v.findViewById(R.id.btnItemDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = files[position]
            holder.tvName.text = if (item.isDirectory) "📁 ${item.name}" else "📄 ${item.name}"
            holder.tvSize.text = if (item.isDirectory) "مجلد" else FileUtils.formatFileSize(item.size)
            holder.itemView.setOnClickListener { onFileClick(item) }
            holder.btnDelete.setOnClickListener { onDeleteClick(item) }
        }

        override fun getItemCount(): Int = files.size
    }
}
