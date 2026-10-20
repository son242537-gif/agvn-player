/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

/**
 * A Windows game still starting ({@link AgvnStartupProgress}: no frames yet) goes on loading when the player leaves the
 * app, and stops once it is up if the player is still away. Winlator stops Wine's processes as the app goes to the
 * background, so a game that loads for a minute before its window never got there for a player who looked away
 * meanwhile: The Agnietta (Pixel Game Maker MV, 32-bit; Galaxy Z Fold4) needs about 56 s of its own time, and four of
 * its five starts were stopped, then swiped away, before that (19/10/2026). A game that is up stops at once, as
 * before. Thread-safe.
 */
public final class AgvnLoadAway {
    /** What stops the game, while it loads on with the player away; null otherwise. */
    private static Runnable pauseWhenUp; // guarded by the class

    private AgvnLoadAway() {}

    /**
     * The player leaves: true when the game is still {@code starting} ({@link AgvnStartupProgress#starting}), so Wine
     * goes on, and {@code pause} runs once the game is up (or its start watch gives up); false to stop it now.
     */
    public static synchronized boolean keepLoading(boolean starting, Runnable pause) {
        if (!starting) return false;
        if (pauseWhenUp == null) AgvnSessionLog.event("Rời app khi game còn đang khởi động: game nạp tiếp, dừng khi đã lên hình");
        pauseWhenUp = pause;
        return true;
    }

    /** The player is back, or a new game starts: nothing to stop later. */
    public static synchronized void back() {
        pauseWhenUp = null;
    }

    /** The game is up ({@code drawn}), or its start watch gave up: stopped now if the player is still away. Any thread. */
    static void up(boolean drawn) {
        Runnable pause;
        synchronized (AgvnLoadAway.class) {
            pause = pauseWhenUp;
            pauseWhenUp = null;
        }
        if (pause == null) return;
        AgvnSessionLog.event(drawn ? "Game đã lên hình khi người chơi ở ngoài app: dừng tới khi quay lại"
                : "Game khởi động quá " + AgvnStartupProgress.MAX_MS / 60_000 + " phút khi người chơi ở ngoài app: dừng tới khi quay lại");
        pause.run();
    }
}
