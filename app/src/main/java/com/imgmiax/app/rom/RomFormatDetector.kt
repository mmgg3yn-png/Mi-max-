package com.imgmiax.app.rom

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object RomFormatDetector {

    fun detect(input: InputStream, fileSize: Long): RomDetectionResult {
        if (fileSize <= 0) {
            return RomDetectionResult(
                format = RomImageFormat.EMPTY_OR_CORRUPT,
                details = mapOf("الحجم" to "0 بايت"),
                headerDescription = "الملف فارغ أو غير موجود.",
                warningMessage = "الملف بحجم صفر بايت، تأكد من سلامة التنزيل أو الاختيار."
            )
        }

        val buffer = ByteArray(8192)
        var totalRead = 0
        while (totalRead < buffer.size) {
            val r = input.read(buffer, totalRead, buffer.size - totalRead)
            if (r < 0) break
            totalRead += r
        }

        if (totalRead < 16) {
            return RomDetectionResult(
                format = RomImageFormat.EMPTY_OR_CORRUPT,
                details = mapOf("الحجم" to "$fileSize بايت"),
                headerDescription = "الملف صغير جدًا وغير صالح للتحليل.",
                warningMessage = "حجم البيانات المقروءة أقل من الحد الأدنى للترويسات."
            )
        }

        // 1. Check Android Sparse Magic: 0xED26FF3A
        val bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
        val magic32 = bb.int.toLong() and 0xFFFFFFFFL
        if (magic32 == SparseImageParser.SPARSE_HEADER_MAGIC) {
            bb.position(0)
            val major = bb.short.toInt() and 0xFFFF
            val minor = bb.short.toInt() and 0xFFFF
            bb.position(12)
            val blockSize = bb.int.toLong() and 0xFFFFFFFFL
            val totalBlocks = bb.int.toLong() and 0xFFFFFFFFL
            val totalChunks = bb.int.toLong() and 0xFFFFFFFFL
            val uncompressed = totalBlocks * blockSize

            return RomDetectionResult(
                format = RomImageFormat.SPARSE,
                details = mapOf(
                    "الصيغة" to "Android Sparse Image",
                    "الإصدار" to "$major.$minor",
                    "حجم الكتلة (Block Size)" to "$blockSize بايت",
                    "إجمالي الكتل" to "$totalBlocks كتلة",
                    "إجمالي الـ Chunks" to "$totalChunks",
                    "الحجم بعد الفك (Raw)" to "${uncompressed / (1024 * 1024)} ميغابايت ($uncompressed بايت)"
                ),
                headerDescription = "صورة Android Sparse مضغوطة رسمية.",
                isSparse = true,
                canConvertToRaw = true,
                canExtract = false
            )
        }

        // 2. Check Android Boot Image Magic: "ANDROID!"
        var isBoot = true
        val bootMagic = "ANDROID!".toByteArray(Charsets.US_ASCII)
        for (i in 0 until 8) {
            if (buffer[i] != bootMagic[i]) {
                isBoot = false
                break
            }
        }
        if (isBoot) {
            bb.position(8)
            val kernelSz = bb.int.toLong() and 0xFFFFFFFFL
            bb.position(16)
            val ramdiskSz = bb.int.toLong() and 0xFFFFFFFFL
            bb.position(36)
            val hdrVer = bb.int

            return RomDetectionResult(
                format = RomImageFormat.BOOT_IMAGE,
                details = mapOf(
                    "الصيغة" to "Android Boot Image",
                    "إصدار الترويسة" to "v$hdrVer",
                    "حجم الكيرنل (Kernel)" to "${kernelSz / 1024} كيلوبايت ($kernelSz بايت)",
                    "حجم الـ Ramdisk" to "${ramdiskSz / 1024} كيلوبايت ($ramdiskSz بايت)"
                ),
                headerDescription = "صورة إقلاع Android Boot / Vendor Boot الرسمية.",
                canExtract = true
            )
        }

        // 3. Check VBMeta Magic: "AVB0"
        if (buffer[0] == 'A'.code.toByte() && buffer[1] == 'V'.code.toByte() &&
            buffer[2] == 'B'.code.toByte() && buffer[3] == '0'.code.toByte()
        ) {
            return RomDetectionResult(
                format = RomImageFormat.VBMETA,
                details = mapOf(
                    "الصيغة" to "Android Verified Boot (VBMeta)",
                    "المعيار" to "AVB 2.0"
                ),
                headerDescription = "ملف التحقق من إقلاع النظام VBMeta AVB2.0."
            )
        }

        // 4. Check LP Metadata (super.img) Magic: 0x616c4430
        bb.position(0)
        val geomMagic = bb.int.toLong() and 0xFFFFFFFFL
        if (geomMagic == SuperImageParser.LP_METADATA_GEOMETRY_MAGIC) {
            return RomDetectionResult(
                format = RomImageFormat.SUPER_LP,
                details = mapOf(
                    "الصيغة" to "Android Dynamic Partitions (super.img)",
                    "معمارية الأقسام" to "Logical Partitions Metadata"
                ),
                headerDescription = "صورة الأقسام الديناميكية المشتركة Super Partition.",
                canExtract = false
            )
        }

        // 5. Check Filesystem Superblocks (ext4, erofs, f2fs at offset 1024)
        if (totalRead >= 1080) {
            // ext4 magic at 1024 + 0x38 = 1080: 0xEF53 (0x53, 0xEF)
            val ext4Magic = ((buffer[1081].toInt() and 0xFF) shl 8) or (buffer[1080].toInt() and 0xFF)
            if (ext4Magic == 0xEF53) {
                return RomDetectionResult(
                    format = RomImageFormat.EXT4,
                    details = mapOf(
                        "الصيغة" to "Linux ext4 Filesystem",
                        "السوبر بلوك" to "0xEF53 تم التحقق بنجاح",
                        "الحجم الخام" to "${fileSize / (1024 * 1024)} ميغابايت"
                    ),
                    headerDescription = "نظام ملفات ext4 غير مضغوط (Raw ext4).",
                    canConvertToRaw = false
                )
            }

            // EROFS magic at 1024: 0xE0F5E1E2
            bb.position(1024)
            val erofsMagic = bb.int.toLong() and 0xFFFFFFFFL
            if (erofsMagic == 0xE0F5E1E2L) {
                return RomDetectionResult(
                    format = RomImageFormat.EROFS,
                    details = mapOf(
                        "الصيغة" to "EROFS (Enhanced Read-Only Filesystem)",
                        "الماجيك" to "0xE0F5E1E2",
                        "الاستخدام" to "أحدث أنظمة ملفات أندرويد الحديثة للقراءة فقط"
                    ),
                    headerDescription = "نظام ملفات EROFS فائق السرعة للقراءة فقط."
                )
            }

            // F2FS magic at 1024: 0xF2F52010
            bb.position(1024)
            val f2fsMagic = bb.int.toLong() and 0xFFFFFFFFL
            if (f2fsMagic == 0xF2F52010L) {
                return RomDetectionResult(
                    format = RomImageFormat.F2FS,
                    details = mapOf(
                        "الصيغة" to "F2FS (Flash-Friendly File System)",
                        "الماجيك" to "0xF2F52010"
                    ),
                    headerDescription = "نظام ملفات F2FS المخصص لذاكرة الفلاش."
                )
            }
        }

        // SquashFS check at offset 0
        if (totalRead >= 4) {
            val sqMagic = String(buffer, 0, 4)
            if (sqMagic == "hsqs" || sqMagic == "sqsh") {
                return RomDetectionResult(
                    format = RomImageFormat.SQUASHFS,
                    details = mapOf("الصيغة" to "SquashFS Compressed", "الماجيك" to sqMagic),
                    headerDescription = "نظام ملفات مضغوط بنظام SquashFS."
                )
            }
        }

        return RomDetectionResult(
            format = RomImageFormat.RAW_UNKNOWN,
            details = mapOf(
                "الحجم" to "${fileSize / (1024 * 1024)} ميغابايت ($fileSize بايت)",
                "حالة الترويسة" to "لم يتم التعرف على ماجيك ترويسة قياسية معروفة"
            ),
            headerDescription = "صورة قسم غير معروفة أو بيانات ثنائية مخصصة Raw Binary.",
            warningMessage = "الملف لا يحمل ترويسة Sparse أو Boot أو أنظمة الملفات الشائعة."
        )
    }
}
