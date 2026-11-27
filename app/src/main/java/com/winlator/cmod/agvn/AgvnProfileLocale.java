/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.FileUtils;

import java.io.File;

/**
 * The "locale" of agvn-profile.json, followed after the game was added. "Thêm game" wrote the game's LC_ALL once, so a
 * game maker who then set "locale": "zh_CN" in the profile still saw LC_ALL=ja_JP in moi-truong.txt, and had to use
 * "env": {"LC_ALL": ...} instead (a KiriKiri 2.30 game of a Chinese build, POCO, 27/11/2026). At each start the profile
 * edited last is read again with its env ({@link AgvnProfileEnv#atLaunch}): the locale it gives now replaces the one it
 * gave before, and one the player or a Doctor fix chose since stays.
 *
 * <p>What it gave before is kept on the game ({@link #EXTRA}). A game added before that record was kept has none: what
 * the import took is then read from the profile's other file (the game folder's and the app's copy said the same at the
 * import, and one was edited since); with no other file, the game's LC_ALL changes only while it is still the one the
 * app gives a game of its kind by itself, which nobody chose. Pure Java but {@link #atLaunch}.
 */
final class AgvnProfileLocale {
    /** Shortcut extra: what the profile's "locale" said when LC_ALL was last written from it. */
    static final String EXTRA = "agvnProfileLocale";
    /** Recorded for a profile without "locale", and for "locale": "" (the container's): no locale has a bracket. */
    static final String NONE = "()", CONTAINER = "(container)";

    private AgvnProfileLocale() {}

    /** What {@link #EXTRA} keeps for {@code locale}, a profile's "locale" (null: it has none). */
    static String record(String locale) {
        if (locale == null) return NONE;
        String l = locale.trim();
        return l.isEmpty() ? CONTAINER : l.contains(".") ? l : l + ".UTF-8";
    }

    /** The LC_ALL a game gets for {@code record}: {@code own}, what the app gives it by itself, for no "locale". */
    static String lcAll(String record, String own) {
        return NONE.equals(record) ? own : CONTAINER.equals(record) ? "" : record;
    }

    /**
     * The game's LC_ALL ("" for the container's) after the profile's "locale" went from {@code before} to {@code now}
     * (records): {@code now}'s while the game still has {@code before}'s, else {@code current}, which someone chose.
     */
    static String apply(String current, String before, String now, String own) {
        return current.equals(lcAll(before, own)) ? lcAll(now, own) : current;
    }

    /**
     * {@link #apply} for a game with no record: {@code other} is the record of the profile's other file, what the
     * import took; null (no such file): only an LC_ALL that is still the app's own gives way.
     */
    static String unrecorded(String current, String other, String now, String own) {
        return apply(current, other != null ? other : NONE, now, own);
    }

    /**
     * Before the game's settings are read: its LC_ALL follows the profile {@code p}, edited last; {@code other} is its
     * other file, or null. Null when nothing was written; else what to tell ("" for a record only). The caller saves.
     */
    static String atLaunch(Shortcut s, AgvnProfile p, File other, File gameDir) {
        if (!AgvnLocale.isValid(p.locale)) return null; // "Thêm game" says why; the game keeps its own
        String now = record(p.locale), recorded = s.getExtra(EXTRA), current = s.getExtra("lc_all");
        if (now.equals(recorded)) return null;
        File exe = AgvnHtmlGame.exeIn(s.file);
        String own = AgvnLocale.forGame(null, AgvnRunMode.engine(s.getExtra(AgvnGameImporter.EXTRA_ENGINE)),
                gameDir != null ? gameDir.getName() : null, exe != null ? exe.getName() : null);
        if (own == null) own = "";
        String after = recorded.isEmpty() ? unrecorded(current, recordOf(other), now, own)
                : apply(current, recorded, now, own);
        s.putExtra(EXTRA, now);
        if (after.equals(current)) return "";
        s.putExtra("lc_all", after.isEmpty() ? null : after);
        return "đổi locale từ khi thêm game, nay LC_ALL=" + (after.isEmpty() ? "(của container)" : after);
    }

    /** The record of profile {@code file}'s "locale"; null when it is not there or cannot be read. */
    private static String recordOf(File file) {
        try {
            if (file == null || !file.isFile()) return null;
            String locale = AgvnProfile.parse(FileUtils.readString(file)).locale;
            return AgvnLocale.isValid(locale) ? record(locale) : null;
        } catch (AgvnProfileException | RuntimeException e) {
            return null;
        }
    }
}
