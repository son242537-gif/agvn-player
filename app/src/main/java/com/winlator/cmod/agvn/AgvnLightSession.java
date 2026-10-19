/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The "Chạy nhẹ" game playing now (Ren'Py, RPG Maker XP/VX/VX Ace, HTML), in files/agvn/light-session.properties,
 * which the runner's own process writes and the library reads ({@link AgvnLightDoctor}): which game, when, in which
 * process, whether its first screen came up, whether it ended normally, and an error it reported. One at a time, as
 * one light game runs at a time. An HTML game's page also leaves its first warnings and errors, and the files it asked
 * for that its folder lacks: they were only in logcat, which a HONOR phone sends encrypted, so the log of Vệ Tinh Đức
 * (RPG Maker web build, no text on screen, HONOR AAK-AN00, 16/10/2026) could not say why. Once the library has read a
 * session that is over, it stays as the last one ({@link #LAST}), so "Gửi nhật ký" after the game still has it.
 */
public final class AgvnLightSession {
    static final String FILE = "agvn/light-session.properties", LAST = "agvn/light-session-last.properties";
    static final String SHORTCUT = "shortcut", CONTAINER = "container", RUNNER = "runner", NAME = "name", GAME_DIR = "gameDir",
            START = "start", PROCESS = "process", PID = "pid", STARTED = "started", ENDED = "ended", SHOWN = "shown",
            ERROR = "error", PAGE_CRASH = "pageCrash", STAND_IN = "standIn", MISSING = "missing", PAGE = "page";
    /** Pictures named as shown empty, at most: enough for a game's system pictures past its first missing ones. */
    static final int STAND_INS = 12;
    /** Missing files, and page messages, kept at most. */
    static final int LISTED = 8;

    private AgvnLightSession() {}

    /** A runner starts (its activity's onCreate), with the launch intent's game. */
    static void begin(Activity a, String runner, File gameDir) {
        Intent intent = a.getIntent();
        String path = intent.getStringExtra("shortcut_path");
        int container = intent.getIntExtra("container_id", 0);
        if (container == 0 && path != null) container = AgvnHtmlGame.containerIdIn(new File(path));
        Properties p = new Properties();
        if (path != null) p.setProperty(SHORTCUT, path);
        p.setProperty(CONTAINER, String.valueOf(container));
        p.setProperty(RUNNER, runner);
        String name = intent.getStringExtra("shortcut_name");
        if (name != null) p.setProperty(NAME, name);
        if (gameDir != null) p.setProperty(GAME_DIR, gameDir.getPath());
        p.setProperty(START, String.valueOf(System.currentTimeMillis()));
        p.setProperty(PROCESS, Application.getProcessName());
        p.setProperty(PID, String.valueOf(Process.myPid()));
        store(a, p);
    }

    /** The game's first screen is up. */
    static void started(Context ctx) {
        set(ctx, STARTED, "1");
    }

    /** The game ended normally (the player quit, or the game closed itself). */
    static void ended(Context ctx) {
        set(ctx, ENDED, "1");
    }

    /** The player swiped AGVN away while this process ran the game: it ended as they chose. */
    static void removedByPlayer(Context ctx) {
        if (String.valueOf(Process.myPid()).equals(read(ctx).getProperty(PID))) ended(ctx);
    }

    /** The runner showed its own error, with its fixes ("Chạy bằng Windows", the RTP): nothing to ask again. */
    static synchronized void shown(Context ctx, String error) {
        Properties p = read(ctx);
        if (p.isEmpty()) return;
        p.setProperty(SHOWN, "1");
        p.setProperty(ERROR, cut(error));
        store(ctx, p);
    }

    /** An error the game reported while playing (the first one counts). */
    static synchronized void error(Context ctx, String error) {
        Properties p = read(ctx);
        if (p.isEmpty() || p.containsKey(ERROR)) return;
        p.setProperty(ERROR, cut(error));
        store(ctx, p);
    }

    /** The HTML game's page process died (crash, or Android took its memory). */
    static void pageCrashed(Context ctx) {
        set(ctx, PAGE_CRASH, "1");
    }

    static Properties read(Context ctx) {
        return AgvnPropsFile.load(file(ctx));
    }

    /** The library has read the session, which is over: it is kept as the last one, in place of the one before. */
    static void clear(Context ctx) {
        File f = file(ctx);
        if (!f.renameTo(lastFile(ctx))) f.delete();
    }

    /** The last session the library read ({@link #clear}), which may be missing. */
    static File lastFile(Context ctx) {
        return new File(ctx.getFilesDir(), LAST);
    }

    /** The game a session file names ({@link #NAME}), or null. */
    static String nameIn(File f) {
        return f.isFile() ? AgvnPropsFile.load(f).getProperty(NAME) : null;
    }

    /**
     * For the runner of {@code ctx}, the first {@code max} different items in the list under {@code key} ("a | b"):
     * the pictures of an HTML game shown empty ({@link #STAND_IN}, AgvnHtmlStandIn), the files its page asked for that
     * are not there ({@link #MISSING}), its page's warnings and errors ({@link #PAGE}). Each item is also kept in memory,
     * so a file asked for again and again, or a flood of messages, does not read the session's file each time.
     */
    static Consumer<String> keeper(Context ctx, String key, int max) {
        return firstDifferent(max, item -> add(ctx, key, item, max));
    }

    /** {@code sink}, told each item the first time it comes, up to {@code max} different items; null is left out. */
    static Consumer<String> firstDifferent(int max, Consumer<String> sink) {
        Set<String> seen = Collections.synchronizedSet(new HashSet<>());
        return item -> {
            if (item != null && seen.size() < max && seen.add(item)) sink.accept(item);
        };
    }

    private static synchronized void add(Context ctx, String key, String item, int max) {
        Properties p = read(ctx);
        String list = p.isEmpty() ? null : added(p.getProperty(key, ""), cut(item).replace(" | ", " / "), max);
        if (list == null) return;
        p.setProperty(key, list);
        store(ctx, p);
    }

    /** {@code list} ("a | b") with {@code item} at its end; null when it holds {@code item} already, or {@code max} items. */
    static String added(String list, String item, int max) {
        if (list.isEmpty()) return item;
        String[] items = list.split(" \\| ");
        return items.length >= max || Arrays.asList(items).contains(item) ? null : list + " | " + item;
    }

    private static synchronized void set(Context ctx, String key, String value) {
        Properties p = read(ctx);
        if (p.isEmpty()) return;
        p.setProperty(key, value);
        store(ctx, p);
    }

    private static void store(Context ctx, Properties p) {
        File f = file(ctx);
        File dir = f.getParentFile();
        if (dir != null) dir.mkdirs();
        AgvnPropsFile.store(p, f, "AGVN Player: the \"Chạy nhẹ\" game playing now");
    }

    private static File file(Context ctx) {
        return new File(ctx.getFilesDir(), FILE);
    }

    private static String cut(String s) {
        String one = s == null ? "" : s.trim().replace('\n', ' ');
        return one.length() > 300 ? one.substring(0, 300) + "…" : one;
    }
}
