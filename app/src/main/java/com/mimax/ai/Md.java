package com.mimax.ai;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** تنسيق Markdown مبسّط: كود، عريض، عناوين، قوائم. */
public class Md {
    static final Pattern INLINE = Pattern.compile("\\*\\*([^*\\n]+)\\*\\*|`([^`\\n]+)`");

    public static CharSequence render(String s, int codeBg) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        String[] lines = s.split("\n", -1);
        boolean code = false;
        for (int i = 0; i < lines.length; i++) {
            String ln = lines[i];
            if (ln.trim().startsWith("```")) { code = !code; continue; }
            int start = out.length();
            if (code) {
                out.append(ln);
                span(out, new TypefaceSpan("monospace"), start);
                span(out, new BackgroundColorSpan(codeBg), start);
                span(out, new RelativeSizeSpan(0.9f), start);
            } else {
                String t = ln;
                boolean head = false;
                if (t.matches("^#{1,4}\\s+.*")) {
                    t = t.replaceFirst("^#{1,4}\\s+", "");
                    head = true;
                } else if (t.matches("^\\s*[-*•]\\s+.*")) {
                    t = "• " + t.replaceFirst("^\\s*[-*•]\\s+", "");
                }
                inline(out, t, codeBg);
                if (head) {
                    span(out, new StyleSpan(Typeface.BOLD), start);
                    span(out, new RelativeSizeSpan(1.12f), start);
                }
            }
            if (i < lines.length - 1) out.append('\n');
        }
        return out;
    }

    static void span(SpannableStringBuilder sb, Object what, int start) {
        if (sb.length() > start) sb.setSpan(what, start, sb.length(), 0);
    }

    static void inline(SpannableStringBuilder out, String t, int codeBg) {
        Matcher m = INLINE.matcher(t);
        int pos = 0;
        while (m.find()) {
            out.append(t, pos, m.start());
            int st = out.length();
            if (m.group(1) != null) {
                out.append(m.group(1));
                span(out, new StyleSpan(Typeface.BOLD), st);
            } else {
                out.append(m.group(2));
                span(out, new TypefaceSpan("monospace"), st);
                span(out, new BackgroundColorSpan(codeBg), st);
            }
            pos = m.end();
        }
        out.append(t, pos, t.length());
    }
}
