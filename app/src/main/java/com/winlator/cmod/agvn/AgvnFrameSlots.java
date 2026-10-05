/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The FPS limit on the screen's vsync grid, as pure logic ({@link AgvnVsyncLimiter} feeds it the vsyncs).
 *
 * <p>A frame stays on screen N vsyncs ({@link AgvnFramePacing#vsyncsPerFrame}). Two independent parts:
 * <ul>
 * <li>Buffers: every buffer the game presents is given back, one per window every N vsyncs, oldest first. None is ever
 *   dropped or kept, so a game waiting for a buffer gets one within N vsyncs, whatever its frame times and however many
 *   buffers it has. (The renderer has already copied a shown frame into its own image by then.)</li>
 * <li>Showing: N vsyncs after the last shown frame, the newest one is put on screen. A frame that comes later goes on
 *   screen at the next vsync, so a game slower than the limit is not held to the grid.</li>
 * </ul>
 * A fast game gets its buffer on the vsync its last frame is shown, and has N whole vsyncs to draw the next one.
 */
final class AgvnFrameSlots {
    /** What a vsync does: put the newest content on screen, and give these buffers back to the game. */
    static final class Tick {
        final boolean show;
        final List<Runnable> releases;

        Tick(boolean show, List<Runnable> releases) {
            this.show = show;
            this.releases = releases;
        }
    }

    /** Buffer releases per window, oldest first. */
    private final Map<Integer, ArrayDeque<Runnable>> held = new LinkedHashMap<>();
    private int vsyncsPerFrame = 1;
    private int sinceShown, sinceReleased;
    private boolean arrived;

    synchronized void setVsyncsPerFrame(int n) {
        vsyncsPerFrame = Math.max(1, n);
    }

    /** A game frame came; {@code release} gives its buffer back on a later vsync. */
    synchronized void onFrame(int windowId, Runnable release) {
        ArrayDeque<Runnable> buffers = held.get(windowId);
        if (buffers == null) held.put(windowId, buffers = new ArrayDeque<>());
        buffers.addLast(release);
        arrived = true;
    }

    /** {@code vsyncs} vsyncs passed (more than 1 when some were missed). */
    synchronized Tick onVsync(int vsyncs) {
        vsyncs = Math.max(1, vsyncs);
        sinceShown += vsyncs;
        sinceReleased += vsyncs;
        List<Runnable> releases = new ArrayList<>();
        if (sinceReleased >= vsyncsPerFrame) {
            for (Iterator<ArrayDeque<Runnable>> it = held.values().iterator(); it.hasNext(); ) {
                ArrayDeque<Runnable> buffers = it.next();
                releases.add(buffers.pollFirst());
                if (buffers.isEmpty()) it.remove();
            }
            // counted from the last release, so a buffer held late goes back on the next vsync, not a whole slot later
            if (!releases.isEmpty()) sinceReleased = 0;
            else sinceReleased = vsyncsPerFrame;
        }
        boolean show = sinceShown >= vsyncsPerFrame;
        if (show && arrived) {
            sinceShown = 0;
            arrived = false;
        } else if (show) {
            // no new game frame: draw anyway, the renderer skips it unless the cursor or another window changed
            sinceShown = vsyncsPerFrame;
        }
        return new Tick(show, releases);
    }

    /** Every held buffer, e.g. when the limit is turned off. */
    synchronized List<Runnable> drain() {
        List<Runnable> out = new ArrayList<>();
        for (ArrayDeque<Runnable> buffers : held.values()) out.addAll(buffers);
        held.clear();
        sinceShown = sinceReleased = 0;
        arrived = false;
        return out;
    }

    synchronized boolean holdsNothing() {
        return held.isEmpty();
    }

    /** Buffers held now, all windows together. */
    synchronized int heldCount() {
        int n = 0;
        for (ArrayDeque<Runnable> buffers : held.values()) n += buffers.size();
        return n;
    }
}
