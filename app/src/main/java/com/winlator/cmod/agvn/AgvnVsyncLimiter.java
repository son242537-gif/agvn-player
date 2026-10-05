/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Log;
import android.view.Choreographer;

import androidx.annotation.RequiresApi;

import com.winlator.cmod.widget.XServerRendererView;

import java.util.List;

/**
 * The X server's FPS limit, locked to the screen's vsync. A free-running timer (the old limiter) drifts against the
 * screen, so on the POCO F8 Pro at 30 FPS / 60 Hz one to three frames a second stayed 50 ms and the next only 16 ms.
 *
 * <p>Choreographer runs on its own thread and feeds {@link AgvnFrameSlots}. On the vsyncs it picks, the renderer draws
 * the newest content and asks Android to show it at a given vsync (frame timelines, Android 13+), so each frame stays
 * exactly N vsyncs. If vsyncs stop coming (screen off), a timer keeps giving the game its buffers. The thread stops
 * when the limit or "Khớp nhịp màn hình" is turned off or the game sends nothing for 5 s, and gives back every buffer
 * it held. Pauses of a second or more between game frames are logged with what the limit did meanwhile
 * ({@link AgvnFrameStalls}), whichever limiter runs.
 */
public final class AgvnVsyncLimiter {
    private static final String TAG = "AGVN";
    /** Time the renderer needs to draw and queue a frame before a frame timeline's deadline. */
    private static final long DRAW_NS = 6_000_000L;
    private static final long STOP_WHEN_IDLE_NS = 5_000_000_000L, HINT_EVERY_NS = 10_000_000_000L;

    private final AgvnFrameSlots slots = new AgvnFrameSlots();
    private final AgvnFrameStalls stalls = new AgvnFrameStalls();
    private volatile XServerRendererView view;
    private volatile long lastFrameNs;
    private Loop loop; // guarded by this

    /** Every game frame, whatever limits it, before {@link #onFrame}: logs a pause of a second or more before it. */
    public void notePresent(int limit, boolean paced) {
        AgvnFrameStalls.Mode mode = limit <= 0 ? AgvnFrameStalls.Mode.NO_LIMIT
                : paced ? AgvnFrameStalls.Mode.VSYNC : AgvnFrameStalls.Mode.TIMER;
        AgvnFrameStalls.Stall stall = stalls.onFrame(System.nanoTime(), mode, limit, slots.heldCount());
        if (stall == null) return;
        Log.w(TAG, stall.english());
        AgvnSessionLog.event(stall.vietnamese());
    }

    /** A game frame came under the vsync limit; {@code release} gives its buffer back. Called on the X server thread. */
    public void onFrame(int windowId, Runnable release, XServerRendererView view) {
        this.view = view;
        lastFrameNs = System.nanoTime();
        synchronized (this) {
            slots.onFrame(windowId, release);
            if (loop == null) loop = new Loop();
        }
    }

    /** One vsync thread. After a stop, the next frame starts a new one. */
    private final class Loop {
        final HandlerThread thread = new HandlerThread("AgvnVsync", Process.THREAD_PRIORITY_DISPLAY);
        final Handler handler;
        final Runnable fallback = this::onFallback;
        long periodNs = 16_666_667L, lastVsyncNs, lastHintNs, refreshCheckedNs;
        int hintedFps = -1;
        boolean callbackPosted, stopped;

        Loop() {
            thread.start();
            handler = new Handler(thread.getLooper());
            handler.post(this::begin);
        }

        void begin() {
            XServerRendererView v = view;
            try {
                if (v != null) v.setPacedPresentation(true);
                postCallback();
                armFallback();
            } catch (RuntimeException e) {
                Log.w(TAG, "vsync limiter did not start", e);
                stop(v, false, "error");
            }
        }

        void postCallback() {
            if (callbackPosted || stopped) return;
            callbackPosted = true;
            Choreographer choreographer = Choreographer.getInstance();
            if (Build.VERSION.SDK_INT >= 33) choreographer.postVsyncCallback(this::onVsync33);
            else choreographer.postFrameCallback(this::onVsyncOld);
        }

        @RequiresApi(33)
        void onVsync33(Choreographer.FrameData data) {
            callbackPosted = false;
            Choreographer.FrameTimeline[] lines = data.getFrameTimelines();
            if (lines.length >= 2) {
                long p = lines[1].getExpectedPresentationTimeNanos() - lines[0].getExpectedPresentationTimeNanos();
                if (p > 2_000_000L && p < 70_000_000L) periodNs = p;
            }
            long now = System.nanoTime(), desired = 0;
            Choreographer.FrameTimeline preferred = data.getPreferredFrameTimeline();
            for (Choreographer.FrameTimeline line : lines) {
                if (line.getDeadlineNanos() < preferred.getDeadlineNanos() || line.getDeadlineNanos() < now + DRAW_NS)
                    continue;
                // half a vsync early: Android shows a buffer at the first vsync after its desired time
                desired = line.getExpectedPresentationTimeNanos() - periodNs / 2;
                break;
            }
            tick(data.getFrameTimeNanos(), desired);
        }

        void onVsyncOld(long frameTimeNanos) {
            callbackPosted = false;
            long now = System.nanoTime();
            if (now - refreshCheckedNs > 1_000_000_000L) {
                refreshCheckedNs = now;
                periodNs = (long) (1e9 / AgvnFramePacing.refreshHz(view));
            }
            tick(frameTimeNanos, 0);
        }

        /** No vsync for a while (screen off, app hidden): counts time instead, so the game keeps its buffers coming. */
        void onFallback() {
            tick(System.nanoTime(), 0);
        }

        void tick(long vsyncNs, long desiredPresentNs) {
            if (stopped) return;
            XServerRendererView v = view;
            long now = System.nanoTime();
            stalls.onTick(now);
            try {
                int fps = v != null ? v.getFpsLimit() : 0;
                if (fps <= 0 || !AgvnFramePacing.enabled(v)) {
                    stop(v, true, "turned off");
                    return;
                }
                if (now - lastFrameNs > STOP_WHEN_IDLE_NS) {
                    stop(v, false, "no game frame for 5 s");
                    return;
                }
                long vsyncs = lastVsyncNs == 0 ? 1 : Math.round((vsyncNs - lastVsyncNs) / (double) periodNs);
                if (vsyncs >= 1) { // 0: a second callback for a vsync already counted
                    lastVsyncNs = vsyncNs;
                    slots.setVsyncsPerFrame(AgvnFramePacing.vsyncsPerFrame(1e9 / periodNs, fps));
                    AgvnFrameSlots.Tick t = slots.onVsync((int) Math.min(8, vsyncs));
                    if (t.show) v.requestPacedFrame(desiredPresentNs);
                    if (!t.releases.isEmpty()) stalls.onBack(now);
                    for (Runnable r : t.releases) r.run();
                }
                if (fps != hintedFps || vsyncNs - lastHintNs > HINT_EVERY_NS) {
                    AgvnFramePacing.hintFrameRate(v, fps);
                    Log.i(TAG, "vsync limiter: screen " + Math.round(1e10 / periodNs) / 10.0 + " Hz, limit " + fps
                            + " FPS, a frame every " + AgvnFramePacing.vsyncsPerFrame(1e9 / periodNs, fps) + " vsyncs");
                    hintedFps = fps;
                    lastHintNs = vsyncNs;
                }
                postCallback();
                armFallback();
            } catch (RuntimeException e) {
                Log.w(TAG, "vsync limiter stopped", e);
                stop(v, false, "error");
            }
        }

        void armFallback() {
            handler.removeCallbacks(fallback);
            handler.postDelayed(fallback, Math.max(40, 4 * periodNs / 1_000_000L));
        }

        /**
         * Gives every held buffer back and lets the renderer draw on every change again. The refresh rate hint is only
         * cleared when the limit or the pacing is turned off: a game that pauses drawing for a while keeps it, so the
         * screen does not switch refresh rates back and forth.
         */
        void stop(XServerRendererView v, boolean limitOff, String why) {
            if (stopped) return;
            stopped = true;
            List<Runnable> held;
            synchronized (AgvnVsyncLimiter.this) {
                held = slots.drain();
                stalls.onStop(System.nanoTime(), held.size());
                Log.i(TAG, "vsync limiter stops (" + why + "), gives back " + held.size() + " held buffers");
                if (v != null) {
                    v.setPacedPresentation(false);
                    if (limitOff) AgvnFramePacing.hintFrameRate(v, 0);
                }
                if (loop == this) loop = null;
            }
            handler.removeCallbacksAndMessages(null);
            thread.quitSafely();
            for (Runnable r : held) r.run();
        }
    }
}
