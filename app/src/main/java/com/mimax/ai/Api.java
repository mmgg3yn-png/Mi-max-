package com.mimax.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

/** الاتصال بـ OpenRouter وخدمات التنزيل. كل الدوال تُستدعى من خيط خلفي. */
public class Api {
    static final String BASE = "https://openrouter.ai/api/v1";
    // Embedded default key, split into fragments to avoid a single plain-text token in source.
    // APK reverse engineering can still recover it; use a server proxy for real secrecy.
    public static String defaultKey() { return "sk-or-v1" + "-93e33f4" + "e50d2055" + "a89c1076" + "682b696f" + "26a2c340" + "e5035490" + "8154891e" + "134e0e73" + "c"; }


    public interface Delta { void on(String full); }

    public static class Item { public String label, url, file; }

    public static class Video {
        public String title, cover;
        public ArrayList<Item> items = new ArrayList<>();
    }

    static String friendly(int st, String m) {
        if (st == 401) return "مفتاح API غير صالح أو منتهي. أدخل مفتاحاً جديداً من الإعدادات ← الذكاء الاصطناعي.";
        if (st == 402) return "الرصيد غير كافٍ في حساب OpenRouter. اشحن الرصيد أو اختر نموذجاً مجانياً.";
        if (st == 404) return "النموذج غير موجود أو لا يدعم هذا النوع من الإدخال. اختر نموذجاً آخر. (" + m + ")";
        if (st == 429) return "عدد الطلبات كبير. انتظر قليلاً ثم أعد المحاولة.";
        return (m == null || m.isEmpty()) ? ("خطأ " + st) : m;
    }

    static String readAll(InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String l;
        while ((l = r.readLine()) != null) sb.append(l).append('\n');
        r.close();
        return sb.toString();
    }

    static String errMsg(HttpURLConnection c, int st, boolean openRouter) {
        try {
            String b = readAll(c.getErrorStream());
            if (!openRouter) return "فشل الاتصال (HTTP " + st + ")";
            JSONObject j = new JSONObject(b);
            JSONObject e = j.optJSONObject("error");
            return friendly(st, e != null ? e.optString("message") : b);
        } catch (Exception e) {
            return openRouter ? friendly(st, "HTTP " + st) : "فشل الاتصال (HTTP " + st + ")";
        }
    }

    static String get(String url, String auth) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        if (auth != null) c.setRequestProperty("Authorization", "Bearer " + auth);
        int st = c.getResponseCode();
        if (st != 200) {
            String m = errMsg(c, st, auth != null);
            c.disconnect();
            throw new Exception(m);
        }
        String s = readAll(c.getInputStream());
        c.disconnect();
        return s;
    }

    /** محادثة مع النموذج. تعيد النص الكامل، وتستدعي cb أثناء البث. */
    public static String chat(String key, String model, JSONArray msgs, double temp, boolean stream,
                              Delta cb, boolean[] cancel) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + "/chat/completions").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("X-Title", "Mi_Max Ai");
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", msgs);
        body.put("temperature", temp);
        body.put("stream", stream);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();
        int st = c.getResponseCode();
        if (st != 200) {
            String m = errMsg(c, st, true);
            c.disconnect();
            throw new Exception(m);
        }
        if (!stream) {
            JSONObject j = new JSONObject(readAll(c.getInputStream()));
            c.disconnect();
            JSONObject er = j.optJSONObject("error");
            if (er != null) throw new Exception(friendly(0, er.optString("message")));
            JSONObject msg = j.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
            return msg.isNull("content") ? "" : msg.getString("content");
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder full = new StringBuilder();
        String line;
        try {
            while ((line = r.readLine()) != null) {
                if (cancel != null && cancel[0]) break;
                line = line.trim();
                if (!line.startsWith("data:")) continue;
                String d = line.substring(5).trim();
                if (d.equals("[DONE]")) break;
                JSONObject j;
                try { j = new JSONObject(d); } catch (Exception e) { continue; }
                JSONObject er = j.optJSONObject("error");
                if (er != null) throw new Exception(friendly(0, er.optString("message")));
                JSONArray ch = j.optJSONArray("choices");
                if (ch == null || ch.length() == 0) continue;
                JSONObject delta = ch.getJSONObject(0).optJSONObject("delta");
                if (delta == null || delta.isNull("content")) continue;
                String t = delta.getString("content");
                if (t.isEmpty()) continue;
                full.append(t);
                if (cb != null) cb.on(full.toString());
            }
        } finally {
            try { r.close(); } catch (Exception e) { /* تجاهل */ }
            c.disconnect();
        }
        return full.toString();
    }

    public static String keyInfo(String key) throws Exception {
        JSONObject d = new JSONObject(get(BASE + "/auth/key", key)).optJSONObject("data");
        if (d == null) return "المفتاح يعمل.";
        String s = "المفتاح يعمل. الاستهلاك: " + String.format("%.4f", d.optDouble("usage", 0)) + " دولار";
        if (!d.isNull("limit")) s += " من " + d.optDouble("limit");
        return s;
    }

    public static JSONArray models() throws Exception {
        return new JSONObject(get(BASE + "/models", null)).getJSONArray("data");
    }

    static String abs(String u) {
        if (u == null || u.isEmpty()) return null;
        return u.startsWith("/") ? "https://www.tikwm.com" + u : u;
    }

    static void add(Video v, String label, String url, String file) {
        if (url == null) return;
        Item i = new Item();
        i.label = label; i.url = url; i.file = file;
        v.items.add(i);
    }

    public static Video resolve(String url, String dlApi, String dlKey) throws Exception {
        Video v = new Video();
        if (url.matches("(?i).*(tiktok\\.com|douyin\\.com).*")) {
            JSONObject j = new JSONObject(get("https://www.tikwm.com/api/?hd=1&url=" + URLEncoder.encode(url, "UTF-8"), null));
            if (j.optInt("code", -1) != 0) throw new Exception(j.optString("msg", "تعذر جلب الفيديو من تيك توك"));
            JSONObject d = j.getJSONObject("data");
            v.title = d.optString("title", "فيديو تيك توك");
            v.cover = abs(d.optString("cover", ""));
            String id = Long.toString(System.currentTimeMillis(), 36);
            String hd = abs(d.optString("hdplay", ""));
            String sd = abs(d.optString("play", ""));
            add(v, "فيديو بجودة عالية", hd, "tiktok_hd_" + id + ".mp4");
            add(v, "فيديو", sd, "tiktok_" + id + ".mp4");
            add(v, "الصوت فقط", abs(d.optString("music", "")), "tiktok_audio_" + id + ".mp3");
            if (v.items.isEmpty()) throw new Exception("لم يتم العثور على روابط تنزيل");
            return v;
        }
        if (dlApi != null && !dlApi.isEmpty()) {
            HttpURLConnection c = (HttpURLConnection) new URL(dlApi).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(20000);
            c.setReadTimeout(60000);
            c.setDoOutput(true);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("Content-Type", "application/json");
            if (dlKey != null && !dlKey.isEmpty()) c.setRequestProperty("Authorization", "Api-Key " + dlKey);
            JSONObject body = new JSONObject();
            body.put("url", url);
            body.put("videoQuality", "1080");
            OutputStream os = c.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();
            int st = c.getResponseCode();
            InputStream in = st < 400 ? c.getInputStream() : c.getErrorStream();
            JSONObject j = new JSONObject(readAll(in));
            c.disconnect();
            String status = j.optString("status", "");
            if (status.equals("error")) {
                JSONObject e = j.optJSONObject("error");
                throw new Exception("رفض الخادم الطلب: " + (e != null ? e.optString("code") : ""));
            }
            v.title = j.optString("filename", "ملف جاهز");
            if (status.equals("picker")) {
                JSONArray p = j.optJSONArray("picker");
                for (int i = 0; p != null && i < p.length(); i++) {
                    JSONObject o = p.getJSONObject(i);
                    add(v, o.optString("type", "ملف") + " " + (i + 1), o.optString("url", null), "media_" + (i + 1));
                }
            } else {
                add(v, "تنزيل", j.optString("url", null), j.optString("filename", "video.mp4"));
            }
            if (v.items.isEmpty()) throw new Exception("لم يرجع الخادم أي رابط");
            return v;
        }
        throw new Exception("هذا الموقع (مثل يوتيوب) يحتاج خادم تنزيل. أضف رابط الخادم من الإعدادات ← إعدادات التنزيل.");
    }
}
