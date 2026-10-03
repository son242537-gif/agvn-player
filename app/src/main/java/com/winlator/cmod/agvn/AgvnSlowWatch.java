/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.widget.XServerRendererView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * While a Windows game plays: every 5 s its FPS, the GPU's load and its busiest thread. When it stays slow for a minute
 * (steadily under 20 FPS, not a still screen, not the player's own FPS cap), a bar over the game says what is busy and
 * offers that problem's fixes for the next start (game-problems.json: slow-gpu, slow-cpu, slow). Once per game session;
 * "Để vậy" stops asking for this game.
 */
public final class AgvnSlowWatch {
    private static final String TAG = "AGVN";
    static final long POLL_S = 5, WARMUP_S = 90;
    static final int WINDOW = 12, SLOW_FPS = 20, STILL_FPS = 5, GPU_BUSY = 85;
    static final double CPU_BUSY = 0.9;
    static final String QUIET = "slowQuiet";

    /** One poll: frames per second, GPU percent (-1 unknown), busiest thread in cores (-1 unknown). */
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
    private final AgvnLoadProbe probe = new AgvnLoadProbe();
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private ScheduledExecutorService poller;
    private long lastMs;
    private volatile boolean asked;

    public AgvnSlowWatch(XServerDisplayActivity activity, Shortcut shortcut) {
        this.activity = activity;
        this.shortcut = shortcut;
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
                samples.addLast(new Sample(fps, probe.gpuPercent(), probe.busiestThread(now)));
                while (samples.size() > WINDOW) samples.removeFirst();
                window = new ArrayList<>(samples);
            }
            if (asked || !AgvnSessionTrack.started() || AgvnSessionTrack.seconds(System.currentTimeMillis()) < WARMUP_S) return;
            XServerRendererView view = activity.getXServerView();
            String id = verdict(window, view != null ? view.getFpsLimit() : 0);
            if (id == null) return;
            asked = true;
            stop();
            Map<String, String> params = new LinkedHashMap<>();
            params.put("fps", String.valueOf(Math.round(median(window, 'f'))));
            params.put("gpu", String.valueOf(Math.round(median(window, 'g'))));
            params.put("cpu", String.valueOf(Math.round(median(window, 'c') * 100)));
            AgvnSessionLog.event("Game chậm (" + id + "): " + params);
            Properties state = AgvnGoodConfig.load(activity, shortcut);
            if (!"1".equals(state.getProperty(QUIET))) activity.runOnUiThread(() -> show(id, params, state));
        } catch (RuntimeException e) {
            Log.w(TAG, "slow watch sample failed", e);
        }
    }

    /** "slow-gpu", "slow-cpu", "slow" or null for the last {@link #WINDOW} polls. Pure Java. */
    static String verdict(List<Sample> window, int fpsCap) {
        if (window.size() < WINDOW) return null;
        for (Sample s : window) if (s.fps < STILL_FPS) return null; // a still screen at times (a visual novel waits)
        double fps = median(window, 'f'), gpu = median(window, 'g'), cpu = median(window, 'c');
        if (fps >= SLOW_FPS || (fpsCap > 0 && fps >= fpsCap - 2)) return null;
        if (gpu >= GPU_BUSY) return "slow-gpu";
        if (cpu >= CPU_BUSY) return "slow-cpu";
        return gpu < 0 ? "slow" : null; // the GPU says it is not busy: the game holds its own pace
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

    private void show(String id, Map<String, String> params, Properties state) {
        AgvnProblemCatalog.Finding f = AgvnDoctor.catalog(activity).finding(id, params);
        if (f == null || activity.isFinishing()) return;
        List<AgvnWarningBar.Choice> choices = new ArrayList<>();
        for (AgvnFixes.Fix fix : AgvnFixes.applicable(activity, shortcut, f, state)) {
            choices.add(new AgvnWarningBar.Choice(fix.label, () -> {
                if (AgvnFixApply.apply(activity, shortcut, fix, state))
                    Toast.makeText(activity, activity.getString(R.string.agvn_doctor_saved, fix.label), Toast.LENGTH_LONG).show();
            }));
        }
        String detail = f.cause() + (choices.isEmpty() ? "" : "\n" + activity.getString(R.string.agvn_doctor_lead));
        choices.add(new AgvnWarningBar.Choice(activity.getString(R.string.agvn_doctor_keep), () -> {
            state.setProperty(QUIET, "1");
            AgvnGoodConfig.save(activity, shortcut, state);
        }));
        AgvnWarningBar.show(activity, f.title(), detail, choices.toArray(new AgvnWarningBar.Choice[0]));
    }
}
