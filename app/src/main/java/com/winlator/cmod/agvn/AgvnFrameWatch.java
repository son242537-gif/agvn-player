/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import com.winlator.cmod.widget.XServerRendererView;
import com.winlator.cmod.xserver.Window;

import java.util.List;

/**
 * What the X server's Present extension tells AGVN about each game frame: the FPS limit on the vsync grid
 * ({@link AgvnVsyncLimiter}), pauses of a second or more for the logs ({@link AgvnFrameStalls}), and the game's CPU
 * ({@link AgvnGameCpu}: what it did during a pause, and performance hints when they are on for the game).
 */
public final class AgvnFrameWatch {
    private static final String TAG = "AGVN";

    private final AgvnFrameStalls stalls = new AgvnFrameStalls();
    private final AgvnVsyncLimiter limiter = new AgvnVsyncLimiter(stalls);
    private final AgvnGameCpu gameCpu = new AgvnGameCpu();

    /** Every game frame, whatever limits it, before {@link #onPacedFrame}. Called on the X server thread. */
    public void onPresent(Window window, int limit, boolean paced, XServerRendererView view) {
        try {
            long now = System.nanoTime();
            AgvnFrameStalls.Mode mode = limit <= 0 ? AgvnFrameStalls.Mode.NO_LIMIT
                    : paced ? AgvnFrameStalls.Mode.VSYNC : AgvnFrameStalls.Mode.TIMER;
            AgvnFrameStalls.Stall stall = stalls.onFrame(now, mode, limit, limiter.heldCount());
            List<AgvnGameThreads.Busy> cpu = stall != null ? gameCpu.stallUsage(now) : null;
            gameCpu.onFrame(window, now, limit, view);
            if (stall == null) return;
            stall.cpu = cpu;
            Log.w(TAG, stall.english());
            AgvnSessionLog.event(stall.vietnamese());
        } catch (RuntimeException e) {
            Log.w(TAG, "frame watch failed", e); // the logs must never cost the game its frame
        }
    }

    /** A game frame under the vsync limit; {@code release} gives its buffer back. Called on the X server thread. */
    public void onPacedFrame(int windowId, Runnable release, XServerRendererView view) {
        limiter.onFrame(windowId, release, view);
    }
}
