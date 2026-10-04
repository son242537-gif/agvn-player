/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
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
    /** A session that ran this long without a crash ran well, however it ended. */
    static final long GOOD_AFTER_S = 60;

    final List<String> lines = new ArrayList<>();
    /** Values the problem texts use: "crash", "screen", "fps", "speed", "gpu", "cpu", "godot", "runner", "error". */
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

    /** The game drew its window and ran without a crash: its settings work and are kept (AgvnGoodConfig). */
    boolean good() {
        return started && !crashed && killedReason < 0 && (playerQuit || seconds >= GOOD_AFTER_S);
    }
}
