package com.mimax.ai;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.ExifInterface;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    static final int REQ_IMG = 1, REQ_AUD = 2, REQ_STO = 3;
    static boolean toSettings = false;
    static final String DEFAULT_PROMPT =
            "أنت Mi_Max Ai، مساعد ذكي ودود وعملي. أجب بلغة المستخدم (والعربية افتراضياً) بوضوح وإيجاز، "
                    + "وقدّم خطوات عملية عند الحاجة. عند كتابة كود ضعه داخل كتل كود. "
                    + "إن لم تكن متأكداً من معلومة فقل ذلك بصراحة.";

    interface Txt { void on(String s); }

    // ---- الألوان ----
    boolean dark;
    int BG, SF, SF2, LN, TX, MUT, AC, ER;
    final Handler ui = new Handler(Looper.getMainLooper());

    // ---- هيكل الشاشة ----
    FrameLayout content;
    TextView title;
    Button backBtn, newBtn;
    Button[] navBtns = new Button[4];
    String tab = "chat";
    String sub = null;

    // ---- البيانات ----
    ArrayList<Store.Chat> chats = new ArrayList<>();
    Store.Chat cur;
    ArrayList<String> mem = new ArrayList<>();
    String prompt = "";
    boolean busy;
    boolean[] cancel = new boolean[]{false};

    // ---- المرفقات والتسجيل ----
    String attImg, attAud;
    Bitmap attThumb;
    int attDur;
    Rec rec;
    Runnable recTick;

    // ---- عناصر المحادثة ----
    LinearLayout msgBox;
    ScrollView msgScroll;
    EditText input;
    Button sendBtn, micBtn;
    TextView attInfo, lastTv;
    String draft = "";

    TextToSpeech tts;
    String pendingSpeak;
    EditText fModel, fAudio;

    // =====================================================================
    @Override
    protected void onCreate(Bundle b) {
        Store.init(this);
        dark = Store.flag("dark", true);
        setTheme(dark ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        super.onCreate(b);
        colors();
        reload();
        buildRoot();
        if (toSettings) { toSettings = false; showSettings(); } else showChat();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) tts.shutdown();
        if (rec != null) { try { rec.stop(); } catch (Exception e) { /* تجاهل */ } }
    }

    void colors() {
        int theme = 0;
        try { theme = Integer.parseInt(Store.get("theme", "0")); } catch (Exception ignored) { }
        int[][] darkPal = {
                {0xFF101820,0xC8273544,0xD9364A5E,0x66D9F0FF,0xFFB9E8FF},
                {0xFF101B2B,0xC8243D5B,0xD9325275,0x667CCBFF,0xFF80C8FF},
                {0xFF1A1230,0xC8322450,0xD9433268,0x669F8BFF,0xFFC1AEFF},
                {0xFF2A1422,0xC842263A,0xD9573450,0x66FF9FC8,0xFFFFA8D0},
                {0xFF10251F,0xC8234438,0xD9325A4B,0x667DE0B6,0xFF8DE8C2}
        };
        int[][] lightPal = {
                {0xFFEAF2F8,0xDDFFFFFF,0xEAFBFFFF,0x6684AFC8,0xFF267FA5},
                {0xFFEAF3FF,0xDDFFFFFF,0xEAF5FFFF,0x6686B8E8,0xFF2879C7},
                {0xFFF1ECFF,0xDDF7F2FF,0xEAF7F2FF,0x669B88D8,0xFF7656C8},
                {0xFFFFEDF4,0xDDFFFFFF,0xEAFEF4F8,0x66D99AB8,0xFFC34D83},
                {0xFFE9F6EF,0xDDFFFFFF,0xEAF3FFF7,0x6687C4A3,0xFF27865C}
        };
        int[] c = (dark ? darkPal : lightPal)[Math.max(0, Math.min(4, theme))];
        BG=c[0]; SF=c[1]; SF2=c[2]; LN=c[3]; AC=c[4];
        TX = dark ? 0xFFF3F7FC : 0xFF17232D;
        MUT = dark ? 0xFFB5C3D0 : 0xFF5D6B78;
        ER = 0xFFFF6B6B;
    }

    void reload() {
        chats = Store.loadChats();
        mem = Store.loadMem();
        prompt = Store.prompt();
        cur = null;
    }

    // ---- مساعدات الواجهة ----
    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    GradientDrawable rr(int fill, int r, int stroke) {
        GradientDrawable g = new GradientDrawable();
        int theme = 0;
        try { theme = Integer.parseInt(Store.get("theme", "0")); } catch (Exception ignored) { }
        if (theme >= 0 && theme < 5) {
            int top = dark ? 0x554D7892 : 0xEEFFFFFF;
            int bottom = fill;
            g.setOrientation(GradientDrawable.Orientation.TL_BR);
            g.setColors(new int[]{top, bottom});
        } else g.setColor(fill);
        g.setCornerRadius(dp(r));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    TextView tv(String t, int color, float sp) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(color);
        v.setTextSize(sp);
        return v;
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    Button flat(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(TX);
        b.setTextSize(20);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setStateListAnimator(null);
        b.setMinWidth(0);
        b.setMinimumWidth(dp(48));
        b.setOnClickListener(l);
        return b;
    }

    Button btn(String t, int bg, int fg, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(15);
        b.setBackground(rr(bg, 12, 0));
        b.setStateListAnimator(null);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48));
        p.topMargin = dp(8);
        b.setLayoutParams(p);
        return b;
    }

    TextView note(String t) {
        TextView v = tv(t, MUT, 13);
        v.setLineSpacing(0, 1.3f);
        v.setPadding(dp(4), dp(4), dp(4), dp(8));
        return v;
    }

    TextView box(String t) {
        TextView v = tv(t, TX, 13.5f);
        v.setLineSpacing(0, 1.3f);
        v.setPadding(dp(12), dp(12), dp(12), dp(12));
        v.setBackground(rr(dark ? 0xFF2B2118 : 0xFFFFF1E6, 12, AC));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(10);
        v.setLayoutParams(p);
        return v;
    }

    View row(String badge, String t, String s, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(12), dp(12), dp(12), dp(12));
        r.setBackground(rr(SF, 12, LN));
        TextView b = tv(badge, AC, 12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setBackground(rr(SF2, 10, 0));
        r.addView(b, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout m = new LinearLayout(this);
        m.setOrientation(LinearLayout.VERTICAL);
        TextView tt = tv(t, TX, 15);
        tt.setTypeface(Typeface.DEFAULT_BOLD);
        m.addView(tt);
        if (s != null && !s.isEmpty()) m.addView(tv(s, MUT, 12.5f));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -2, 1f);
        mp.setMarginStart(dp(12));
        r.addView(m, mp);
        if (l != null) r.setOnClickListener(l);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.bottomMargin = dp(8);
        r.setLayoutParams(rp);
        return r;
    }

    TextWatcher watcher(final Txt t) {
        return new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) { t.on(e.toString()); }
        };
    }

    EditText field(LinearLayout p, String label, String val, boolean pwd, boolean ltr) {
        TextView l = tv(label, MUT, 13);
        l.setPadding(0, dp(8), 0, dp(4));
        p.addView(l);
        EditText e = new EditText(this);
        e.setText(val);
        e.setTextColor(TX);
        e.setHintTextColor(MUT);
        e.setBackground(rr(SF, 10, LN));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setInputType(pwd ? (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                : InputType.TYPE_CLASS_TEXT);
        if (ltr) {
            e.setTextDirection(View.TEXT_DIRECTION_LTR);
            e.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        }
        p.addView(e, new LinearLayout.LayoutParams(-1, -2));
        return e;
    }

    View toggle(String t, boolean v, final String key) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(12), dp(8), dp(12), dp(8));
        r.setBackground(rr(SF, 12, LN));
        TextView l = tv(t, TX, 14);
        r.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(v);
        sw.setOnCheckedChangeListener((cb, c) -> Store.setFlag(key, c));
        r.addView(sw);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(8);
        r.setLayoutParams(p);
        return r;
    }

    void confirm(String msg, final Runnable yes) {
        new AlertDialog.Builder(this).setMessage(msg)
                .setPositiveButton("تأكيد", (d, w) -> yes.run())
                .setNegativeButton("إلغاء", null).show();
    }

    LinearLayout page() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(12), dp(12), dp(12), dp(24));
        return p;
    }

    void setPage(LinearLayout p) {
        content.removeAllViews();
        ScrollView sv = new ScrollView(this);
        sv.addView(p);
        content.addView(sv, new FrameLayout.LayoutParams(-1, -1));
    }

    void header(String t, boolean back, boolean showNew) {
        title.setText(t);
        backBtn.setVisibility(back ? View.VISIBLE : View.GONE);
        newBtn.setVisibility(showNew ? View.VISIBLE : View.INVISIBLE);
    }

    void selNav(int idx) {
        for (int i = 0; i < navBtns.length; i++) {
            navBtns[i].setTextColor(i == idx ? AC : MUT);
            navBtns[i].setTypeface(i == idx ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    void buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setFitsSystemWindows(true);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(SF);
        backBtn = flat("→", v -> onBackPressed());
        title = tv("Mi_Max Ai", TX, 17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(8), 0, dp(8), 0);
        newBtn = flat("+", v -> {
            cur = null; attImg = null; attAud = null; attThumb = null;
            showChat();
        });
        top.addView(backBtn, new LinearLayout.LayoutParams(-2, dp(54)));
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(newBtn, new LinearLayout.LayoutParams(-2, dp(54)));
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(54)));
        View d1 = new View(this);
        d1.setBackgroundColor(LN);
        root.addView(d1, new LinearLayout.LayoutParams(-1, dp(1)));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        View d2 = new View(this);
        d2.setBackgroundColor(LN);
        root.addView(d2, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout nav = new LinearLayout(this);
        nav.setBackgroundColor(SF);
        String[] names = {"المحادثة", "السجل", "التنزيل", "الإعدادات"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            Button b = new Button(this);
            b.setText(names[i]);
            b.setAllCaps(false);
            b.setTextSize(13);
            b.setBackgroundColor(Color.TRANSPARENT);
            b.setStateListAnimator(null);
            b.setOnClickListener(v -> goTab(idx));
            navBtns[i] = b;
            nav.addView(b, new LinearLayout.LayoutParams(0, dp(52), 1f));
        }
        root.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);

        getWindow().setStatusBarColor(SF);
        getWindow().setNavigationBarColor(SF);
        if (!dark && Build.VERSION.SDK_INT >= 23) {
            View dv = getWindow().getDecorView();
            dv.setSystemUiVisibility(dv.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    void goTab(int i) {
        sub = null;
        if (i == 0) showChat();
        else if (i == 1) showHist();
        else if (i == 2) showDl();
        else showSettings();
    }

    @Override
    public void onBackPressed() {
        if (sub != null) showSettings();
        else if (!tab.equals("chat")) showChat();
        else super.onBackPressed();
    }

    // =====================================================================
    //                              المحادثة
    // =====================================================================
    void showChat() {
        if (input != null) draft = input.getText().toString();
        tab = "chat";
        sub = null;
        selNav(0);
        header(cur != null ? cur.title : "Mi_Max Ai", false, true);
        content.removeAllViews();

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        msgScroll = new ScrollView(this);
        msgBox = new LinearLayout(this);
        msgBox.setOrientation(LinearLayout.VERTICAL);
        msgBox.setPadding(dp(12), dp(12), dp(12), dp(8));
        msgScroll.addView(msgBox);
        col.addView(msgScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        attInfo = tv("", TX, 13);
        attInfo.setPadding(dp(14), dp(8), dp(14), dp(8));
        attInfo.setBackground(rr(SF2, 10, 0));
        attInfo.setVisibility(View.GONE);
        attInfo.setOnClickListener(v -> {
            if (rec != null && rec.on) return;
            attImg = null; attAud = null; attThumb = null;
            updateAtt();
        });
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
        ap.setMargins(dp(10), dp(4), dp(10), 0);
        col.addView(attInfo, ap);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.BOTTOM);
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));
        Button pic = new Button(this);
        pic.setText("📷");
        pic.setTextSize(18);
        pic.setBackground(rr(SF, 22, LN));
        pic.setStateListAnimator(null);
        pic.setOnClickListener(v -> pickImage());
        bar.addView(pic, new LinearLayout.LayoutParams(dp(46), dp(46)));
        micBtn = new Button(this);
        micBtn.setTextSize(18);
        micBtn.setStateListAnimator(null);
        micBtn.setOnClickListener(v -> toggleMic());
        boolean recording = rec != null && rec.on;
        micBtn.setText(recording ? "■" : "🎤");
        micBtn.setTextColor(recording ? Color.WHITE : TX);
        micBtn.setBackground(rr(recording ? ER : SF, 22, recording ? 0 : LN));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(46), dp(46));
        mp.setMarginStart(dp(6));
        bar.addView(micBtn, mp);
        input = new EditText(this);
        input.setHint("اكتب رسالتك…");
        input.setHintTextColor(MUT);
        input.setTextColor(TX);
        input.setTextSize(15.5f);
        input.setBackground(rr(SF, 22, LN));
        input.setPadding(dp(16), dp(10), dp(16), dp(10));
        input.setMaxLines(5);
        input.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(draft);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, -2, 1f);
        ip.setMargins(dp(6), 0, dp(6), 0);
        bar.addView(input, ip);
        sendBtn = new Button(this);
        sendBtn.setAllCaps(false);
        sendBtn.setTextColor(Color.WHITE);
        sendBtn.setText(busy ? "إيقاف" : "إرسال");
        sendBtn.setBackground(rr(AC, 22, 0));
        sendBtn.setStateListAnimator(null);
        sendBtn.setOnClickListener(v -> send(false));
        bar.addView(sendBtn, new LinearLayout.LayoutParams(-2, dp(46)));
        col.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        content.addView(col, new FrameLayout.LayoutParams(-1, -1));
        fillMsgs();
        updateAtt();
    }

    void scrollEnd() {
        if (msgScroll != null) msgScroll.post(() -> msgScroll.fullScroll(View.FOCUS_DOWN));
    }

    void fillMsgs() {
        msgBox.removeAllViews();
        lastTv = null;
        if (cur == null || cur.msgs.isEmpty()) {
            TextView h = tv("", TX, 26);
            SpannableString ss = new SpannableString("Mi_Max Ai");
            ss.setSpan(new ForegroundColorSpan(AC), 7, 9, 0);
            h.setText(ss);
            h.setTypeface(Typeface.DEFAULT_BOLD);
            h.setGravity(Gravity.CENTER);
            h.setPadding(0, dp(40), 0, dp(6));
            msgBox.addView(h);
            TextView s = tv("اسألني أي شيء، أرسل صورة لأصفها، أو سجّل رسالة صوتية.", MUT, 14);
            s.setGravity(Gravity.CENTER);
            s.setPadding(dp(10), 0, dp(10), dp(20));
            msgBox.addView(s);
            String[] chips = {"لخّص لي نصاً", "اشرح لي هذه الصورة", "اكتب لي كوداً", "ترجم إلى الإنجليزية"};
            for (final String c : chips) {
                Button b = new Button(this);
                b.setText(c);
                b.setAllCaps(false);
                b.setTextColor(TX);
                b.setTextSize(14);
                b.setBackground(rr(SF, 20, LN));
                b.setStateListAnimator(null);
                b.setOnClickListener(v -> { input.setText(c + ": "); input.setSelection(input.length()); input.requestFocus(); });
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(44));
                p.bottomMargin = dp(8);
                msgBox.addView(b, p);
            }
            return;
        }
        for (int i = 0; i < cur.msgs.size(); i++) bubble(cur.msgs.get(i), i == cur.msgs.size() - 1);
        scrollEnd();
    }

    void bubble(final Store.Msg m, final boolean last) {
        final boolean u = m.r.equals("user");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(13), dp(10), dp(13), dp(10));
        box.setBackground(u ? rr(AC, 16, 0) : rr(SF, 16, LN));
        if (m.img != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(m.img);
            iv.setAdjustViewBounds(true);
            iv.setMaxHeight(dp(150));
            iv.setMaxWidth(dp(150));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.bottomMargin = dp(6);
            box.addView(iv, lp);
        }
        TextView t = new TextView(this);
        t.setTextSize(15.5f);
        t.setLineSpacing(0, 1.25f);
        t.setTextColor(u ? Color.WHITE : TX);
        t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        t.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.85f));
        t.setText(u ? m.t : Md.render(m.t, SF2));
        box.addView(t);
        box.setOnLongClickListener(v -> { msgMenu(m, last); return true; });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.gravity = u ? Gravity.START : Gravity.END;
        lp.bottomMargin = dp(10);
        msgBox.addView(box, lp);
        lastTv = t;
    }

    void msgMenu(final Store.Msg m, boolean last) {
        final ArrayList<String> items = new ArrayList<>();
        items.add("نسخ");
        if (m.r.equals("assistant")) {
            items.add("استماع");
            if (last) items.add("إعادة الرد");
        }
        final String[] arr = items.toArray(new String[0]);
        new AlertDialog.Builder(this).setItems(arr, (d, w) -> {
            String s = arr[w];
            if (s.equals("نسخ")) copy(m.t);
            else if (s.equals("استماع")) speak(m.t);
            else send(true);
        }).show();
    }

    void copy(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("mimax", s));
        toast("تم النسخ");
    }

    void speak(String t) {
        pendingSpeak = t.replaceAll("```[\\s\\S]*?```", " ");
        if (tts == null) {
            tts = new TextToSpeech(this, st -> {
                if (st == TextToSpeech.SUCCESS) {
                    tts.setLanguage(new Locale("ar"));
                    tts.speak(pendingSpeak, TextToSpeech.QUEUE_FLUSH, null, "mm");
                } else toast("الاستماع غير متاح على هذا الجهاز");
            });
        } else {
            tts.speak(pendingSpeak, TextToSpeech.QUEUE_FLUSH, null, "mm");
        }
    }

    // ---- الذاكرة والاستدعاء ----
    HashSet<String> toks(String s) {
        HashSet<String> h = new HashSet<>();
        Matcher m = Pattern.compile("[\\p{L}\\p{N}]{3,}").matcher(s.toLowerCase());
        while (m.find()) h.add(m.group());
        return h;
    }

    ArrayList<String> recall(String q) {
        ArrayList<String> out = new ArrayList<>();
        if (!Store.flag("recall", true)) return out;
        HashSet<String> qt = toks(q);
        if (qt.isEmpty()) return out;
        ArrayList<Object[]> sc = new ArrayList<>();
        for (Store.Chat c : chats) {
            if (cur != null && c.id.equals(cur.id)) continue;
            for (Store.Msg m : c.msgs) {
                HashSet<String> mt = toks(m.t);
                int n = 0;
                for (String w : qt) if (mt.contains(w)) n++;
                if (n >= 2 || (qt.size() <= 2 && n >= 1)) {
                    String tx = m.t.length() > 220 ? m.t.substring(0, 220) : m.t;
                    sc.add(new Object[]{n, "[" + c.title + "] " + (m.r.equals("user") ? "المستخدم" : "المساعد") + ": " + tx});
                }
            }
        }
        Collections.sort(sc, (a, b) -> (Integer) b[0] - (Integer) a[0]);
        for (int i = 0; i < Math.min(4, sc.size()); i++) out.add((String) sc.get(i)[1]);
        return out;
    }

    String buildSystem(String q) {
        StringBuilder s = new StringBuilder(prompt.isEmpty() ? DEFAULT_PROMPT : prompt);
        s.append("\nالتاريخ الحالي: ")
                .append(new SimpleDateFormat("EEEE d MMMM yyyy", new Locale("ar")).format(new Date())).append(".");
        String u = Store.session();
        if (!u.isEmpty()) s.append("\nاسم حساب المستخدم: ").append(u).append(".");
        if (!mem.isEmpty()) {
            s.append("\n\nمعلومات محفوظة عن المستخدم (استخدمها عند الصلة فقط ولا تذكرها بلا داعٍ):");
            for (String m : mem) s.append("\n- ").append(m);
        }
        ArrayList<String> r = recall(q);
        if (!r.isEmpty()) {
            s.append("\n\nمقتطفات من محادثات سابقة قد تفيد:");
            for (String x : r) s.append("\n- ").append(x);
        }
        return s.toString();
    }

    JSONObject jm(String role, Object content) {
        try {
            JSONObject o = new JSONObject();
            o.put("role", role);
            o.put("content", content);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    String cut(String s, int n) { return s.length() > n ? s.substring(0, n) : s; }

    void extractMem(final String u, final String a) {
        if (!Store.flag("autoMem", true)) return;
        new Thread(() -> {
            try {
                JSONArray ms = new JSONArray();
                ms.put(jm("system", "استخرج من الحوار حقائق ثابتة ومفيدة عن المستخدم (اسمه، اهتماماته، تفضيلاته، مشاريعه، لغته). "
                        + "أعد مصفوفة JSON فقط من جمل عربية قصيرة، أو [] إن لم يوجد شيء. "
                        + "لا تُدرج معلومات حساسة مثل الصحة أو المال أو كلمات السر."));
                ms.put(jm("user", "المستخدم: " + cut(u, 800) + "\nالمساعد: " + cut(a, 800)));
                String r = Api.chat(Store.key(), Store.model(), ms, 0.2, false, null, null);
                int st = r.indexOf('['), en = r.lastIndexOf(']');
                if (st < 0 || en < st) return;
                final JSONArray arr = new JSONArray(r.substring(st, en + 1));
                ui.post(() -> {
                    boolean ch = false;
                    for (int i = 0; i < arr.length() && i < 3; i++) {
                        String t = arr.optString(i, "").trim();
                        if (t.length() > 3 && !mem.contains(t) && mem.size() < 100) { mem.add(t); ch = true; }
                    }
                    if (ch) Store.saveMem(mem);
                });
            } catch (Exception e) { /* تجاهل */ }
        }).start();
    }

    // ---- الإرسال ----
    void send(final boolean regen) {
        if (busy) { cancel[0] = true; return; }
        if (Store.key().isEmpty()) {
            toast("أضف مفتاح API أولاً: الإعدادات ← الذكاء الاصطناعي");
            return;
        }
        final Store.Msg um;
        final String text;
        if (regen) {
            if (cur == null || cur.msgs.isEmpty()) return;
            if (cur.msgs.get(cur.msgs.size() - 1).r.equals("assistant")) cur.msgs.remove(cur.msgs.size() - 1);
            if (cur.msgs.isEmpty()) return;
            um = cur.msgs.get(cur.msgs.size() - 1);
            text = um.t;
        } else {
            String tx = input.getText().toString().trim();
            if (tx.isEmpty() && attImg == null && attAud == null) return;
            if (cur == null) {
                cur = new Store.Chat();
                chats.add(0, cur);
                cur.title = tx.isEmpty() ? "محادثة" : (tx.length() > 40 ? tx.substring(0, 40) : tx);
            }
            um = new Store.Msg("user", tx.isEmpty() ? (attAud != null ? "[رسالة صوتية]" : "[صورة]") : tx);
            um.img = attThumb;
            text = tx;
        }
        final String img = regen ? null : attImg;
        final String aud = regen ? null : attAud;
        final String model = aud != null ? (Store.audioModel().isEmpty() ? Store.model() : Store.audioModel()) : Store.model();

        JSONArray msgs = new JSONArray();
        msgs.put(jm("system", buildSystem(um.t)));
        ArrayList<Store.Msg> hist = new ArrayList<>();
        for (Store.Msg m : cur.msgs) if (m != um) hist.add(m);
        for (int i = Math.max(0, hist.size() - 16); i < hist.size(); i++) msgs.put(jm(hist.get(i).r, hist.get(i).t));
        Object content;
        if (img == null && aud == null) {
            content = um.t;
        } else {
            JSONArray parts = new JSONArray();
            try {
                JSONObject t = new JSONObject();
                t.put("type", "text");
                t.put("text", text.isEmpty() ? (aud != null ? "استمع إلى الرسالة الصوتية وأجب عليها." : "صف هذه الصورة بالتفصيل.") : text);
                parts.put(t);
                if (img != null) {
                    JSONObject p = new JSONObject();
                    p.put("type", "image_url");
                    p.put("image_url", new JSONObject().put("url", img));
                    parts.put(p);
                }
                if (aud != null) {
                    JSONObject p = new JSONObject();
                    p.put("type", "input_audio");
                    p.put("input_audio", new JSONObject().put("data", aud).put("format", "wav"));
                    parts.put(p);
                }
            } catch (Exception e) { /* تجاهل */ }
            content = parts;
        }
        msgs.put(jm("user", content));

        if (!regen) {
            cur.msgs.add(um);
            input.setText("");
            draft = "";
            attImg = null; attAud = null; attThumb = null;
        }
        final Store.Msg am = new Store.Msg("assistant", "");
        cur.msgs.add(am);
        cur.ts = System.currentTimeMillis();
        fillMsgs();
        updateAtt();
        title.setText(cur.title);
        final TextView tvv = lastTv;
        tvv.setText("…");
        busy = true;
        cancel = new boolean[]{false};
        final boolean[] cn = cancel;
        sendBtn.setText("إيقاف");
        final boolean stream = Store.flag("stream", true);
        final String key = Store.key();
        final double temp = Store.temp();
        final JSONArray fm = msgs;

        new Thread(() -> {
            String res = "";
            String err = null;
            try {
                res = Api.chat(key, model, fm, temp, stream,
                        full -> ui.post(() -> { am.t = full; tvv.setText(Md.render(full, SF2)); scrollEnd(); }), cn);
            } catch (Exception e) {
                err = e.getMessage();
                if (err == null) err = "خطأ غير معروف";
            }
            final String fr = res, fe = err;
            ui.post(() -> {
                if (fe != null) am.t = (am.t.isEmpty() ? "" : am.t + "\n\n") + "⚠ " + fe;
                else am.t = fr.isEmpty() ? (cn[0] ? "(تم الإيقاف)" : "(لا يوجد رد)") : fr;
                busy = false;
                Store.saveChats(chats);
                if (tab.equals("chat") && sub == null) showChat();
                if (fe == null && !fr.isEmpty() && !regen) extractMem(um.t, am.t);
            });
        }).start();
    }

    // =====================================================================
    //                         الصور والصوت
    // =====================================================================
    void updateAtt() {
        if (attInfo == null) return;
        ArrayList<String> p = new ArrayList<>();
        if (attImg != null) p.add("صورة");
        if (attAud != null) p.add("رسالة صوتية (" + attDur + " ث)");
        if (rec != null && rec.on) {
            attInfo.setText("جارٍ التسجيل… " + ((System.currentTimeMillis() - rec.t0) / 1000) + " ث");
            attInfo.setVisibility(View.VISIBLE);
        } else if (p.isEmpty()) {
            attInfo.setVisibility(View.GONE);
        } else {
            attInfo.setText("المرفقات: " + TextUtils.join(" + ", p) + "  — اضغط للإزالة");
            attInfo.setVisibility(View.VISIBLE);
        }
    }

    void pickImage() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(Intent.createChooser(i, "اختر صورة"), REQ_IMG);
    }

    @Override
    protected void onActivityResult(int rq, int rs, Intent d) {
        super.onActivityResult(rq, rs, d);
        if (rq == REQ_IMG && rs == RESULT_OK && d != null && d.getData() != null) {
            final Uri u = d.getData();
            new Thread(() -> {
                try {
                    final Object[] r = loadImage(u);
                    ui.post(() -> { attImg = (String) r[0]; attThumb = (Bitmap) r[1]; updateAtt(); });
                } catch (Exception e) {
                    ui.post(() -> toast("تعذر قراءة الصورة"));
                }
            }).start();
        }
    }

    Bitmap scale(Bitmap b, int max) {
        float r = Math.min(1f, (float) max / Math.max(b.getWidth(), b.getHeight()));
        if (r >= 1f) return b;
        return Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * r), Math.round(b.getHeight() * r), true);
    }

    Object[] loadImage(Uri u) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = getContentResolver().openInputStream(u);
        BitmapFactory.decodeStream(in, null, o);
        in.close();
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / s > 2560) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        in = getContentResolver().openInputStream(u);
        Bitmap b = BitmapFactory.decodeStream(in, null, o2);
        in.close();
        if (b == null) throw new Exception("bad image");
        int rot = 0;
        try {
            in = getContentResolver().openInputStream(u);
            ExifInterface ex = new ExifInterface(in);
            int ori = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            in.close();
            if (ori == 6) rot = 90; else if (ori == 3) rot = 180; else if (ori == 8) rot = 270;
        } catch (Exception e) { /* تجاهل */ }
        if (rot != 0) {
            Matrix mx = new Matrix();
            mx.postRotate(rot);
            b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), mx, true);
        }
        Bitmap big = scale(b, 1280);
        Bitmap th = scale(b, 180);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        big.compress(Bitmap.CompressFormat.JPEG, 85, bo);
        return new Object[]{"data:image/jpeg;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP), th};
    }

    /** تسجيل صوتي PCM 16kHz أحادي ويُحوَّل إلى WAV. */
    static class Rec {
        AudioRecord ar;
        Thread th;
        volatile boolean on;
        final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        long t0;

        boolean start() {
            try {
                int min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                ar = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 8192));
                if (ar.getState() != AudioRecord.STATE_INITIALIZED) return false;
                ar.startRecording();
            } catch (Exception e) {
                return false;
            }
            on = true;
            t0 = System.currentTimeMillis();
            th = new Thread(() -> {
                byte[] b = new byte[4096];
                while (on) {
                    int n = ar.read(b, 0, b.length);
                    if (n > 0) { synchronized (pcm) { pcm.write(b, 0, n); } }
                    if (System.currentTimeMillis() - t0 > 120000) on = false;
                }
            });
            th.start();
            return true;
        }

        byte[] stop() {
            on = false;
            try { th.join(1500); } catch (Exception e) { /* تجاهل */ }
            try { ar.stop(); ar.release(); } catch (Exception e) { /* تجاهل */ }
            byte[] d;
            synchronized (pcm) { d = pcm.toByteArray(); }
            ByteBuffer bb = ByteBuffer.allocate(44 + d.length).order(ByteOrder.LITTLE_ENDIAN);
            bb.put("RIFF".getBytes());
            bb.putInt(36 + d.length);
            bb.put("WAVE".getBytes());
            bb.put("fmt ".getBytes());
            bb.putInt(16);
            bb.putShort((short) 1);
            bb.putShort((short) 1);
            bb.putInt(16000);
            bb.putInt(32000);
            bb.putShort((short) 2);
            bb.putShort((short) 16);
            bb.put("data".getBytes());
            bb.putInt(d.length);
            bb.put(d);
            return bb.array();
        }
    }

    void toggleMic() {
        if (rec != null && rec.on) { stopRec(); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUD);
            return;
        }
        startRec();
    }

    void startRec() {
        rec = new Rec();
        if (!rec.start()) { toast("تعذر بدء التسجيل"); rec = null; return; }
        attAud = null;
        micBtn.setText("■");
        micBtn.setTextColor(Color.WHITE);
        micBtn.setBackground(rr(ER, 22, 0));
        recTick = new Runnable() {
            public void run() {
                if (rec == null) return;
                if (!rec.on) { stopRec(); return; }
                updateAtt();
                ui.postDelayed(this, 500);
            }
        };
        ui.post(recTick);
    }

    void stopRec() {
        if (rec == null) return;
        Rec r = rec;
        rec = null;
        ui.removeCallbacks(recTick);
        byte[] wav = r.stop();
        attDur = (int) ((System.currentTimeMillis() - r.t0) / 1000);
        if (micBtn != null) {
            micBtn.setText("🎤");
            micBtn.setTextColor(TX);
            micBtn.setBackground(rr(SF, 22, LN));
        }
        if (wav != null && wav.length > 44 + 16000) attAud = Base64.encodeToString(wav, Base64.NO_WRAP);
        else toast("التسجيل قصير جداً");
        updateAtt();
    }

    @Override
    public void onRequestPermissionsResult(int rq, String[] p, int[] r) {
        super.onRequestPermissionsResult(rq, p, r);
        boolean ok = r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED;
        if (rq == REQ_AUD) {
            if (ok) startRec(); else toast("لم يتم السماح باستخدام الميكروفون");
        }
    }

    // =====================================================================
    //                               السجل
    // =====================================================================
    void showHist() {
        tab = "hist";
        sub = null;
        selNav(1);
        header("سجل المحادثات", false, false);
        LinearLayout p = page();
        final EditText s = new EditText(this);
        s.setHint("بحث في المحادثات");
        s.setHintTextColor(MUT);
        s.setTextColor(TX);
        s.setBackground(rr(SF, 10, LN));
        s.setPadding(dp(12), dp(10), dp(12), dp(10));
        s.setSingleLine(true);
        p.addView(s, new LinearLayout.LayoutParams(-1, -2));
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        p.addView(list, lp);
        s.addTextChangedListener(watcher(q -> drawHist(list, q)));
        drawHist(list, "");
        setPage(p);
    }

    void drawHist(LinearLayout list, String q) {
        list.removeAllViews();
        q = q.trim().toLowerCase();
        SimpleDateFormat df = new SimpleDateFormat("d MMM yyyy، h:mm a", new Locale("ar"));
        int n = 0;
        for (final Store.Chat c : chats) {
            boolean ok = q.isEmpty() || c.title.toLowerCase().contains(q);
            if (!ok) for (Store.Msg m : c.msgs) if (m.t.toLowerCase().contains(q)) { ok = true; break; }
            if (!ok) continue;
            n++;
            View r = row("💬", c.title, c.msgs.size() + " رسالة — " + df.format(new Date(c.ts)), v -> { cur = c; showChat(); });
            r.setOnLongClickListener(v -> { chatMenu(c); return true; });
            list.addView(r);
        }
        if (n == 0) list.addView(note(q.isEmpty() ? "لا توجد محادثات بعد. اضغط مطولاً على أي محادثة لخيارات الحذف والتصدير." : "لا نتائج"));
        else list.addView(note("اضغط مطولاً على المحادثة لفتح قائمة الخيارات."));
    }

    void chatMenu(final Store.Chat c) {
        final String[] it = {"فتح", "مشاركة كنص", "حذف"};
        new AlertDialog.Builder(this).setTitle(c.title).setItems(it, (d, w) -> {
            if (w == 0) { cur = c; showChat(); }
            else if (w == 1) {
                StringBuilder sb = new StringBuilder(c.title).append("\n\n");
                for (Store.Msg m : c.msgs) sb.append(m.r.equals("user") ? "أنا" : "Mi_Max Ai").append(":\n").append(m.t).append("\n\n");
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, sb.toString());
                startActivity(Intent.createChooser(i, "مشاركة"));
            } else {
                confirm("حذف هذه المحادثة؟", () -> {
                    chats.remove(c);
                    if (cur == c) cur = null;
                    Store.saveChats(chats);
                    showHist();
                });
            }
        }).show();
    }

    // =====================================================================
    //                              التنزيل
    // =====================================================================
    void showDl() {
        tab = "dl";
        sub = null;
        selNav(2);
        header("تنزيل الفيديو", false, false);
        LinearLayout p = page();
        p.addView(note("الصق رابط فيديو من تيك توك" + (Store.get("dlApi", "").isEmpty() ? "" : " أو أي موقع يدعمه خادم التنزيل الذي أضفته")
                + ". حمّل فقط ما يحق لك تنزيله."));
        final EditText u = field(p, "رابط الفيديو", "", false, true);
        final LinearLayout res = new LinearLayout(this);
        res.setOrientation(LinearLayout.VERTICAL);
        p.addView(btn("جلب الفيديو", AC, Color.WHITE, v -> {
            final String url = u.getText().toString().trim();
            if (!url.startsWith("http")) { toast("الصق رابطاً صحيحاً"); return; }
            res.removeAllViews();
            res.addView(note("جارٍ الجلب…"));
            new Thread(() -> {
                try {
                    final Api.Video vd = Api.resolve(url, Store.get("dlApi", ""), Store.get("dlKey", ""));
                    ui.post(() -> showVideo(res, vd));
                } catch (Exception e) {
                    final String m = e.getMessage() == null ? "فشل الجلب" : e.getMessage();
                    ui.post(() -> { res.removeAllViews(); res.addView(box(m)); });
                }
            }).start();
        }));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = dp(12);
        p.addView(res, rp);
        setPage(p);
    }

    void showVideo(LinearLayout res, Api.Video vd) {
        res.removeAllViews();
        if (vd.cover != null) {
            final ImageView iv = new ImageView(this);
            iv.setAdjustViewBounds(true);
            iv.setMaxHeight(dp(240));
            final String cu = vd.cover;
            new Thread(() -> {
                try {
                    InputStream in = new URL(cu).openStream();
                    final Bitmap b = BitmapFactory.decodeStream(in);
                    in.close();
                    if (b != null) ui.post(() -> iv.setImageBitmap(b));
                } catch (Exception e) { /* تجاهل */ }
            }).start();
            res.addView(iv, new LinearLayout.LayoutParams(-1, -2));
        }
        TextView t = tv(vd.title == null ? "" : vd.title, TX, 15);
        t.setPadding(0, dp(8), 0, dp(4));
        res.addView(t);
        for (final Api.Item it : vd.items) {
            res.addView(btn("تنزيل: " + it.label, AC, Color.WHITE, v -> download(it)));
            res.addView(btn("فتح في المتصفح: " + it.label, SF2, TX, v -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(it.url))); }
                catch (Exception e) { toast("تعذر فتح الرابط"); }
            }));
        }
    }

    void download(Api.Item it) {
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STO);
            toast("اسمح بالتخزين ثم أعد الضغط");
            return;
        }
        try {
            String name = it.file.replaceAll("[\\\\/:*?\"<>|]", "_");
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(it.url));
            r.setTitle(name);
            r.addRequestHeader("User-Agent", "Mozilla/5.0");
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "MiMaxAi/" + name);
            dm.enqueue(r);
            toast("بدأ التنزيل — تجده في Download/MiMaxAi");
        } catch (Exception e) {
            toast("تعذر التنزيل. جرّب فتح الرابط في المتصفح.");
        }
    }

    // =====================================================================
    //                             الإعدادات
    // =====================================================================
    void showSettings() {
        tab = "set";
        sub = null;
        selNav(3);
        header("الإعدادات", false, false);
        LinearLayout p = page();
        String u = Store.session();
        p.addView(row("ID", u.isEmpty() ? "إنشاء حساب" : "حسابي", u.isEmpty() ? "احفظ بياناتك باسم مستخدم وكلمة مرور" : u, v -> openSub("account")));
        p.addView(row("API", "الذكاء الاصطناعي", "مفتاح API والنماذج المجانية", v -> openSub("ai")));
        p.addView(row("TXT", "البرمبت", "تعليمات تحسّن أسلوب الذكاء الاصطناعي", v -> openSub("prompt")));
        p.addView(row("MEM", "الذاكرة", "ما يتذكره عنك (" + mem.size() + ")", v -> openSub("mem")));
        p.addView(row("DL", "إعدادات التنزيل", "خادم تنزيل لمواقع مثل يوتيوب", v -> openSub("dl")));
        p.addView(row("GL", "الثيمات الزجاجية", "خمسة تصاميم شفافة — الحالي: " + themeName(), v -> chooseTheme()));
        p.addView(row("UI", "الوضع الفاتح والداكن", dark ? "داكن — اضغط للتبديل" : "فاتح — اضغط للتبديل", v -> {
            Store.setFlag("dark", !dark);
            toSettings = true;
            recreate();
        }));
        p.addView(row("DEV", "المطور والشكاوى", "محمد السعيد علي", v -> openSub("dev")));
        setPage(p);
    }

    String themeName() {
        String[] n = {"زجاج سماوي", "زجاج المحيط", "زجاج بنفسجي", "زجاج وردي", "زجاج زمردي"};
        int i = 0; try { i = Integer.parseInt(Store.get("theme", "0")); } catch (Exception ignored) { }
        return n[Math.max(0, Math.min(4, i))];
    }

    int safeThemeIndex() {
        try { return Integer.parseInt(Store.get("theme", "0")); } catch (Exception e) { return 0; }
    }

    void chooseTheme() {
        final String[] names = {"زجاج سماوي", "زجاج المحيط", "زجاج بنفسجي", "زجاج وردي", "زجاج زمردي"};
        new AlertDialog.Builder(this).setTitle("اختر الثيم")
                .setSingleChoiceItems(names, Math.max(0, Math.min(4, safeThemeIndex())), (d, which) -> {
                    Store.put("theme", String.valueOf(which));
                    d.dismiss();
                    toSettings = true;
                    recreate();
                }).setNegativeButton("إغلاق", null).show();
    }

    void openSub(String s) {
        sub = s;
        LinearLayout p = page();
        String t = "";
        switch (s) {
            case "account": t = Store.session().isEmpty() ? "إنشاء حساب" : "حسابي"; pAccount(p); break;
            case "ai": t = "الذكاء الاصطناعي"; pAi(p); break;
            case "prompt": t = "البرمبت"; pPrompt(p); break;
            case "mem": t = "الذاكرة"; pMem(p); break;
            case "dl": t = "إعدادات التنزيل"; pDl(p); break;
            default: t = "المطور والشكاوى"; pDev(p); break;
        }
        header(t, true, false);
        setPage(p);
    }

    void pAccount(final LinearLayout p) {
        if (!Store.session().isEmpty()) {
            p.removeAllViews();
            TextView av = tv(Store.session().substring(0, 1).toUpperCase(), Color.WHITE, 34);
            av.setGravity(Gravity.CENTER);
            av.setTypeface(Typeface.DEFAULT_BOLD);
            av.setBackground(rr(AC, 42, 0));
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(84), dp(84));
            ap.gravity = Gravity.CENTER_HORIZONTAL;
            ap.topMargin = dp(12);
            p.addView(av, ap);
            TextView n = tv(Store.session(), TX, 20);
            n.setGravity(Gravity.CENTER);
            n.setPadding(0, dp(10), 0, dp(4));
            p.addView(n);
            TextView c = note(chats.size() + " محادثة — " + mem.size() + " عنصر في الذاكرة");
            c.setGravity(Gravity.CENTER);
            p.addView(c);
            p.addView(box("الحساب محفوظ على هذا الجهاز فقط. لا يوجد خادم، لذلك لا يمكن استرجاع كلمة المرور ولا فتح الحساب من جهاز آخر."));
            p.addView(btn("تسجيل الخروج", SF2, TX, v -> { Store.logout(); reload(); toast("تم تسجيل الخروج"); showSettings(); }));
            p.addView(btn("حذف الحساب وبياناته", SF, ER, v -> confirm("حذف الحساب وكل محادثاته وذاكرته نهائياً؟", () -> {
                Store.deleteAccount();
                reload();
                showSettings();
            })));
            return;
        }
        accountForm(p, true);
    }

    void accountForm(final LinearLayout p, final boolean reg) {
        p.removeAllViews();
        p.addView(box("الحساب هنا محلي على جهازك: يفصل محادثاتك وذاكرتك عن غيرك على نفس الجهاز. "
                + "تسجيل الدخول بحساب Google غير متاح لأنه يحتاج خادماً خاصاً بك."));
        final EditText u = field(p, "اسم المستخدم", "", false, true);
        final EditText pw = field(p, "كلمة المرور", "", true, true);
        final EditText pw2 = reg ? field(p, "تأكيد كلمة المرور", "", true, true) : null;
        p.addView(btn(reg ? "إنشاء الحساب" : "تسجيل الدخول", AC, Color.WHITE, v -> {
            try {
                if (reg) {
                    if (!pw.getText().toString().equals(pw2.getText().toString())) throw new Exception("كلمتا المرور غير متطابقتين");
                    Store.register(u.getText().toString(), pw.getText().toString());
                } else {
                    Store.login(u.getText().toString(), pw.getText().toString());
                }
                reload();
                toast("تم بنجاح");
                showSettings();
            } catch (Exception e) {
                toast(e.getMessage() == null ? "حدث خطأ" : e.getMessage());
            }
        }));
        p.addView(btn(reg ? "لدي حساب بالفعل" : "إنشاء حساب جديد", SF2, TX, v -> accountForm(p, !reg)));
        p.addView(note("يمكنك المتابعة كضيف بدون حساب. عند إنشاء حساب تُنقل محادثاتك الحالية إليه."));
    }

    void pAi(final LinearLayout p) {
        p.addView(box("يوجد مفتاح افتراضي مدمج للتشغيل مباشرة. يمكنك إدخال مفتاحك الشخصي هنا بدلاً منه. تنبيه: المفتاح المدمج داخل APK يمكن استخراجه من نسخة التطبيق؛ تجزئته في ملفات المصدر ليست تشفيراً آمناً."));
        EditText k = field(p, "مفتاح API الشخصي (اتركه فارغاً لاستخدام الافتراضي)", Store.get("key", ""), true, true);
        k.setHint("sk-or-v1-…");
        k.addTextChangedListener(watcher(s -> Store.put("key", s.trim())));
        final TextView res = note("");
        p.addView(btn("اختبار المفتاح", SF2, TX, v -> {
            res.setText("جارٍ الاختبار…");
            new Thread(() -> {
                String m;
                try { m = Api.keyInfo(Store.key()); } catch (Exception e) { m = "فشل: " + e.getMessage(); }
                final String fm = m;
                ui.post(() -> res.setText(fm));
            }).start();
        }));
        p.addView(res);
        fModel = field(p, "النموذج", Store.model(), false, true);
        fModel.addTextChangedListener(watcher(s -> Store.put("model", s.trim())));
        fAudio = field(p, "نموذج الرسائل الصوتية (يجب أن يدعم الصوت)", Store.audioModel(), false, true);
        fAudio.addTextChangedListener(watcher(s -> Store.put("audioModel", s.trim())));
        p.addView(btn("عرض النماذج المجانية ومميزاتها", AC, Color.WHITE, v -> {
            toast("جارٍ جلب النماذج المجانية…");
            new Thread(() -> {
                try {
                    final JSONArray d = Api.models();
                    ui.post(() -> showFreeModels(d));
                } catch (Exception e) {
                    final String m = String.valueOf(e.getMessage());
                    ui.post(() -> toast("تعذر جلب النماذج: " + m));
                }
            }).start();
        }));
        p.addView(btn("اختيار من قائمة النماذج", SF2, TX, v -> {
            toast("جارٍ جلب القائمة…");
            new Thread(() -> {
                try {
                    final JSONArray d = Api.models();
                    ui.post(() -> new AlertDialog.Builder(this).setItems(
                            new String[]{"نموذج المحادثة (يدعم الصور)", "نموذج الصوت"},
                            (dg, w) -> pickModel(d, w == 1)).show());
                } catch (Exception e) {
                    final String m = String.valueOf(e.getMessage());
                    ui.post(() -> toast("تعذر جلب القائمة: " + m));
                }
            }).start();
        }));
        final TextView tl = tv("الإبداع (الحرارة): " + Store.temp(), MUT, 13);
        tl.setPadding(0, dp(12), 0, dp(4));
        p.addView(tl);
        SeekBar sb = new SeekBar(this);
        sb.setMax(15);
        sb.setProgress((int) Math.round(Store.temp() * 10));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean u) {
                Store.put("temp", String.valueOf(v / 10.0));
                tl.setText("الإبداع (الحرارة): " + (v / 10.0));
            }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        p.addView(sb, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-1, dp(12));
        p.addView(new View(this), gap);
        p.addView(toggle("عرض الرد تدريجياً أثناء الكتابة", Store.flag("stream", true), "stream"));
        p.addView(toggle("حفظ المعلومات تلقائياً في الذاكرة", Store.flag("autoMem", true), "autoMem"));
        p.addView(toggle("تذكّر المحادثات السابقة", Store.flag("recall", true), "recall"));
        p.addView(note("الحفظ التلقائي يرسل مقطعاً قصيراً من كل حوار إلى النموذج لاستخراج المعلومات المفيدة، فيستهلك رصيداً بسيطاً إضافياً."));
    }

    void showFreeModels(JSONArray data) {
        final ArrayList<String> ids = new ArrayList<>();
        final ArrayList<String> details = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject m = data.optJSONObject(i);
            if (m == null) continue;
            JSONObject pricing = m.optJSONObject("pricing");
            if (pricing == null) continue;
            double in = parsePrice(pricing.optString("prompt", "-1"));
            double out = parsePrice(pricing.optString("completion", "-1"));
            if (in != 0 || out != 0) continue;
            String id = m.optString("id", "");
            if (id.isEmpty()) continue;
            JSONObject arch = m.optJSONObject("architecture");
            JSONArray mods = arch != null ? arch.optJSONArray("input_modalities") : null;
            StringBuilder modalityText = new StringBuilder();
            if (mods != null) {
                for (int j = 0; j < mods.length(); j++) {
                    if (j > 0) modalityText.append("، ");
                    modalityText.append(mods.optString(j));
                }
            }
            String modalities = modalityText.length() == 0 ? "نص" : modalityText.toString();
            long ctx = m.optLong("context_length", 0);
            JSONArray outMods = arch != null ? arch.optJSONArray("output_modalities") : null;
            StringBuilder outputText = new StringBuilder();
            if (outMods != null) {
                for (int j = 0; j < outMods.length(); j++) {
                    if (j > 0) outputText.append("، ");
                    outputText.append(outMods.optString(j));
                }
            }
            String name = m.optString("name", id);
            String description = m.optString("description", "");
            if (description.length() > 100) description = description.substring(0, 100) + "…";
            ids.add(id);
            details.add(name + "\\n" + id + "\\nمجاني حسب بيانات الموقع • سياق: " + (ctx > 0 ? ctx : "غير محدد")
                    + "\\nالمدخلات: " + modalities + " • المخرجات: " + (outputText.length() == 0 ? "غير محدد" : outputText)
                    + (description.isEmpty() ? "" : "\\n" + description));
        }
        if (ids.isEmpty()) { toast("لم يعثر الموقع على نماذج بسعر صفر حالياً"); return; }
        final String[] labels = details.toArray(new String[0]);
        final String[] choices = ids.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("النماذج المجانية (" + ids.size() + ")")
                .setItems(labels, (dialog, which) -> {
                    Store.put("model", choices[which]);
                    if (fModel != null) fModel.setText(choices[which]);
                    toast("تم اختيار " + choices[which]);
                }).setNegativeButton("إغلاق", null).show();
    }

    double parsePrice(String value) {
        try { return Double.parseDouble(value); } catch (Exception e) { return -1; }
    }

    void pickModel(JSONArray d, final boolean audio) {
        final ArrayList<String> ids = new ArrayList<>();
        for (int i = 0; i < d.length() && ids.size() < 80; i++) {
            JSONObject m = d.optJSONObject(i);
            if (m == null) continue;
            JSONObject ar = m.optJSONObject("architecture");
            JSONArray im = ar != null ? ar.optJSONArray("input_modalities") : null;
            if (im == null) continue;
            String want = audio ? "audio" : "image";
            for (int j = 0; j < im.length(); j++) {
                if (want.equals(im.optString(j))) { ids.add(m.optString("id")); break; }
            }
        }
        if (ids.isEmpty()) { toast("لا توجد نماذج مناسبة"); return; }
        final String[] arr = ids.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle(audio ? "نماذج تدعم الصوت" : "نماذج تدعم الصور")
                .setItems(arr, (dg, w) -> {
                    Store.put(audio ? "audioModel" : "model", arr[w]);
                    if (audio && fAudio != null) fAudio.setText(arr[w]);
                    if (!audio && fModel != null) fModel.setText(arr[w]);
                }).show();
    }

    void pPrompt(LinearLayout p) {
        p.addView(note("اكتب هنا تعليمات تُرسل مع كل محادثة لتحدد شخصية الذكاء الاصطناعي وأسلوبه. اتركها فارغة لاستخدام الافتراضي."));
        final String[] names = {"مساعد عام", "مبرمج", "مترجم", "مدرّس"};
        final String[] texts = {
                DEFAULT_PROMPT,
                "أنت Mi_Max Ai، مهندس برمجيات خبير. اشرح بإيجاز ثم أعطِ كوداً كاملاً قابلاً للتشغيل داخل كتل كود، ونبّه على الأخطاء الشائعة. أجب بلغة المستخدم.",
                "أنت Mi_Max Ai، مترجم محترف. ترجم النص بدقة وبأسلوب طبيعي بين العربية والإنجليزية (أو اللغة المطلوبة) دون شرح إلا إذا طُلب.",
                "أنت Mi_Max Ai، مدرّس صبور. اشرح بخطوات بسيطة مع أمثلة، ثم اطرح سؤالاً قصيراً للتأكد من الفهم. أجب بلغة المستخدم."};
        final EditText e = field(p, "البرمبت", prompt, false, false);
        e.setMinLines(8);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setHint(DEFAULT_PROMPT);
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            p.addView(btn("قالب: " + names[i], SF2, TX, v -> e.setText(texts[idx])));
        }
        p.addView(btn("حفظ", AC, Color.WHITE, v -> {
            prompt = e.getText().toString().trim();
            Store.setPrompt(prompt);
            toast("تم الحفظ");
        }));
        p.addView(btn("استعادة الافتراضي", SF2, TX, v -> {
            prompt = "";
            Store.setPrompt("");
            e.setText("");
            toast("تمت الاستعادة");
        }));
    }

    void pMem(final LinearLayout p) {
        p.removeAllViews();
        p.addView(note("هذه المعلومات تُرسل مع المحادثات ليعرف الذكاء الاصطناعي من أنت. يمكنك إضافتها أو حذفها."));
        final EditText e = field(p, "أضف معلومة", "", false, false);
        e.setHint("مثال: اسمي محمد وأحب البرمجة");
        p.addView(btn("إضافة", AC, Color.WHITE, v -> {
            String t = e.getText().toString().trim();
            if (t.isEmpty()) return;
            mem.add(t);
            Store.saveMem(mem);
            pMem(p);
        }));
        if (mem.isEmpty()) {
            p.addView(note("الذاكرة فارغة."));
            return;
        }
        for (final String m : new ArrayList<>(mem)) {
            View r = row("•", m, "اضغط للحذف", v -> {
                mem.remove(m);
                Store.saveMem(mem);
                pMem(p);
            });
            p.addView(r);
        }
        p.addView(btn("مسح كل الذاكرة", SF, ER, v -> confirm("مسح كل الذاكرة؟", () -> {
            mem.clear();
            Store.saveMem(mem);
            pMem(p);
        })));
    }

    void pDl(LinearLayout p) {
        p.addView(box("تيك توك يعمل مباشرة. يوتيوب ومواقع أخرى تحتاج خادم تنزيل خارجياً (متوافق مع Cobalt API) لأن الهاتف وحده لا يستطيع تنزيلها."));
        EditText a = field(p, "رابط خادم التنزيل (اختياري)", Store.get("dlApi", ""), false, true);
        a.setHint("https://your-server.example/");
        a.addTextChangedListener(watcher(s -> Store.put("dlApi", s.trim())));
        EditText k = field(p, "مفتاح الخادم (إن وُجد)", Store.get("dlKey", ""), true, true);
        k.addTextChangedListener(watcher(s -> Store.put("dlKey", s.trim())));
    }

    void pDev(LinearLayout p) {
        TextView av = tv("M", Color.WHITE, 34);
        av.setGravity(Gravity.CENTER);
        av.setTypeface(Typeface.DEFAULT_BOLD);
        av.setBackground(rr(AC, 42, 0));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(84), dp(84));
        ap.gravity = Gravity.CENTER_HORIZONTAL;
        ap.topMargin = dp(12);
        p.addView(av, ap);
        TextView n = tv("محمد السعيد علي", TX, 20);
        n.setGravity(Gravity.CENTER);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        n.setPadding(0, dp(10), 0, dp(2));
        p.addView(n);
        TextView s = note("مطوّر Mi_Max Ai");
        s.setGravity(Gravity.CENTER);
        p.addView(s);
        p.addView(row("MAIL", "للشكاوى والاقتراحات", "mmgg3yn@gmail.com", v -> {
            Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:mmgg3yn@gmail.com"));
            try { startActivity(i); } catch (Exception e) { toast("لا يوجد تطبيق بريد"); }
        }));
        p.addView(row("TT", "قناة تيك توك", "@xiaomi_12o", v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/@xiaomi_12o"))); }
            catch (Exception e) { toast("تعذر فتح الرابط"); }
        }));
        TextView ver = note("الإصدار 1.0.0");
        ver.setGravity(Gravity.CENTER);
        p.addView(ver);
    }
}
