/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.regex.Pattern;

/**
 * Wine locale (LC_ALL) for an imported game. Japanese engines (KiriKiri, Siglus, NScripter, Wolf RPG) and games whose
 * folder or exe name has kana read Shift-JIS text and file names: without ja_JP they show garbled text or crash.
 * Unicode text (Vietnamese fan translations) still renders under ja_JP. A profile "locale" always wins; "" = default.
 */
public final class AgvnLocale {
    public static final String JAPANESE = "ja_JP.UTF-8";
    /** "ja_JP" or "ja_JP.UTF-8". Mirrored by tools/agvn/agvn_profile_lib.py. */
    static final Pattern VALID = Pattern.compile("^[a-z]{2}_[A-Z]{2}(\\.UTF-8)?$");

    private AgvnLocale() {}

    public static boolean isValid(String locale) {
        return locale == null || locale.isEmpty() || VALID.matcher(locale).matches();
    }

    /** LC_ALL to store on the shortcut, or null to keep the container's. */
    public static String forGame(AgvnProfile profile, GameExeResolver.Engine engine, String folderName, String exe) {
        if (profile != null && profile.locale != null) {
            String l = profile.locale.trim();
            if (l.isEmpty()) return null;
            return l.contains(".") ? l : l + ".UTF-8";
        }
        if (isJapaneseEngine(engine) || hasKana(folderName) || hasKana(exe)) return JAPANESE;
        return null;
    }

    static boolean isJapaneseEngine(GameExeResolver.Engine engine) {
        switch (engine) {
            case KIRIKIRI:
            case SIGLUS:
            case NSCRIPTER:
            case WOLFRPG:
                return true;
            default:
                return false;
        }
    }

    /** Hiragana, katakana or half-width katakana: only Japanese uses them (kanji alone could be Chinese). */
    static boolean hasKana(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '぀' && c <= 'ヿ') || (c >= 'ｦ' && c <= 'ﾝ')) return true;
        }
        return false;
    }
}
