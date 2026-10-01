/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
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
 * <li>Every 2 s it picks the game's threads that matter: the main thread, and the busiest others from 30% of a core,
 *   at most 3 in all ({@link AgvnGameThreads#pick}).</li>
 * <li>When "Ưu tiên nhân CPU mạnh" is on for the game, it hands those threads to {@link AgvnCpuBoost} with the frame
 *   time the FPS limit aims at (60 FPS without a limit), and reports the CPU time the busiest of them spent per frame.
 *   That is the work, not the gap between frames: a visual novel that sends a frame a second is idle, not late. During a
 *   pause the work so far is reported, so loading counts too. A busy main thread is also pinned to the fastest cores
 *   ({@link AgvnMainThreadPin}).</li>
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
    private final AgvnMainThreadPin pin = new AgvnMainThreadPin();
    private final long ticksPerSecond = AgvnGameProcess.clockTicks();
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
    private String busyLabel = "";
    private long lastPickNs, gameSeenNs;

    /** Every game frame, on the X server thread. */
    void onFrame(Window window, long nowNs, int limit, XServerRendererView view) {
        synchronized (this) {
            if (window.id != windowId) {
                windowId = window.id;
                windowPid = AgvnGameProcess.pidOf(window);
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
        if (to == null) return null;
        List<AgvnGameThreads.Busy> busy = AgvnGameThreads.busiest(from, to, ticksPerSecond);
        if (!busy.isEmpty()) { // the cores the busiest thread may use now: shows whether a pin held
            AgvnGameThreads.Busy b = busy.get(0);
            busy.set(0, new AgvnGameThreads.Busy(b.tid, b.name, b.core, b.percent, b.main,
                    AgvnCpuCores.allowed(PROC, b.tid)));
        }
        return busy;
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
            pid = AgvnGameProcess.findGame(PROC, ownPid, Process.myUid(), Process.myPid(), seen);
            synchronized (this) {
                gamePid = pid;
            }
            AgvnGameThreads.Snapshot snap = pid > 0 ? AgvnGameThreads.read(PROC, pid, now) : null;
            if (snap != null) gameSeenNs = now;
            if (snap != null && lastPick != null && lastPick.pid == pid) {
                List<AgvnGameThreads.Busy> busy = AgvnGameThreads.busiest(lastPick, snap, ticksPerSecond);
                List<AgvnGameThreads.Busy> picked = AgvnGameThreads.pick(busy, BUSY_PERCENT, MAX_THREADS);
                int[] tids = AgvnGameThreads.ids(picked);
                if (tids.length > 0 && !Arrays.equals(tids, busyTids)) { // an idle game keeps its last threads
                    busyTids = tids;
                    busyLabel = AgvnGameThreads.label(picked);
                    cpuSeen.clear();
                }
                for (AgvnGameThreads.Busy b : picked) { // an idle main thread keeps its pin; turning off releases it
                    if (b.main) pin.update(on, b.tid, b.percent >= BUSY_PERCENT);
                }
            }
            lastPick = snap;
            if (on && ctx != null) boost.aim(ctx, busyTids, busyLabel, target);
        }
        if (on) pin.recheck(); // pinned again at once when something else moved it
        if (on && pid > 0) reportWork(pid, framesNow, target);
        else if (!on) {
            boost.close();
            pin.release();
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
        pin.release();
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
}
