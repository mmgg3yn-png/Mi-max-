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
import com.imgmiax.app.R
import com.imgmiax.app.apk.ApkAnalyzer
import com.imgmiax.app.utils.OperationLogger
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ApkToolsFragment : Fragment() {

    private lateinit var btnSelectApk: Button
    private lateinit var tvApkDetails: TextView
    private lateinit var progressBar: ProgressBar

    private val selectApkLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data?.data != null) {
            analyzeApkUri(res.data!!.data!!)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_apk_tools, container, false)
        btnSelectApk = v.findViewById(R.id.btnSelectApk)
        tvApkDetails = v.findViewById(R.id.tvApkDetails)
        progressBar = v.findViewById(R.id.progressBarApk)

        btnSelectApk.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            selectApkLauncher.launch(intent)
        }

        return v
    }

    private fun analyzeApkUri(uri: Uri) {
        val ctx = context ?: return
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            tvApkDetails.text = "جاري قراءة وتحليل حزمة التطبيق..."
            try {
                // Copy to temp cache for PackageManager inspection
                val tempApk = File(ctx.cacheDir, "temp_inspect.apk")
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempApk).use { output ->
                        input.copyTo(output)
                    }
                }

                val details = ApkAnalyzer.analyzeApk(ctx, tempApk)
                val sb = StringBuilder()
                sb.append("📱 اسم التطبيق: ").append(details.appName).append("\n")
                sb.append("📦 الحزمة (Package): ").append(details.packageName).append("\n")
                sb.append("🏷️ الإصدار (Version): ").append(details.versionName).append(" (كود: ").append(details.versionCode).append(")\n")
                sb.append("🎯 Target SDK: ").append(details.targetSdk).append(" | Min SDK: ").append(details.minSdk).append("\n")
                sb.append("⚙️ ملفات DEX: ").append(details.dexFilesCount).append(" ملف(ات)\n")
                sb.append("🧱 المعماريات المدعومة (Native): ").append(if (details.nativeArchitectures.isEmpty()) "لا توجد مكتبات Native" else details.nativeArchitectures.joinToString(", ")).append("\n")
                sb.append("🔐 توقيع التطبيق: ").append(if (details.signatureFound) "موقع (Signed)" else "غير موقع").append("\n\n")
                sb.append("🛡️ الأذونات المطلوبة (Permissions):\n")
                if (details.permissions.isEmpty()) {
                    sb.append("لا توجد أذونات مطلوبة.\n")
                } else {
                    details.permissions.forEach { p -> sb.append(" • ").append(p).append("\n") }
                }

                tvApkDetails.text = sb.toString()
                OperationLogger.log("تحليل APK", "الحزمة: ${details.packageName} (${details.versionName})", true)
            } catch (e: Exception) {
                tvApkDetails.text = "فشل في تحليل ملف APK: ${e.message}"
                OperationLogger.log("أدوات APK", "فشل التحليل: ${e.message}", false)
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
}
