/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A crash a Unity game's crash handler caught, seen while the game plays ({@link AgvnUnityCrash}): the game is dead
 * but its process can stay, its last picture frozen, as Become A Vtuber's did five times. Every {@link #POLL_MS} the
 * game's Unity log is looked at (a log not rewritten since the watch began is the last session's); once it holds the
 * crash, su-kien.txt says where, a bar says the game stopped, and the game is closed {@link #CLOSE_MS} later, counted
 * while the player is in the app, so "Tự sửa lỗi" asks what to change. Never in the game's way: an error ends it.
 */
public final class AgvnUnityCrashWatch {
    private static final String TAG = "AGVN";
    static final long POLL_MS = 3000, CLOSE_MS = 10_000, TICK_MS = 1000;
    /** "Crash!!!" without the rest this long: Unity stopped writing the crash midway; the game is dead all the same. */
    static final long UNFINISHED_MS = 10_000;

    private final XServerDisplayActivity activity;
    private final List<File> logs = new ArrayList<>();
    /** When each log was last written as the watch began (0: none). */
    private final Map<File, Long> before = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "AgvnUnityCrashWatch");
        t.setDaemon(true);
        return t;
    });
    private long lastSize = -1, crashSeenMs, leftMs = CLOSE_MS;

    private AgvnUnityCrashWatch(XServerDisplayActivity activity, Shortcut s) {
        this.activity = activity;
        for (File f : AgvnEngineLogs.of(s)) {
            String name = f.getName();
            if (!name.equals("Player.log") && !name.equals("output_log.txt")) continue;
            logs.add(f);
            before.put(f, f.lastModified());
        }
    }

    /** Watches the game of {@code activity} when it is a Unity game; call before Wine starts it. */
    public static void start(XServerDisplayActivity activity) {
        Shortcut s = activity.agvnShortcut();
        if (s == null || !"UNITY".equals(s.getExtra(AgvnGameImporter.EXTRA_ENGINE))) return;
        AgvnUnityCrashWatch watch = new AgvnUnityCrashWatch(activity, s);
        if (watch.logs.isEmpty()) return;
        watch.timer.scheduleWithFixedDelay(watch::poll, POLL_MS, POLL_MS, TimeUnit.MILLISECONDS);
    }

    private void poll() {
        try {
            if (activity.isFinishing() || activity.isDestroyed()) {
                timer.shutdown();
                return;
            }
            File log = current();
            if (log == null) return;
            long size = log.length();
            boolean grew = size != lastSize;
            lastSize = size;
            if (!grew && crashSeenMs == 0) return;
            List<String> lines = AgvnUnityCrashFiles.tail(log);
            if (!AgvnUnityCrash.crashed(lines)) return;
            long now = SystemClock.uptimeMillis();
            if (crashSeenMs == 0) crashSeenMs = now;
            if (!AgvnUnityCrash.complete(lines) && now - crashSeenMs < UNFINISHED_MS) return; // still writing it
            timer.shutdown();
            String crash = AgvnUnityCrash.describe(lines), where = AgvnUnityCrash.where(lines);
            handler.post(() -> found(crash, where));
        } catch (IOException e) {
            Log.w(TAG, "Unity log not read", e); // read again at the next poll
        } catch (RuntimeException e) {
            Log.w(TAG, "Unity crash watch failed", e);
            timer.shutdown();
        }
    }

    /** The log Unity writes in this session: rewritten since the watch began. */
    private File current() {
        for (File f : logs) if (f.isFile() && f.lastModified() != before.get(f)) return f;
        return null;
    }

    private void found(String crash, String where) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        AgvnSessionTrack.engineCrashed(crash);
        AgvnSessionLog.event(crash + " (Unity tự bắt lỗi: Wine vẫn chạy nhưng game đã dừng); app đóng game sau "
                + CLOSE_MS / 1000 + " giây, tính lúc người chơi ở trong app");
        Log.i(TAG, "Unity caught a crash: " + crash);
        AgvnWarningBar.show(activity, activity.getString(R.string.agvn_unity_crash_title),
                activity.getString(R.string.agvn_unity_crash_detail, where != null ? where : "?", CLOSE_MS / 1000),
                new AgvnWarningBar.Choice(R.string.agvn_unity_crash_close, this::close));
        handler.postDelayed(this::countDown, TICK_MS);
    }

    /** Closes the game once {@link #CLOSE_MS} passed with the player in the app: a game the app stopped waits. */
    private void countDown() {
        if (activity.isFinishing() || activity.isDestroyed() || leftMs <= 0) return;
        if (!AgvnGamePause.isPaused()) leftMs -= TICK_MS;
        if (leftMs > 0) handler.postDelayed(this::countDown, TICK_MS);
        else close();
    }

    private void close() {
        if (leftMs < 0) return; // closed already
        leftMs = -1;
        handler.removeCallbacksAndMessages(null);
        AgvnSessionLog.event("App đóng game đã crash");
        activity.agvnExit();
    }
}
