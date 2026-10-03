/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Application;
import android.content.Context;
import android.os.Process;
import android.os.SystemClock;

import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * What "Tự sửa lỗi" leaves for the library, in files every process reads: the problem to ask about
 * (doctor-pending.properties) and a game to start again at once (doctor-relaunch.properties, "Mở lại game ngay").
 * Each is read once.
 */
final class AgvnDoctorStore {
    static final String PENDING = "agvn/doctor-pending.properties", RELAUNCH = "agvn/doctor-relaunch.properties";
    static final String PARAM = "param.";
    /** A restart asked for this long ago is stale: the player stayed in the game (said no to its own "quit?"). */
    static final long RELAUNCH_MS = 2 * 60_000L;
    /** How long the library waits for a "Chạy nhẹ" runner that asked for a restart to end its process. */
    private static final long RUNNER_END_MS = 12_000, RUNNER_END_STEP_MS = 200;

    /** A problem or a restart as stored: the game (container, shortcut file), the problem and its values. */
    static final class Entry {
        final int container;
        final String shortcut, problem;
        final long time;
        final Map<String, String> params = new LinkedHashMap<>();

        Entry(Properties p) {
            container = (int) number(p.getProperty("container"));
            shortcut = p.getProperty("shortcut", "");
            problem = p.getProperty("problem", "");
            time = number(p.getProperty("time"));
            for (String key : p.stringPropertyNames()) {
                if (key.startsWith(PARAM)) params.put(key.substring(PARAM.length()), p.getProperty(key));
            }
        }
    }

    private AgvnDoctorStore() {}

    static void ask(Context ctx, Shortcut s, AgvnProblemCatalog.Finding f) {
        Properties p = game(s);
        p.setProperty("problem", f.id());
        for (Map.Entry<String, String> e : f.params.entrySet()) p.setProperty(PARAM + e.getKey(), e.getValue());
        write(ctx, PENDING, p, "AGVN Player: a problem to ask the player about");
    }

    /** {@code ctx}'s process asks: a "Chạy nhẹ" runner's own one must end before the game can start there again. */
    static void relaunch(Context ctx, Shortcut s) {
        Properties p = game(s);
        p.setProperty("pid", String.valueOf(Process.myPid()));
        p.setProperty("process", Application.getProcessName());
        write(ctx, RELAUNCH, p, "AGVN Player: start this game again");
    }

    /**
     * Off the UI thread, before a restart: waits while the runner that asked for it is still ending (SDL and Python
     * cannot start twice in one process; a new game started now would end with the old process).
     */
    static void awaitRelaunch(Context ctx) {
        Properties p = AgvnPropsFile.load(new File(ctx.getFilesDir(), RELAUNCH));
        int pid = (int) number(p.getProperty("pid"));
        if (pid == Process.myPid()) return; // a Windows or HTML game: it ran in this very process
        String process = p.getProperty("process", "");
        for (long waited = 0; waited < RUNNER_END_MS && AgvnLightDoctor.alive(ctx, pid, process); waited += RUNNER_END_STEP_MS)
            SystemClock.sleep(RUNNER_END_STEP_MS);
    }

    static boolean hasPending(Context ctx) {
        return new File(ctx.getFilesDir(), PENDING).isFile();
    }

    /** The stored entry of {@code name} (PENDING or RELAUNCH), removed; null when there is none. */
    static Entry take(Context ctx, String name) {
        File f = new File(ctx.getFilesDir(), name);
        if (!f.isFile()) return null;
        Properties p = AgvnPropsFile.load(f);
        f.delete();
        return p.isEmpty() ? null : new Entry(p);
    }

    private static Properties game(Shortcut s) {
        Properties p = new Properties();
        p.setProperty("container", String.valueOf(s.container.id));
        p.setProperty("shortcut", s.file.getPath());
        p.setProperty("time", String.valueOf(System.currentTimeMillis()));
        return p;
    }

    private static void write(Context ctx, String name, Properties p, String comment) {
        File f = new File(ctx.getFilesDir(), name);
        File dir = f.getParentFile();
        if (dir != null) dir.mkdirs();
        AgvnPropsFile.store(p, f, comment);
    }

    private static long number(String value) {
        try {
            return value != null ? Long.parseLong(value.trim()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
