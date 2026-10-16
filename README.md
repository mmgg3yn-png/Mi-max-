# IMGmiax - مدير ملفات وأدوات تحليل واستخراج ملفات Android ROM

مشروع Android Native احترافي متكامل مكتوب بلغة **Kotlin** وبنظام بناء **Android Gradle**، مستوحى من تنظيم وتجربة أدوات MT Manager بهوية عربية كاملة وتصميم RTL عصري مبني بـ Material 3.

---

## 📌 معلومات المشروع والمطور
- **اسم التطبيق:** IMGmiax
- **المطور:** IMGmiax Team
- **البريد الإلكتروني:** [mmgg3yn@gmail.com](mailto:mmgg3yn@gmail.com)
- **قناة Telegram:** [@Mi_Max12](https://t.me/Mi_Max12)

---

## 🚀 المواصفات التقنية وبيئة البناء
| العنصر | الإصدار / القيمة |
| :--- | :--- |
| **لغة البرمجة** | Kotlin 1.9.23 |
| **Android Gradle Plugin (AGP)** | 8.3.2 |
| **Gradle Wrapper** | 8.7 (مضمّن وجاهز) |
| **إصدار Java المستهدف** | JDK 17 |
| **الحد الأدنى للنظام (minSdkVersion)** | 26 (Android 8.0 Oreo) |
| **الإصدار المستهدف (targetSdkVersion)** | 34 (Android 14) |
| **إصدار التجميع (compileSdkVersion)** | 34 |
| **المعماريات المدعومة** | ARM64 (arm64-v8a), ARM32 (armeabi-v7a), x86, x86_64 |
| **نظام أذونات التخزين** | Android SAF (Storage Access Framework) الآمن 100% |
| **صلاحية Root** | **غير مطلوبة إطلاقًا** (لا يحتاج روت) |

---

## 🛠️ الميزات والوحدات المنفذة فعليًا

### 1. أدوات ملفات الرومات (ROM Tools)
- **Android Sparse Image:**
  - قراءة ترويسة Sparse وفحص الماجيك `0xED26FF3A` والتحقق من الإصدار وكتل البيانات.
  - فك ضغط Sparse إلى صورة خام Raw Image (`simg2img`) عبر تدفقات I/O غير محملة للذاكرة مع دعم إيقاف/إلغاء العملية وشريط تقدم لحظي.
- **Android Boot Images (`boot.img`, `vendor_boot.img`, `init_boot.img`):**
  - فحص الماجيك `ANDROID!` وقراءة ترويسات الإصدارات (v0, v1, v2, v3, v4).
  - استخراج أحجام الكيرنل (Kernel)، الرام ديسك (Ramdisk)، شجرة العتاد (DTB)، وسطر الأوامر (cmdline).
- **Dynamic Partitions (`super.img`):**
  - فحص ترويسة LP Metadata (`LpMetadataGeometry` و `LpMetadataHeader`) بماجيك `0x616c4430` و `0x414C5030`.
  - قراءة جدول الأقسام المنطقية وأحجامها ومجموعات الأقسام.
- **VBMeta (Android Verified Boot AVB 2.0):**
  - فحص ماجيك `AVB0` وقراءة كتل التحقق وفهرس التراجع (Rollback Index).
- **كشف أنظمة الملفات للأقسام:**
  - التعرف الدقيق على أنظمة ملفات الرومات عبر السوبر بلوك:
    - **ext4:** ماجيك `0xEF53` عند الإزاحة 1080.
    - **EROFS:** ماجيك `0xE0F5E1E2` عند الإزاحة 1024.
    - **F2FS:** ماجيك `0xF2F52010` عند الإزاحة 1024.
    - **SquashFS:** ماجيك `hsqs` / `sqsh`.

### 2. مدير الملفات بنظام SAF (Storage Access Framework)
- تصفح المجلدات والملفات بالآلية الرسمية لـ Android بدون صلاحيات حساسة أو روت.
- عرض تفاصيل الملفات (الاسم، الحجم المنسق، النوع، تاريخ التعديل).
- ترتيب الملفات حسب الاسم، الحجم، والتاريخ تصاعديًا وتنازليًا.
- حذف آمن للملفات والمجلدات بعد تأكيد صريح من المستخدم.

### 3. مدير الأرشيفات (Archive Manager)
- استعراض كامل لمحتويات ملفات ZIP وحجم كل ملف داخله قبل الاستخراج.
- استخراج آمن مزود بحماية **Zip Slip** لمنع هجمات Path Traversal.

### 4. أدوات تحليل حزم APK
- قراءة اسم التطبيق الظاهر واسم الحزمة (Package Name).
- قراءة versionName و versionCode و minSdk و targetSdk.
- فحص ملفات DEX الداخلية وعددها.
- كشف معماريات المكتبات الثنائية (Native Libraries ABIs).
- التحقق من حالة توقيع التطبيق v1 Signature.

### 5. محرر النصوص عالي الكفاءة
- فتح وتعديل وحفظ ملفات النصوص بترميز UTF-8 سليم.
- اقتطاع آمن للملفات الضخمة لتفادي انهيار الذاكرة.

### 6. عارض الست عشري (Hex Viewer)
- قراءة مجزأة بنظام الشرائح (Paged Reading) للملفات الضخمة التي تصل لعدة جيجابايت دون تحميلها بالكامل في الرام.
- عرض قيم الـ Hex مع التمثيل النصي ASCII والإزاحة (Offsets).
- القفز السريع لأي إزاحة بالنظام الست عشري (0x...) أو العشري.

### 7. سجل العمليات والإعدادات
- تسجيل لحظي لكل العمليات (نجاح/فشل) مع التوقيت الدقيق.
- دعم الوضع الليلي (Dark Mode) والوضع الفاتح (Light Mode) وتلقائي النظام.

---

## 📱 كيفية بناء ملف APK من الهاتف أو السحابة

### الطريقة الأولى: عبر أي موقع أو خدمة بناء Android في السحابة
1. ارفع ملف `IMGmiax-Android-Project.zip` مباشرة إلى منصة بناء (مثل GitHub Actions, Bitrise, CodeMagic, إلخ).
2. بالنسبة لـ GitHub Actions، استخدم خطة عمل بسيطة:
```yaml
name: Build IMGmiax APK
on: [push, workflow_dispatch]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - name: Build APK
        run: ./gradlew assembleDebug --stacktrace
      - uses: actions/upload-artifact@v4
        with:
          name: IMGmiax-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
```

### الطريقة الثانية: من الهاتف مباشرة عبر Termux
1. ثبّت حزم البناء في Termux:
```bash
pkg update && pkg install openjdk-17
```
2. فك ضغط ملف المشروع:
```bash
unzip IMGmiax-Android-Project.zip -d IMGmiax
cd IMGmiax
chmod +x gradlew
```
3. ابدأ البناء:
```bash
./gradlew assembleDebug
```
ستجد ملف الـ APK الناتج داخل:
`app/build/outputs/apk/debug/app-debug.apk`

---
© 2026 IMGmiax. جميع الحقوق محفوظة للمطور.
