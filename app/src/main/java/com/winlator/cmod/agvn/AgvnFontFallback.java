/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Japanese games draw text with Windows fonts the environment lacks (ＭＳ ゴシック, MS Mincho, Meiryo…). Wine then takes
 * any font with the Japanese character set, sometimes a bitmap one, and engines that cache glyph outlines (WOLF RPG,
 * DxLib) stop with "次の文字がキャッシュできませんでした". GameHub has these fonts through Proton. Here the missing names
 * point to the bundled Source Han Sans CN (kana, kanji and Vietnamese letters) in Wine's font replacements in
 * user.reg. Only missing lines are added, before Wine starts; a name the player or a real font already has is kept.
 */
public final class AgvnFontFallback {
    private static final String TAG = "AGVN";
    static final String KEY = "[Software\\\\Wine\\\\Fonts\\\\Replacements]";
    static final String TARGET = "Source Han Sans CN";
    static final String[] NAMES = {
            "MS Gothic", "ＭＳ ゴシック", "MS PGothic", "ＭＳ Ｐゴシック", "MS UI Gothic",
            "MS Mincho", "ＭＳ 明朝", "MS PMincho", "ＭＳ Ｐ明朝",
            "Meiryo", "メイリオ", "Meiryo UI", "Yu Gothic", "游ゴシック", "Yu Mincho", "游明朝"};

    private AgvnFontFallback() {}

    public static void apply(File userReg) {
        if (!userReg.isFile()) return;
        try {
            // ISO-8859-1 keeps every byte of the file as it is; the lines we add are plain ASCII
            String text = new String(Files.readAllBytes(userReg.toPath()), StandardCharsets.ISO_8859_1);
            String updated = withReplacements(text);
            if (updated == null) return;
            File tmp = new File(userReg.getPath() + ".agvn");
            Files.write(tmp.toPath(), updated.getBytes(StandardCharsets.ISO_8859_1));
            Files.move(tmp.toPath(), userReg.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Log.w(TAG, "font replacements not written", e);
        }
    }

    /** The registry text with the missing replacement lines added, or null when none is missing. */
    static String withReplacements(String text) {
        int header = text.indexOf("\n" + KEY);
        int bodyStart = header < 0 ? -1 : text.indexOf('\n', header + 1);
        int next = bodyStart < 0 ? -1 : text.indexOf("\n[", bodyStart);
        String block = header < 0 ? "" : text.substring(header, next < 0 ? text.length() : next).toLowerCase(Locale.ROOT);

        StringBuilder add = new StringBuilder();
        for (String name : NAMES) {
            String quoted = "\"" + escape(name) + "\"=";
            if (!block.contains("\n" + quoted.toLowerCase(Locale.ROOT))) add.append(quoted).append('"').append(TARGET).append("\"\n");
        }
        if (add.length() == 0) return null;
        if (header < 0) return text + (text.endsWith("\n") ? "" : "\n") + "\n" + KEY + " 1700000000\n" + add;
        if (bodyStart < 0) return text + "\n" + add;
        int at = next < 0 ? text.length() : next;
        String before = text.substring(0, at);
        return before + (before.endsWith("\n") ? "" : "\n") + add + text.substring(at);
    }

    /** Wine's registry string form: backslash and quote escaped, anything past ASCII as \x and 4 hex digits. */
    static String escape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '"') out.append('\\').append(c);
            else if (c < 0x20 || c > 0x7e) out.append(String.format(Locale.ROOT, "\\x%04x", (int) c));
            else out.append(c);
        }
        return out.toString();
    }
}
