/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import com.winlator.cmod.widget.XServerRendererView;
import com.winlator.cmod.xserver.Window;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Watches the CPU of the process that draws the game, on its own thread every 100 ms:
 * <ul>
 * <li>When no frame has come for 0.5 s, it notes the threads' CPU time once, so a stall's log line can say whether the
 *   game was busy (loading or computing: its busiest thread near 100%) or waiting (near 0%).</li>
 * <li>Every 2 s it finds the game's busiest threads (from 30% of a core, at most 3).</li>
 * <li>When "Ưu tiên nhân CPU mạnh" is on for the game, it hands those threads to {@link AgvnCpuBoost} with the frame
 *   time the FPS limit aims at (60 FPS without a limit), and reports the CPU time the busiest of them spent per frame.
 *   That is the work, not the gap between frames: a visual novel that sends a frame a second is idle, not late. During a
 *   pause the work so far is reported, so loading counts too.</li>
 * </ul>
 * The game's process is the one that owns the frame's window (_NET_WM_PID), or else this user's busiest process. The
 * thread stops once that process has been gone and no frame has come for 10 s; the next frame starts it again.
 */
final class AgvnGameCpu {
    private static final File PROC = new File("/proc");
    private static final long TICK_MS = 100;
    private static final long PAUSE_NS = 500_000_000L, PICK_EVERY_NS = 2_000_000_000L, GONE_NS = 10_000_000_000L;
    private static final int BUSY_PERCENT = 30, MAX_THREADS = 3;

    private final AgvnCpuBoost boost = new AgvnCpuBoost();
    private final long ticksPerSecond = clockTicks();
    // guarded by this
    private int windowId = -1, windowPid, gamePid, frames;
    private long lastFrameNs, targetNs = 1_000_000_000L / 60;
    private boolean boostOn;
    private Context context;
    private Handler handler;
    private AgvnGameThreads.Snapshot baseline;
    // the worker thread's own (reset under the lock when it stops)
    private final Map<Integer, Long> seen = new HashMap<>(), cpuSeen = new HashMap<>();
    private AgvnGameThreads.Snapshot lastPick;
    private int[] busyTids = new int[0];
    private long lastPickNs, gameSeenNs;

    /** Every game frame, on the X server thread. */
    void onFrame(Window window, long nowNs, int limit, XServerRendererView view) {
        synchronized (this) {
            if (window.id != windowId) {
                windowId = window.id;
                windowPid = pidOf(window);
            }
            lastFrameNs = nowNs;
            frames++;
            baseline = null;
            boostOn = view != null && view.isCpuBoost();
            targetNs = 1_000_000_000L / (limit > 0 ? limit : 60);
            if (context == null && view != null) context = view.getContext().getApplicationContext();
            if (handler == null) start();
        }
    }

    /** The game's threads from 0.5 s into the pause that ends now, busiest first; null when not measured. */
    List<AgvnGameThreads.Busy> stallUsage(long nowNs) {
        AgvnGameThreads.Snapshot from;
        synchronized (this) {
            from = baseline;
            baseline = null;
        }
        AgvnGameThreads.Snapshot to = from != null ? AgvnGameThreads.read(PROC, from.pid, nowNs) : null;
        return to != null ? AgvnGameThreads.busiest(from, to, ticksPerSecond) : null;
    }

    private void start() { // under this
        HandlerThread thread = new HandlerThread("AgvnGameCpu", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.post(this::tick);
    }

    private void tick() {
        boolean keep = true;
        try {
            keep = work();
        } catch (RuntimeException e) {
            Log.w("AGVN", "game CPU watch failed", e); // diagnostics must never take the app down
        }
        if (!keep) stop();
        else synchronized (this) {
            if (handler != null) handler.postDelayed(this::tick, TICK_MS);
        }
    }

    /** One tick; false once the game's process has been gone and no frame has come for 10 s. */
    private boolean work() {
        long now = System.nanoTime(), last, target;
        int pid, ownPid, framesNow;
        boolean on, needBaseline;
        Context ctx;
        synchronized (this) {
            last = lastFrameNs;
            target = targetNs;
            on = boostOn;
            ctx = context;
            ownPid = windowPid;
            pid = gamePid;
            framesNow = frames;
            frames = 0;
            needBaseline = baseline == null;
        }
        if (now - last >= PAUSE_NS && needBaseline && pid > 0) {
            AgvnGameThreads.Snapshot snap = AgvnGameThreads.read(PROC, pid, now);
            synchronized (this) {
                if (baseline == null && lastFrameNs == last) baseline = snap;
            }
        }
        if (now - lastPickNs >= PICK_EVERY_NS) {
            lastPickNs = now;
            pid = AgvnGameThreads.findGame(PROC, ownPid, Process.myUid(), Process.myPid(), seen);
            synchronized (this) {
                gamePid = pid;
            }
            AgvnGameThreads.Snapshot snap = pid > 0 ? AgvnGameThreads.read(PROC, pid, now) : null;
            if (snap != null) gameSeenNs = now;
            int[] tids = snap != null && lastPick != null ? AgvnGameThreads.top(
                    AgvnGameThreads.busiest(lastPick, snap, ticksPerSecond), BUSY_PERCENT, MAX_THREADS) : new int[0];
            if (tids.length > 0 && !Arrays.equals(tids, busyTids)) { // an idle game keeps its last threads
                busyTids = tids;
                cpuSeen.clear();
            }
            lastPick = snap;
            if (on && ctx != null) boost.aim(ctx, busyTids, target);
        }
        if (on && pid > 0) reportWork(pid, framesNow, target);
        else if (!on) {
            boost.close();
            cpuSeen.clear();
        }
        return now - gameSeenNs <= GONE_NS || now - last <= GONE_NS;
    }

    /** CPU time the busiest hinted thread spent per frame since the last tick, or so far in a pause. */
    private void reportWork(int pid, int framesNow, long target) {
        long work = 0;
        for (int tid : busyTids) {
            long cpu = AgvnGameThreads.cpuNs(PROC, pid, tid, ticksPerSecond);
            Long before = cpu >= 0 ? cpuSeen.put(tid, cpu) : null;
            if (before != null) work = Math.max(work, cpu - before);
        }
        if (work > 0) boost.report(Math.min(framesNow > 0 ? work / framesNow : work, 4 * target));
    }

    private void stop() {
        boost.close();
        synchronized (this) {
            seen.clear();
            cpuSeen.clear();
            lastPick = null;
            busyTids = new int[0];
            lastPickNs = gameSeenNs = 0;
            gamePid = 0;
            baseline = null;
            if (handler != null) handler.getLooper().quitSafely();
            handler = null;
        }
    }

    /** The process that owns the window or one of its parents (Wine sets _NET_WM_PID on its top-level windows). */
    private static int pidOf(Window window) {
        try {
            for (Window w = window; w != null; w = w.getParent()) {
                int pid = w.getProcessId();
                if (pid > 0) return pid;
            }
        } catch (RuntimeException ignored) {
            // a malformed property: find the game by its CPU instead
        }
        return 0;
    }

    private static long clockTicks() {
        try {
            long ticks = Os.sysconf(OsConstants._SC_CLK_TCK);
            return ticks > 0 ? ticks : 100;
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            return 100;
        }
    }
}
