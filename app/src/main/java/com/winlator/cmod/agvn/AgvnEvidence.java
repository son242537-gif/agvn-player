/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a game's end left for {@link AgvnProblemCatalog} to read: the lines Wine, DXVK, the game and its engine log
 * printed, how the session ended, and the conditions game-problems.json names in "when". Pure Java (JVM-testable).
 */
final class AgvnEvidence {
    /** ApplicationExitInfo.REASON_SIGNALED, REASON_LOW_MEMORY and IMPORTANCE_FOREGROUND. */
    static final int REASON_SIGNALED = 2, REASON_LOW_MEMORY = 3, IMPORTANCE_FOREGROUND = 100;
    /** A player who quits a game that showed nothing for this long, besides maybe an error box, saw it fail. */
    static final long QUIT_AFTER_S = 10;
    /** A session that ran this long without a crash ran well, however it ended, unless it froze. */
    static final long GOOD_AFTER_S = 60;
    /**
     * A game that showed no frame for this long before it ended, while the player pressed at least
     * {@link #FROZEN_PRESSES} times, froze. Party Me (Godot on ANGLE, Mali-G615) stopped showing frames 17-24 s
     * before it closed itself, three times, the player tapping on: 0.1.17 kept the one that lasted 88 s as a good run.
     */
    static final long FROZEN_S = 10;
    static final int FROZEN_PRESSES = 5;

    final List<String> lines = new ArrayList<>();
    /** Values the problem texts use: "crash", "screen", "free", "fps", "speed", "gpu", "cpu", "godot", "runner", "error". */
    final Map<String, String> params = new LinkedHashMap<>();
    String engine = "";
    /** "" for a Windows game; renpy, rgss or html for a "Chạy nhẹ" game (AgvnLightDoctor). */
    String runner = "";
    boolean crashed, endedByGame, playerQuit, started, bigWindowSeen, changed, smallScreen;
    /** A Godot game started with the renderer a fix picked (AgvnFixEdits.godotSwitched). */
    boolean godotSwitched;
    /** "Chạy nhẹ": the game reported an error, its page process died, Android found it frozen. */
    boolean scriptError, pageCrash, frozen;
    long seconds;
    /** How Android ended the app, for a session found unfinished at the next start; -1 when it was not. */
    int killedReason = -1, killedImportance;
    /** Free RAM (MB) when it last fell under the RAM bar's level, shortly before the game ended; -1 when it did not. */
    long lowRamFreeMb = -1;
    /** The game started with the most RAM savings, having run out of RAM before ({@link AgvnMemorySaver#ranOut}). */
    boolean ramSaved;
    /** Seconds from the game's last frame to its end (-1: none), and the player's presses in them. */
    long frozenS = -1;
    int frozenPresses;

    /** The game never drew its window and then closed, or the player gave up on it. */
    boolean noStart() {
        if (crashed || started || killedReason >= 0) return false;
        if (endedByGame) return true;
        return playerQuit && !bigWindowSeen && seconds >= QUIT_AFTER_S;
    }

    /** The game failed within a minute: it crashed or closed by itself, or never showed its window. */
    boolean endedEarly() {
        return seconds < GOOD_AFTER_S && (crashed || endedByGame || noStart());
    }

    boolean holds(String condition) {
        switch (condition) {
            case "failed": return crashed || noStart();
            case "no-start": return noStart();
            case "crash": return crashed;
            case "ended": return crashed || endedByGame;
            case "ended-early": return endedEarly();
            case "godot-switched": return godotSwitched;
            case "changed": return changed;
            case "small-screen": return smallScreen;
            case "killed-low-memory": return killedReason == REASON_LOW_MEMORY;
            case "killed-background": return killedReason == REASON_SIGNALED && killedImportance > IMPORTANCE_FOREGROUND;
            case "low-ram": return lowRamFreeMb >= 0;
            case "ram-saved": return ramSaved;
            case "light": return !runner.isEmpty();
            case "script-error": return scriptError;
            case "page-crash": return pageCrash;
            case "frozen": return frozen;
            default: return false; // "live" problems are found while playing; names a newer file may add stay unmet
        }
    }

    boolean holdsAll(List<String> conditions) {
        for (String c : conditions) if (!holds(c)) return false;
        return true;
    }

    /** The game stopped showing frames before it ended, though the player kept pressing ({@link #FROZEN_S}). */
    boolean frozeAtEnd() {
        return frozenS >= FROZEN_S && frozenPresses >= FROZEN_PRESSES;
    }

    /** The game drew its window and ran without a crash or freeze: its settings work and are kept (AgvnGoodConfig). */
    boolean good() {
        return started && !crashed && killedReason < 0 && !frozeAtEnd() && (playerQuit || seconds >= GOOD_AFTER_S);
    }
}
