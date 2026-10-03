/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * What "Gửi nhật ký" adds so the player need not quit a game first: each session still running as it is now
 * ({@link AgvnSessionLog#snapshot}), the app's own logcat, where the "Chạy nhẹ" engines write (mkxp-z, SDL, Ren'Py,
 * the HTML games' messages), the phone's facts ({@link AgvnDeviceReport}) and the "Chạy nhẹ" Ren'Py logs
 * (AGVN-Player/renpy/&lt;game&gt;). Off the UI thread.
 */
final class AgvnLiveLogs {
    private static final String TAG = "AGVN";
    static final String APP = "app", RENPY = "renpy", LOGCAT = "logcat.txt", DEVICE = "thiet-bi.txt";
    private static final int LOGCAT_LINES = 20000;
    private static final long LOGCAT_MAX_BYTES = 4L << 20;

    private AgvnLiveLogs() {}

    /**
     * Fills {@code into}: "&lt;session&gt;/" for each running session, "app/logcat.txt", "app/thiet-bi.txt" and "renpy/"
     * for {@code gameDir}.
     */
    static void collect(Context context, File[] sessions, File gameDir, File into) {
        if (sessions != null) {
            for (File session : sessions) {
                if (!new File(session, AgvnSessionLog.RUNNING).isFile()) continue;
                File out = new File(into, session.getName());
                if (out.mkdirs()) AgvnSessionLog.snapshot(context, session, out);
            }
        }
        File app = new File(into, APP);
        if (app.mkdirs()) {
            logcat(new File(app, LOGCAT));
            write(new File(app, DEVICE), AgvnDeviceReport.text(context));
        }
        if (gameDir == null) return;
        File renpyLogs = AgvnRenpyGame.publicDir(AgvnSessionLog.root().getParentFile(), gameDir);
        List<File> files = new ArrayList<>();
        for (String name : new String[]{"log.txt", "traceback.txt", "errors.txt"}) files.add(new File(renpyLogs, name));
        File renpy = new File(into, RENPY);
        if (renpy.mkdirs()) AgvnEngineLogs.copy(files, renpy, 0);
    }

    /**
     * The newest {@value #LOGCAT_LINES} lines of logcat. An app reads only its own processes' lines: the game screens,
     * the ":renpy" and ":rgss" engines and the app, nothing of other apps.
     */
    static void logcat(File out) {
        Process process = null;
        try {
            process = new ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", String.valueOf(LOGCAT_LINES))
                    .redirectErrorStream(true).start();
            try (InputStream in = process.getInputStream(); OutputStream o = new FileOutputStream(out)) {
                byte[] buf = new byte[64 * 1024];
                long written = 0;
                for (int n; (n = in.read(buf)) > 0; ) {
                    int keep = (int) Math.min(n, LOGCAT_MAX_BYTES - written);
                    if (keep > 0) o.write(buf, 0, keep);
                    written += keep;
                }
            }
            process.waitFor(10, TimeUnit.SECONDS);
        } catch (IOException e) {
            Log.w(TAG, "logcat not read", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static void write(File f, String text) {
        try {
            Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "not written: " + f, e);
        }
    }

    /** Deletes {@code dir} and everything in it (the temporary folder of one "Gửi nhật ký"). */
    static void delete(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) delete(f);
        if (!dir.delete() && dir.exists()) Log.w(TAG, "not deleted: " + dir);
    }
}
