/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
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
 * <p>A frame stays on screen N vsyncs ({@link AgvnFramePacing#vsyncsPerFrame}). Every N vsyncs the newest game frame is
 * put on screen and the game gets one buffer back, so it starts its next frame right when the last one is shown and
 * has N whole vsyncs to draw it. The game's buffers are held (not its frames): the X server shows a frame as soon as it
 * comes, and the renderer only draws on the vsyncs asked here. A game slower than the limit gets its buffer back as
 * soon as its late frame comes, and that frame goes on screen at the next vsync.
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

    private static final Tick NOTHING = new Tick(false, new ArrayList<>());

    /** Buffer releases per window, oldest first. */
    private final Map<Integer, ArrayDeque<Runnable>> held = new LinkedHashMap<>();
    private int vsyncsPerFrame = 1;
    private int sinceShown;
    private boolean arrived, releasedEarly;

    synchronized void setVsyncsPerFrame(int n) {
        vsyncsPerFrame = Math.max(1, n);
    }

    /** A game frame came; {@code release} gives its buffer back. Returns the releases to run now. */
    synchronized List<Runnable> onFrame(int windowId, Runnable release) {
        ArrayDeque<Runnable> queue = held.get(windowId);
        if (queue == null) held.put(windowId, queue = new ArrayDeque<>());
        queue.addLast(release);
        arrived = true;
        if (sinceShown < vsyncsPerFrame || releasedEarly) return new ArrayList<>();
        releasedEarly = true; // the game missed its vsync: no point making it wait for the next one
        return oneEach();
    }

    /** {@code vsyncs} vsyncs passed (more than 1 when some were missed). */
    synchronized Tick onVsync(int vsyncs) {
        sinceShown += Math.max(1, vsyncs);
        if (sinceShown < vsyncsPerFrame) return NOTHING;
        if (!arrived) {
            // no new game frame yet: draw anyway, the renderer skips it unless the cursor or another window changed
            sinceShown = vsyncsPerFrame;
            return new Tick(true, new ArrayList<>());
        }
        List<Runnable> releases = releasedEarly ? new ArrayList<>() : oneEach();
        sinceShown = 0;
        arrived = releasedEarly = false;
        return new Tick(true, releases);
    }

    /** Every held buffer, e.g. when the limit is turned off. */
    synchronized List<Runnable> drain() {
        List<Runnable> out = new ArrayList<>();
        for (ArrayDeque<Runnable> queue : held.values()) out.addAll(queue);
        held.clear();
        sinceShown = 0;
        arrived = releasedEarly = false;
        return out;
    }

    synchronized boolean holdsNothing() {
        return held.isEmpty();
    }

    /** The oldest held buffer of each window. */
    private List<Runnable> oneEach() {
        List<Runnable> out = new ArrayList<>();
        for (Iterator<ArrayDeque<Runnable>> it = held.values().iterator(); it.hasNext(); ) {
            ArrayDeque<Runnable> queue = it.next();
            out.add(queue.pollFirst());
            if (queue.isEmpty()) it.remove();
        }
        return out;
    }
}
