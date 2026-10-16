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
import com.imgmiax.app.R
import com.imgmiax.app.archive.ArchiveItem
import com.imgmiax.app.archive.ZipArchiveManager
import com.imgmiax.app.utils.FileUtils
import com.imgmiax.app.utils.OperationLogger
import kotlinx.coroutines.launch

class ArchiveManagerFragment : Fragment() {

    private lateinit var btnSelectZip: Button
    private lateinit var tvZipName: TextView
    private lateinit var rvEntries: RecyclerView
    private lateinit var progressBar: ProgressBar

    private val selectZipLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data?.data != null) {
            val uri = res.data!!.data!!
            loadArchive(uri)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_archive_manager, container, false)
        btnSelectZip = v.findViewById(R.id.btnSelectZip)
        tvZipName = v.findViewById(R.id.tvZipName)
        rvEntries = v.findViewById(R.id.rvArchiveEntries)
        progressBar = v.findViewById(R.id.progressBarZip)

        rvEntries.layoutManager = LinearLayoutManager(context)

        btnSelectZip.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            selectZipLauncher.launch(intent)
        }

        return v
    }

    private fun loadArchive(uri: Uri) {
        val ctx = context ?: return
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            tvZipName.text = "الأرشيف: ${uri.lastPathSegment ?: "archive.zip"}"
            try {
                val stream = ctx.contentResolver.openInputStream(uri)
                if (stream != null) {
                    val entries = ZipArchiveManager.listEntries(stream)
                    rvEntries.adapter = ArchiveEntryAdapter(entries)
                    OperationLogger.log("قراءة أرشيف", "تم العثور على ${entries.size} ملف داخل الأرشيف.", true)
                }
            } catch (e: Exception) {
                Toast.makeText(ctx, "خطأ في قراءة الأرشيف: ${e.message}", Toast.LENGTH_SHORT).show()
                OperationLogger.log("مدير الأرشيفات", "فشل قراءة الأرشيف: ${e.message}", false)
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    class ArchiveEntryAdapter(private val items: List<ArchiveItem>) : RecyclerView.Adapter<ArchiveEntryAdapter.ViewHolder>() {
        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvPath: TextView = v.findViewById(R.id.tvEntryPath)
            val tvSize: TextView = v.findViewById(R.id.tvEntrySize)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_archive_entry, parent, false)
            return ViewHolder(v)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvPath.text = if (item.isDirectory) "📁 ${item.path}" else "📄 ${item.path}"
            holder.tvSize.text = if (item.isDirectory) "مجلد" else FileUtils.formatFileSize(item.uncompressedSize)
        }
        override fun getItemCount(): Int = items.size
    }
}
