package com.mimax.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** التخزين المحلي: الإعدادات، الحسابات، المحادثات، الذاكرة. */
public class Store {

    public static class Msg {
        public String r;      // user | assistant
        public String t;
        public Bitmap img;    // صورة مصغرة تبقى في الجلسة فقط
        public Msg(String r, String t) { this.r = r; this.t = t; }
    }

    public static class Chat {
        public String id = newId();
        public String title = "محادثة جديدة";
        public long ts = System.currentTimeMillis();
        public ArrayList<Msg> msgs = new ArrayList<>();
    }

    static SharedPreferences sp;

    public static void init(Context c) {
        if (sp == null) sp = c.getApplicationContext().getSharedPreferences("mm", Context.MODE_PRIVATE);
    }

    public static String newId() {
        return Long.toString(System.currentTimeMillis(), 36) + Integer.toString(new Random().nextInt(46656), 36);
    }

    public static String get(String k, String d) { return sp.getString(k, d); }
    public static void put(String k, String v) { sp.edit().putString(k, v).apply(); }
    public static boolean flag(String k, boolean d) { return sp.getBoolean(k, d); }
    public static void setFlag(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }

    // ---- الإعدادات ----
    public static String key() {
        String personal = get("key", "").trim();
        return personal.isEmpty() ? Api.defaultKey() : personal;
    }
    public static String model() { return get("model", "openai/gpt-4o-mini").trim(); }
    public static String audioModel() { return get("audioModel", "google/gemini-2.5-flash").trim(); }
    public static double temp() {
        try { return Double.parseDouble(get("temp", "0.7")); } catch (Exception e) { return 0.7; }
    }

    // ---- الحساب ----
    public static String session() { return get("session", ""); }
    public static String ns() { String s = session(); return s.isEmpty() ? "guest" : s; }

    static String hash(String pw, String salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pw.toCharArray(), salt.getBytes("UTF-8"), 20000, 256);
        byte[] h = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
        StringBuilder sb = new StringBuilder();
        for (byte b : h) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static JSONObject accts() {
        try { return new JSONObject(get("accts", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    public static void register(String u, String p) throws Exception {
        u = u.trim().toLowerCase();
        if (!u.matches("[\\p{L}\\p{N}_.]{3,24}")) throw new Exception("اسم المستخدم 3–24 حرفاً (حروف أو أرقام أو _ .)");
        if (p.length() < 6) throw new Exception("كلمة المرور 6 أحرف على الأقل");
        JSONObject a = accts();
        if (a.has(u)) throw new Exception("اسم المستخدم مستخدم من قبل");
        String salt = newId() + newId();
        JSONObject o = new JSONObject();
        o.put("salt", salt);
        o.put("hash", hash(p, salt));
        a.put(u, o);
        put("accts", a.toString());
        if (session().isEmpty()) { // نقل بيانات الضيف إلى الحساب الجديد
            for (String k : new String[]{"chats", "mem", "prompt"}) {
                String v = get(k + ":guest", null);
                if (v != null) put(k + ":" + u, v);
            }
        }
        put("session", u);
    }

    public static void login(String u, String p) throws Exception {
        u = u.trim().toLowerCase();
        JSONObject o = accts().optJSONObject(u);
        if (o == null || !o.getString("hash").equals(hash(p, o.getString("salt"))))
            throw new Exception("اسم المستخدم أو كلمة المرور غير صحيحة");
        put("session", u);
    }

    public static void logout() { put("session", ""); }

    public static void deleteAccount() {
        String u = session();
        JSONObject a = accts();
        a.remove(u);
        SharedPreferences.Editor e = sp.edit();
        e.putString("accts", a.toString());
        for (String k : new String[]{"chats", "mem", "prompt"}) e.remove(k + ":" + u);
        e.putString("session", "");
        e.apply();
    }

    // ---- المحادثات ----
    public static ArrayList<Chat> loadChats() {
        ArrayList<Chat> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(get("chats:" + ns(), "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Chat c = new Chat();
                c.id = o.getString("id");
                c.title = o.getString("title");
                c.ts = o.getLong("ts");
                JSONArray m = o.getJSONArray("msgs");
                for (int j = 0; j < m.length(); j++) {
                    JSONObject mo = m.getJSONObject(j);
                    c.msgs.add(new Msg(mo.getString("r"), mo.getString("t")));
                }
                out.add(c);
            }
        } catch (Exception e) { /* تجاهل */ }
        return out;
    }

    public static void saveChats(List<Chat> l) {
        try {
            JSONArray a = new JSONArray();
            for (Chat c : l) {
                JSONObject o = new JSONObject();
                o.put("id", c.id);
                o.put("title", c.title);
                o.put("ts", c.ts);
                JSONArray m = new JSONArray();
                for (Msg x : c.msgs) {
                    JSONObject mo = new JSONObject();
                    mo.put("r", x.r);
                    mo.put("t", x.t);
                    m.put(mo);
                }
                o.put("msgs", m);
                a.put(o);
            }
            put("chats:" + ns(), a.toString());
        } catch (Exception e) { /* تجاهل */ }
    }

    // ---- الذاكرة ----
    public static ArrayList<String> loadMem() {
        ArrayList<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(get("mem:" + ns(), "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception e) { /* تجاهل */ }
        return out;
    }

    public static void saveMem(List<String> l) {
        JSONArray a = new JSONArray();
        for (String s : l) a.put(s);
        put("mem:" + ns(), a.toString());
    }

    public static String prompt() { return get("prompt:" + ns(), ""); }
    public static void setPrompt(String p) { put("prompt:" + ns(), p); }
}
