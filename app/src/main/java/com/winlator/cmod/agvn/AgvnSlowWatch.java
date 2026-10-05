/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.SystemClock;
import android.util.Log;

import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.widget.XServerRendererView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * While a Windows game plays: every 5 s its FPS, the GPU's load and its busiest thread. When it stays slow for a minute
 * (steadily under 20 FPS, not a still screen, not the player's own FPS cap), a bar over the game says what is busy and
 * offers that problem's fixes ({@link AgvnSlowBar}; game-problems.json: slow-gpu, slow-cpu, slow, slow-unknown). Once
 * per game session; "Để vậy" stops asking for this game. {@link #verdict} also serves the "Chạy nhẹ" games
 * ({@link AgvnLightSlow}).
 */
public final class AgvnSlowWatch {
    private static final String TAG = "AGVN";
    static final long POLL_S = 5, WARMUP_S = 90;
    static final int WINDOW = 12, SLOW_FPS = 20, STILL_FPS = 5, GPU_BUSY = 85;
    static final double CPU_BUSY = 0.9, CPU_BUSY_ALONE = 0.95;

    /** One poll: frames per second (-1 unknown), GPU percent (-1 unknown), busiest thread in cores (-1 unknown). */
    static final class Sample {
        final float fps;
        final int gpu;
        final double cpu;

        Sample(float fps, int gpu, double cpu) {
            this.fps = fps;
            this.gpu = gpu;
            this.cpu = cpu;
        }
    }

    private final XServerDisplayActivity activity;
    private final Shortcut shortcut;
    private final Runnable exitGame;
    private final AgvnLoadProbe probe = new AgvnLoadProbe(AgvnLoadProbe.Threads.WINE);
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private ScheduledExecutorService poller;
    private long lastMs;
    private volatile boolean asked;

    /** {@code exitGame}: closes the game, for "Mở lại game ngay" after a fix. */
    public AgvnSlowWatch(XServerDisplayActivity activity, Shortcut shortcut, Runnable exitGame) {
        this.activity = activity;
        this.shortcut = shortcut;
        this.exitGame = exitGame;
    }

    public synchronized void start() {
        if (poller != null || shortcut == null || asked) return;
        AgvnSessionTrack.takeFrames(); // frames drawn while paused are not this minute's
        samples.clear();
        lastMs = SystemClock.uptimeMillis();
        poller = Executors.newSingleThreadScheduledExecutor();
        poller.scheduleWithFixedDelay(this::poll, POLL_S, POLL_S, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (poller != null) poller.shutdownNow();
        poller = null;
    }

    private void poll() {
        try {
            long now = SystemClock.uptimeMillis();
            float fps = AgvnSessionTrack.takeFrames() * 1000f / Math.max(1, now - lastMs);
            lastMs = now;
            List<Sample> window;
            synchronized (this) {
                samples.addLast(new Sample(fps, probe.gpuPercent(now), probe.busiestThread(now)));
                while (samples.size() > WINDOW) samples.removeFirst();
                window = new ArrayList<>(samples);
            }
            if (asked || !AgvnSessionTrack.started() || AgvnSessionTrack.seconds(System.currentTimeMillis()) < WARMUP_S) return;
            XServerRendererView view = activity.getXServerView();
            String id = verdict(window, view != null ? view.getFpsLimit() : 0, SLOW_FPS);
            if (id == null) return;
            asked = true;
            stop();
            Map<String, String> params = params(window);
            AgvnSessionLog.event("Game chậm (" + id + "): " + params);
            AgvnSlowBar.ask(activity, shortcut, id, params, exitGame);
        } catch (RuntimeException e) {
            Log.w(TAG, "slow watch sample failed", e);
        }
    }

    /**
     * "slow-gpu", "slow-cpu", "slow" or null for the last {@link #WINDOW} polls: under {@code slowFps} steadily, a GPU
     * from {@link #GPU_BUSY}% or a thread from {@link #CPU_BUSY} of a core. Without any FPS reading (Ren'Py draws only
     * when something moves), only a thread busy all the time ({@link #CPU_BUSY_ALONE}) counts. Pure Java.
     */
    static String verdict(List<Sample> window, int fpsCap, float slowFps) {
        if (window.size() < WINDOW) return null;
        double fps = median(window, 'f'), gpu = median(window, 'g'), cpu = median(window, 'c');
        if (fps < 0) return cpu >= CPU_BUSY_ALONE ? "slow-cpu" : null;
        for (Sample s : window) if (s.fps >= 0 && s.fps < STILL_FPS) return null; // a still screen at times
        if (fps >= slowFps || (fpsCap > 0 && fps >= fpsCap - 2)) return null;
        if (gpu >= GPU_BUSY) return "slow-gpu";
        if (cpu >= CPU_BUSY) return "slow-cpu";
        if (gpu >= 0) return null; // the GPU says it is not busy: the game holds its own pace
        return cpu >= 0 ? "slow" : "slow-unknown"; // "slow": the CPU is not busy, so most likely the GPU is
    }

    /** Median FPS ('f'), GPU ('g') or CPU ('c') of the known values; -1 when none is known. */
    static double median(List<Sample> window, char what) {
        List<Double> values = new ArrayList<>();
        for (Sample s : window) {
            double v = what == 'f' ? s.fps : what == 'g' ? s.gpu : s.cpu;
            if (v >= 0) values.add(v);
        }
        if (values.isEmpty()) return -1;
        Collections.sort(values);
        return values.get(values.size() / 2);
    }

    /** The texts' values: fps, speed (" (14 FPS)", empty without one: Ren'Py), gpu (percent), cpu (percent of a core). */
    static Map<String, String> params(List<Sample> window) {
        Map<String, String> params = new LinkedHashMap<>();
        long fps = Math.round(median(window, 'f'));
        params.put("fps", String.valueOf(fps));
        params.put("speed", fps >= 0 ? " (" + fps + " FPS)" : "");
        params.put("gpu", String.valueOf(Math.round(median(window, 'g'))));
        params.put("cpu", String.valueOf(Math.round(median(window, 'c') * 100)));
        return params;
    }
}
