/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import com.winlator.cmod.container.Shortcut;

import java.io.File;

/**
 * A game added as KiriKiri or NScripter for a stray .xp3 or .nsa in its Electron or NW.js folder keeps that engine in its
 * shortcut: 0.1.68 stopped seeing such a folder as KiriKiri when a game is added, not for a game added before. The
 * Electron game of the 26/10/2026 report (Xiaomi 2510DPC44G) still ran as KiriKiri on 0.1.68, with the
 * LC_ALL=ja_JP.UTF-8 KiriKiri gets, so Chromium ran with --lang=ja. At its start such a game gets the engine its folder
 * shows now; the Japanese locale goes too unless its profile or a kana name asks for it, and KiriKiri's screen size.
 * <p>A game of an engine the app learned to see after it was added also gets that engine: SRPG Studio (0.1.80), which
 * a player could only add by its exe or with agvn-profile.json, as a Windows game of no known engine.
 */
public final class AgvnEngineRecheck {
    private static final String TAG = "AGVN";
    private static volatile String note;

    private AgvnEngineRecheck() {}

    /** Before the game's settings are read (XServerDisplayActivity). Saves the shortcut when it changes; never throws. */
    public static void atLaunch(Shortcut s) {
        if (s == null) return;
        try {
            String stored = s.getExtra(AgvnGameImporter.EXTRA_ENGINE), dir = s.getExtra(AgvnGameImporter.EXTRA_GAME_DIR);
            String now = dir.isEmpty() ? null : engineNow(stored, new File(dir));
            if (now == null) return;
            String before = s.getExtra("lc_all");
            String locale = locale(before, AgvnQuality.profileOf(s), now, new File(dir).getName(), exeName(s.path));
            s.putExtra(AgvnGameImporter.EXTRA_ENGINE, now);
            s.putExtra("lc_all", locale);
            s.putExtra(AgvnKirikiri.EXTRA_GAME_SIZE, null);
            s.saveData();
            note = "Engine đã lưu " + (stored.isEmpty() ? "(không có)" : stored) + " không khớp thư mục game, nay là "
                    + now + (before.equals(locale == null ? "" : locale) ? "" : "; bỏ LC_ALL=" + before);
            Log.i(TAG, s.name + ": " + note);
        } catch (RuntimeException e) {
            Log.w(TAG, "engine not rechecked", e);
        }
    }

    /**
     * The engine to keep instead of {@code stored}: what {@code gameDir} shows when it is an Electron or NW.js one, or
     * SRPG Studio for a game stored with no known engine; else null.
     */
    static String engineNow(String stored, File gameDir) {
        String srpg = GameExeResolver.Engine.SRPG_STUDIO.name();
        boolean none = stored == null || stored.isEmpty() || GameExeResolver.Engine.UNKNOWN.name().equals(stored);
        if (none && GameExeResolver.detectEngine(gameDir).name().equals(srpg)) return srpg;
        boolean japanese = GameExeResolver.Engine.KIRIKIRI.name().equals(stored)
                || GameExeResolver.Engine.NSCRIPTER.name().equals(stored);
        if (!japanese || !GameExeResolver.chromiumShell(gameDir)) return null;
        String now = GameExeResolver.detectEngine(gameDir).name();
        return now.equals(stored) ? null : now;
    }

    /** LC_ALL to keep: {@code current}, unless it is the Japanese one the old engine gave and {@code engine} does not. */
    static String locale(String current, AgvnProfile profile, String engine, String folderName, String exe) {
        if (!AgvnLocale.JAPANESE.equals(current)) return current == null || current.isEmpty() ? null : current;
        return AgvnLocale.forGame(profile, GameExeResolver.Engine.valueOf(engine), folderName, exe);
    }

    /** "Game.exe" of "F:\Games\Game.exe" or a unix path. */
    static String exeName(String path) {
        String p = path == null ? "" : path.replace("\"", "");
        return p.substring(Math.max(p.lastIndexOf('\\'), p.lastIndexOf('/')) + 1);
    }

    static String takeNote() {
        String n = note;
        note = null;
        return n;
    }
}
