/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The slow-game check of a "Chạy nhẹ" game, as {@link AgvnSlowWatch} does it for Windows games. Every 5 s:
 * <ul>
 *   <li>the game's FPS: an HTML page's, or the frames mkxp-z runs (AGVN's preload script agvn_fps.rb); Ren'Py draws
 *   only when something moves, so it has none and only a thread busy all the time counts;</li>
 *   <li>the GPU's load, and the runner's busiest thread (Ren'Py, mkxp-z; an HTML page runs in WebView's own process).</li>
 * </ul>
 * Under 3/4 of the game's own frame rate for a minute shows the same bar ({@link AgvnSlowBar}); "Mở lại game ngay"
 * quits the game and the library starts it again.
 */
final class AgvnLightSlow {
    private static final String TAG = "AGVN";
    static final float SLOW_SHARE = 0.75f;

    private final Activity activity;
    private final AgvnLightTools.Host host;
    private final AgvnLoadProbe probe;
    private final ArrayDeque<AgvnSlowWatch.Sample> samples = new ArrayDeque<>();
    private final long createdMs = SystemClock.uptimeMillis();
    private Shortcut shortcut;
    private boolean looked;
    private ScheduledExecutorService poller;
    private volatile boolean asked;

    AgvnLightSlow(Activity activity, AgvnLightTools.Host host) {
        this.activity = activity;
        this.host = host;
        probe = new AgvnLoadProbe(AgvnHtmlGame.RUNNER_HTML.equals(host.runner()) ? AgvnLoadProbe.Threads.NONE : AgvnLoadProbe.Threads.SELF);
    }

    synchronized void start() {
        if (poller != null || asked) return;
        samples.clear();
        poller = Executors.newSingleThreadScheduledExecutor();
        poller.scheduleWithFixedDelay(this::poll, AgvnSlowWatch.POLL_S, AgvnSlowWatch.POLL_S, TimeUnit.SECONDS);
    }

    synchronized void stop() {
        if (poller != null) poller.shutdownNow();
        poller = null;
    }

    private void poll() {
        try {
            long now = SystemClock.uptimeMillis();
            List<AgvnSlowWatch.Sample> window;
            synchronized (this) {
                samples.addLast(new AgvnSlowWatch.Sample(host.fps(), probe.gpuPercent(now), probe.busiestThread(now)));
                while (samples.size() > AgvnSlowWatch.WINDOW) samples.removeFirst();
                window = new ArrayList<>(samples);
            }
            if (asked || now - createdMs < AgvnSlowWatch.WARMUP_S * 1000) return;
            int target = host.targetFps();
            String id = AgvnSlowWatch.verdict(window, 0, target > 0 ? target * SLOW_SHARE : AgvnSlowWatch.SLOW_FPS);
            if (id == null || game() == null) return;
            asked = true;
            stop();
            Log.i(TAG, "light game slow (" + id + "): " + AgvnSlowWatch.params(window));
            AgvnSlowBar.ask(activity, shortcut, id, AgvnSlowWatch.params(window), host::quit);
        } catch (RuntimeException e) {
            Log.w(TAG, "light slow check failed", e);
        }
    }

    /** The game's shortcut, from the launch intent (looked up once, off the UI thread); null when it is gone. */
    private Shortcut game() {
        if (looked) return shortcut;
        looked = true;
        Intent intent = activity.getIntent();
        String path = intent.getStringExtra("shortcut_path");
        int id = intent.getIntExtra("container_id", 0);
        if (id == 0 && path != null) id = AgvnHtmlGame.containerIdIn(new File(path));
        shortcut = path != null ? AgvnRelaunch.find(activity, id, path) : null;
        return shortcut;
    }
}
