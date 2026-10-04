/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.os.SystemClock;
import android.util.Log;

import com.winlator.cmod.R;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Watches memory every 5 s while a game is on screen ({@link AgvnMemoryRules} decides). On a warning it shows a bar
 * over the game with "Thoát game an toàn", so the player can save and leave before Android kills the whole app.
 * Warnings and the session's peaks go to the session log.
 */
public final class AgvnMemoryWatch {
    private static final String TAG = "AGVN";
    private static final long POLL_S = 5, PEAKS_EVERY_MS = 60_000;
    private static volatile long lastInputMs = SystemClock.uptimeMillis();
    private static volatile long playerInputMs;

    private final Activity activity;
    private final Runnable exitGame;
    private final AgvnMemoryRules rules = new AgvnMemoryRules(AgvnMemoryProbe.totalMb());
    private final long startedMs = SystemClock.uptimeMillis();
    private ScheduledExecutorService poller;
    private long minFreeMb = Long.MAX_VALUE, maxRssMb, maxDmabufMb, peaksWrittenMs;

    public AgvnMemoryWatch(Activity activity, Runnable exitGame) {
        this.activity = activity;
        this.exitGame = exitGame;
    }

    /** Called on every touch, key or controller input. */
    public static void touched() {
        lastInputMs = SystemClock.uptimeMillis();
        playerInputMs = lastInputMs;
    }

    /** SystemClock.uptimeMillis() of the player's last touch, key or controller input; 0 before the first one. */
    static long playerInputMs() {
        return playerInputMs;
    }

    public synchronized void start() {
        if (poller != null) return;
        poller = Executors.newSingleThreadScheduledExecutor();
        poller.scheduleWithFixedDelay(this::poll, POLL_S, POLL_S, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (poller != null) poller.shutdownNow();
        poller = null;
    }

    /** The game ended: stop and write the peaks. */
    public void finish() {
        stop();
        writePeaks();
        activity.runOnUiThread(() -> AgvnWarningBar.hide(activity));
    }

    private void poll() {
        try {
            long now = SystemClock.uptimeMillis(), free = AgvnMemoryProbe.freeMb();
            AgvnMemoryProbe.Usage use = AgvnMemoryProbe.appUsage();
            synchronized (this) {
                if (free >= 0) minFreeMb = Math.min(minFreeMb, free);
                maxRssMb = Math.max(maxRssMb, use.rssMb);
                maxDmabufMb = Math.max(maxDmabufMb, use.dmabufMb);
            }
            AgvnSessionTrack.memory(System.currentTimeMillis(), free, rules.lowFreeMb); // for AgvnDoctor at the game's end
            if (now - peaksWrittenMs >= PEAKS_EVERY_MS) writePeaks();
            AgvnMemoryRules.Reason reason = rules.feed(now, startedMs, free, use.rssMb + use.dmabufMb, lastInputMs);
            if (reason == AgvnMemoryRules.Reason.NONE) return;
            AgvnSessionLog.event("Cảnh báo RAM (" + reason + "): còn trống " + free + " MB, app và game RSS "
                    + use.rssMb + " MB, DMA-BUF " + use.dmabufMb + " MB");
            activity.runOnUiThread(() -> showBar(reason, free, use));
        } catch (RuntimeException e) {
            Log.w(TAG, "memory watch sample failed", e);
        }
    }

    private synchronized void writePeaks() {
        peaksWrittenMs = SystemClock.uptimeMillis();
        if (minFreeMb == Long.MAX_VALUE) return;
        AgvnSessionLog.note(AgvnSessionLog.RAM, "RAM trống thấp nhất " + minFreeMb + " MB; app và game cao nhất: RSS "
                + maxRssMb + " MB, DMA-BUF " + maxDmabufMb + " MB\n");
    }

    private void showBar(AgvnMemoryRules.Reason reason, long freeMb, AgvnMemoryProbe.Usage use) {
        String detail = activity.getString(R.string.agvn_ram_detail, gb(freeMb), gb(use.rssMb + use.dmabufMb), gb(use.dmabufMb));
        if (reason == AgvnMemoryRules.Reason.GROWING) detail += "\n" + activity.getString(R.string.agvn_ram_growing);
        AgvnWarningBar.show(activity, activity.getString(R.string.agvn_ram_title), detail,
                new AgvnWarningBar.Choice(R.string.agvn_ram_later, null),
                new AgvnWarningBar.Choice(R.string.agvn_ram_exit, exitGame));
    }

    /** "1,2 GB" (Vietnamese decimal comma). */
    static String gb(long mb) {
        return String.format(new Locale("vi", "VN"), "%.1f GB", Math.max(0, mb) / 1024.0);
    }
}
