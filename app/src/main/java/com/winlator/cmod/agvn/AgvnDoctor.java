/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * "Tự sửa lỗi": when a Windows game ends badly, finds what went wrong ({@link AgvnProblemCatalog}) and leaves it for
 * the library to ask the player about after the app restarts ({@link AgvnDoctorDialog}). A game that ended well keeps
 * its settings as the ones that work ({@link AgvnGoodConfig}).
 */
public final class AgvnDoctor {
    private static final String TAG = "AGVN";
    /** Settings a lower Đồ họa step changes: when only they changed and the game then refused to start, the screen did it. */
    private static final List<String> SCREEN_KEYS = Arrays.asList("screenSize", AgvnQuality.EXTRA_QUALITY, "nativeFpsLimit",
            "nativeFpsLimiterEnabled");
    static final int ENGINE_LOG_BYTES = 16 * 1024, SMALL_SCREEN_LINES = 480;
    static final long KILL_RECENT_MS = 24 * 3600_000L;
    /** Free RAM under the RAM bar's level this shortly before a game ended by itself: memory most likely ended it. */
    static final long LOW_RAM_RECENT_MS = 30_000;
    private static AgvnProblemCatalog catalog;

    private AgvnDoctor() {}

    static synchronized AgvnProblemCatalog catalog(Context ctx) {
        if (catalog != null) return catalog;
        try (Reader in = new InputStreamReader(ctx.getAssets().open(AgvnProblemCatalog.ASSET), StandardCharsets.UTF_8)) {
            catalog = AgvnProblemCatalog.parse(in);
        } catch (Exception e) {
            Log.w(TAG, "game-problems.json not read", e);
            catalog = AgvnProblemCatalog.parse(new StringReader("{}"));
        }
        return catalog;
    }

    /** True when a game left a problem to ask about (MainActivity then stays in front of Big Picture). */
    public static boolean hasPending(Context ctx) {
        return AgvnDoctorStore.hasPending(ctx);
    }

    /** "Mở lại game ngay": the library starts {@code s} again as soon as it shows (AgvnDoctorDialog). */
    static void requestRelaunch(Context ctx, Shortcut s) {
        AgvnDoctorStore.relaunch(ctx, s);
    }

    /** A "Chạy nhẹ" game's end ({@link AgvnLightDoctor}): its problem, if any, is asked about at the library. */
    static void diagnoseLight(Context ctx, Shortcut s, AgvnEvidence ev) {
        AgvnProblemCatalog.Finding f = catalog(ctx).find(ev);
        if (f == null) return;
        Log.i(TAG, "doctor: " + f.id() + " for " + s.name);
        if (AgvnMemorySaver.ranOutOfRam(f.id())) AgvnMemorySaver.markRamShort(s);
        AgvnDoctorStore.ask(ctx, s, f);
    }

    /** The game ends (XServerDisplayActivity.exit), before its session log closes. Never throws. */
    public static void afterGame(Context ctx, Shortcut s) {
        if (s == null) return;
        try {
            Map<String, String> ranWith = AgvnSessionTrack.settings();
            if (ranWith.isEmpty()) ranWith = AgvnGoodConfig.snapshot(s);
            String screen = ranWith.containsKey("screenSize") ? ranWith.get("screenSize") : s.container.getScreenSize();
            AgvnEvidence ev = evidence(s, screen, AgvnWineTail.get().lines(), AgvnWineTail.get().crash(), System.currentTimeMillis());
            if (AgvnGodotGame.learn(s, ev.lines)) ev.engine = s.getExtra(AgvnGameImporter.EXTRA_ENGINE); // its error box said so
            ev.lines.addAll(engineLogTails(s, AgvnSessionTrack.startMs()));
            String unity = AgvnSessionTrack.engineCrash(); // seen while the game played, else in its log now
            if (unity == null) unity = AgvnUnityCrashFiles.inLogs(AgvnEngineLogs.of(s), AgvnSessionTrack.startMs());
            AgvnUnityCrash.into(ev, unity);
            AgvnGodotGame.version(ev, AgvnEngineLogs.exe(s), AgvnEngineLogs.gameDir(s));
            ev.godotSwitched = AgvnFixEdits.godotSwitched(ranWith.get("execArgs"));
            if (ev.frozeAtEnd()) AgvnSessionLog.event("Game đứng hình " + ev.frozenS + " giây trước khi tắt (người chơi "
                    + "bấm " + ev.frozenPresses + " lần mà hình không đổi): không tính là lần chạy tốt");
            diagnose(ctx, s, ev, ranWith);
        } catch (RuntimeException e) {
            Log.w(TAG, "doctor: game end not read", e);
        }
    }

    /**
     * A session found unfinished at the next start (AgvnSessionLog.finishPending); its notes name the game. A game that
     * had crashed (its Unity log says so) is asked about as a crash, however the app ended then; else Android's end is.
     */
    static void afterKill(Context ctx, String notes) {
        try {
            long start = AgvnSessionNotes.value(notes, "start", 0);
            if (System.currentTimeMillis() - start > KILL_RECENT_MS) return; // too long ago to ask about
            String crash = AgvnUnityCrashFiles.inLogs(AgvnSessionNotes.engineLogs(notes, start), start);
            AgvnExitReason.Exit exit = AgvnSessionNotes.removedByPlayer(notes) ? null : AgvnExitReason.exit(ctx, start);
            Shortcut s = exit == null && crash == null ? null : AgvnRelaunch.find(ctx,
                    (int) AgvnSessionNotes.value(notes, "container", -1), AgvnSessionNotes.text(notes, "shortcut"));
            if (s == null) return;
            AgvnEvidence ev = new AgvnEvidence();
            ev.engine = s.getExtra(AgvnGameImporter.EXTRA_ENGINE);
            ev.ramSaved = AgvnMemorySaver.ranOut(s);
            ev.lines.addAll(engineLogTails(s, start));
            AgvnUnityCrash.into(ev, crash);
            ev.killedReason = crash == null ? exit.reason : -1; // a game dead already: its crash, not Android's end
            ev.killedImportance = crash == null ? exit.importance : 0;
            diagnose(ctx, s, ev, AgvnGoodConfig.snapshot(s));
        } catch (RuntimeException e) {
            Log.w(TAG, "doctor: app end not read", e);
        }
    }

    /** {@code now}: the settings the session ran with. */
    private static void diagnose(Context ctx, Shortcut s, AgvnEvidence ev, Map<String, String> now) {
        Properties state = AgvnGoodConfig.load(ctx, s);
        List<String> changed = AgvnGoodConfig.changed(AgvnGoodConfig.good(state), now);
        ev.changed = AgvnGoodConfig.hasGood(state) && !changed.isEmpty();
        AgvnProblemCatalog.Finding f = catalog(ctx).find(ev);
        if (f == null) {
            if (ev.good()) {
                AgvnGoodConfig.setGood(state, now);
                AgvnGoodConfig.save(ctx, s, state);
            }
            return;
        }
        String screen = now.containsKey("screenSize") ? now.get("screenSize") : s.container.getScreenSize();
        if (refusedScreen(f.id(), ev.changed, changed, screen, AgvnGoodConfig.good(state).get("screenSize"))) {
            state.setProperty(AgvnGoodConfig.TOO_SMALL, screen);
            AgvnGoodConfig.save(ctx, s, state);
        }
        AgvnSessionLog.event("Tự sửa lỗi: " + f.id() + " – " + f.title());
        Log.i(TAG, "doctor: " + f.id() + " for " + s.name);
        if (AgvnMemorySaver.ranOutOfRam(f.id())) AgvnMemorySaver.markRamShort(s); // its next starts save the most RAM
        if (AgvnOpenGlCheck.PROBLEM.equals(f.id())) AgvnOpenGlCheck.failed(ctx, s); // no more WineD3D with this driver
        AgvnDoctorStore.ask(ctx, s, f);
    }

    /** True when the game refused this screen size: "low-resolution", or only the screen got smaller before it failed. */
    static boolean refusedScreen(String problem, boolean changed, List<String> changedKeys, String screen, String goodScreen) {
        if (problem.equals("low-resolution")) return true;
        if (!problem.equals("no-start-after-change") || !changed || !SCREEN_KEYS.containsAll(changedKeys)) return false;
        return AgvnGoodConfig.pixels(screen) > 0 && AgvnGoodConfig.pixels(screen) < AgvnGoodConfig.pixels(goodScreen);
    }

    /** {@code screen}: the game's screen size as it ran ("854x480"). */
    static AgvnEvidence evidence(Shortcut s, String screen, List<String> tail, List<String> crash, long nowMs) {
        AgvnEvidence ev = new AgvnEvidence();
        ev.lines.addAll(tail);
        ev.lines.addAll(crash);
        ev.engine = s.getExtra(AgvnGameImporter.EXTRA_ENGINE);
        String crashed = AgvnCrashScan.describe(crash);
        ev.crashed = crashed != null;
        if (crashed != null) ev.params.put("crash", crashed);
        ev.endedByGame = AgvnSessionTrack.endedByGame();
        ev.playerQuit = AgvnSessionTrack.playerQuit();
        ev.started = AgvnSessionTrack.started();
        ev.bigWindowSeen = AgvnSessionTrack.bigWindowSeen();
        ev.seconds = AgvnSessionTrack.seconds(nowMs);
        ev.smallScreen = screenLines(screen) < SMALL_SCREEN_LINES;
        ev.params.put("screen", screen.replace('x', '×'));
        ev.lowRamFreeMb = AgvnSessionTrack.lowRamFreeMb(nowMs, LOW_RAM_RECENT_MS);
        ev.ramSaved = AgvnMemorySaver.ranOut(s);
        ev.frozenS = AgvnSessionTrack.frozenSecondsAtEnd();
        ev.frozenPresses = AgvnSessionTrack.pressesWithoutFrame();
        ev.tilerOoms = AgvnWineTail.get().tilerOoms();
        if (ev.lowRamFreeMb >= 0) ev.params.put("free", String.valueOf(ev.lowRamFreeMb));
        return ev;
    }

    /** The height of "854x480", or a large number when unreadable (never a small screen by mistake). */
    static int screenLines(String size) {
        String[] wh = size == null ? new String[0] : size.toLowerCase().split("x");
        try {
            return wh.length == 2 ? Integer.parseInt(wh[1].trim()) : Integer.MAX_VALUE;
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** The last lines of the engine's logs (Unity's Player.log, Godot's godot.log...) written during this session. */
    private static List<String> engineLogTails(Shortcut s, long sinceMs) {
        List<String> lines = new java.util.ArrayList<>();
        List<File> logs = AgvnEngineLogs.of(s);
        for (File roaming : AgvnEngineLogs.scanned(s)) logs.addAll(AgvnGodotFiles.logs(roaming, sinceMs));
        for (File f : logs) {
            if (!f.isFile() || (sinceMs > 0 && f.lastModified() < sinceMs - AgvnEngineLogs.OLD_SLACK_MS)) continue;
            try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
                byte[] buf = new byte[(int) Math.min(in.length(), ENGINE_LOG_BYTES)];
                in.seek(in.length() - buf.length);
                in.readFully(buf);
                lines.addAll(Arrays.asList(new String(buf, StandardCharsets.UTF_8).split("\r?\n")));
            } catch (IOException ignored) {
                // a log less to read
            }
        }
        return lines;
    }
}
