/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

import java.io.File;
import java.util.Properties;

/**
 * The "Chạy nhẹ" game playing now (Ren'Py, RPG Maker XP/VX/VX Ace, HTML), in files/agvn/light-session.properties,
 * which the runner's own process writes and the library reads ({@link AgvnLightDoctor}): which game, when, in which
 * process, whether its first screen came up, whether it ended normally, and an error it reported. One at a time, as
 * one light game runs at a time.
 */
public final class AgvnLightSession {
    static final String FILE = "agvn/light-session.properties";
    static final String SHORTCUT = "shortcut", CONTAINER = "container", RUNNER = "runner", NAME = "name", GAME_DIR = "gameDir",
            START = "start", PROCESS = "process", PID = "pid", STARTED = "started", ENDED = "ended", SHOWN = "shown",
            ERROR = "error", PAGE_CRASH = "pageCrash";

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
    static void shown(Context ctx, String error) {
        Properties p = read(ctx);
        if (p.isEmpty()) return;
        p.setProperty(SHOWN, "1");
        p.setProperty(ERROR, cut(error));
        store(ctx, p);
    }

    /** An error the game reported while playing (the first one counts). */
    static void error(Context ctx, String error) {
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

    static void clear(Context ctx) {
        file(ctx).delete();
    }

    private static void set(Context ctx, String key, String value) {
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
