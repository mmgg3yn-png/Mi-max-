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
import com.google.android.material.card.MaterialCardView
import com.imgmiax.app.R
import com.imgmiax.app.rom.*
import com.imgmiax.app.utils.FileUtils
import com.imgmiax.app.utils.OperationLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RomToolsFragment : Fragment() {

    private lateinit var tvFileName: TextView
    private lateinit var tvDetectionHeader: TextView
    private lateinit var tvDetectionDetails: TextView
    private lateinit var btnSelectImage: Button
    private lateinit var btnConvertRaw: Button
    private lateinit var btnCancel: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var cardResult: MaterialCardView

    private var selectedUri: Uri? = null
    private var conversionJob: Job? = null
    private var detectedResult: RomDetectionResult? = null

    private val selectFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data?.data != null) {
            val uri = res.data!!.data!!
            selectedUri = uri
            analyzeImage(uri)
        }
    }

    private val saveRawFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null && selectedUri != null) {
            startSparseConversion(selectedUri!!, uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_rom_tools, container, false)
        tvFileName = v.findViewById(R.id.tvRomFileName)
        tvDetectionHeader = v.findViewById(R.id.tvDetectionHeader)
        tvDetectionDetails = v.findViewById(R.id.tvDetectionDetails)
        btnSelectImage = v.findViewById(R.id.btnSelectRomFile)
        btnConvertRaw = v.findViewById(R.id.btnConvertSparseToRaw)
        btnCancel = v.findViewById(R.id.btnCancelOperation)
        progressBar = v.findViewById(R.id.progressBarRom)
        tvProgress = v.findViewById(R.id.tvRomProgress)
        cardResult = v.findViewById(R.id.cardRomResult)

        btnSelectImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            selectFileLauncher.launch(intent)
        }

        btnConvertRaw.setOnClickListener {
            if (detectedResult?.isSparse == true) {
                saveRawFileLauncher.launch("unpacked_raw.img")
            } else {
                Toast.makeText(context, "الملف الحالي ليس بتنسيق Sparse صالح للتحويل", Toast.LENGTH_SHORT).show()
            }
        }

        btnCancel.setOnClickListener {
            conversionJob?.cancel()
            btnCancel.visibility = View.GONE
            progressBar.visibility = View.GONE
            tvProgress.text = "تم إلغاء العملية."
            OperationLogger.log("تحويل Sparse", "تم إلغاء العملية بواسطة المستخدم", false)
        }

        return v
    }

    private fun analyzeImage(uri: Uri) {
        val ctx = context ?: return
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            tvProgress.text = "جاري فحص وتحديد صيغة الملف..."
            val pfd = ctx.contentResolver.openFileDescriptor(uri, "r")
            val size = pfd?.statSize ?: 0L
            pfd?.close()

            val stream = ctx.contentResolver.openInputStream(uri)
            if (stream == null) {
                progressBar.visibility = View.GONE
                tvProgress.text = "تعذر فتح تدفق قراءة الملف."
                return@launch
            }

            val result = stream.use { RomFormatDetector.detect(it, size) }
            detectedResult = result

            cardResult.visibility = View.VISIBLE
            tvFileName.text = "الملف المختار: ${uri.lastPathSegment ?: "image.img"} (${FileUtils.formatFileSize(size)})"
            tvDetectionHeader.text = result.headerDescription

            val sb = java.lang.StringBuilder()
            result.details.forEach { (k, v) ->
                sb.append("• ").append(k).append(": ").append(v).append("\n")
            }
            if (result.warningMessage != null) {
                sb.append("\n⚠️ تنبيه: ").append(result.warningMessage)
            }
            tvDetectionDetails.text = sb.toString()

            btnConvertRaw.visibility = if (result.canConvertToRaw) View.VISIBLE else View.GONE
            progressBar.visibility = View.GONE
            tvProgress.text = "تم التحليل بنجاح."

            OperationLogger.log("تحليل صورة ROM", "الصيغة: ${result.format.displayName}", true)
        }
    }

    private fun startSparseConversion(sourceUri: Uri, destUri: Uri) {
        val ctx = context ?: return
        btnCancel.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        btnConvertRaw.isEnabled = false

        conversionJob = lifecycleScope.launch {
            try {
                val input = ctx.contentResolver.openInputStream(sourceUri)
                val output = ctx.contentResolver.openOutputStream(destUri)

                if (input == null || output == null) {
                    Toast.makeText(ctx, "فشل في فتح تدفق القراءة أو الكتابة", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val success = SparseImageParser.unpackSparseToRaw(
                    input = input,
                    output = output,
                    onProgress = { percent, currentBlocks, totalBlocks ->
                        progressBar.progress = percent
                        tvProgress.text = "جاري التحويل: $percent% ($currentBlocks من $totalBlocks كتلة)"
                    },
                    isCancelled = { conversionJob?.isCancelled == true }
                )

                if (success) {
                    tvProgress.text = "اكتمل التحويل بنجاح! تم حفظ ملف Raw Image."
                    Toast.makeText(ctx, "اكتمل تحويل Sparse بنجاح!", Toast.LENGTH_LONG).show()
                    OperationLogger.log("تحويل Sparse إلى Raw", "تم التحويل بنجاح وحفظ الصورة الخام.", true)
                } else {
                    tvProgress.text = "فشلت عملية التحويل أو تم إلغاؤها."
                }
            } catch (e: Exception) {
                tvProgress.text = "خطأ أثناء المعالجة: ${e.localizedMessage}"
                OperationLogger.log("تحويل Sparse", "خطأ: ${e.message}", false)
            } finally {
                btnCancel.visibility = View.GONE
                progressBar.visibility = View.GONE
                btnConvertRaw.isEnabled = true
            }
        }
    }
}
