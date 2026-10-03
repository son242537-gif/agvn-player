/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Tự sửa lỗi": when a Windows game ends badly, finds what went wrong ({@link AgvnProblemCatalog}) and leaves it for
 * the library to ask the player about after the app restarts ({@link AgvnDoctorDialog}). A game that ended well keeps
 * its settings as the ones that work ({@link AgvnGoodConfig}).
 */
public final class AgvnDoctor {
    private static final String TAG = "AGVN";
    static final String PENDING = "agvn/doctor-pending.properties", PARAM = "param.";
    private static final Pattern GODOT = Pattern.compile("Godot Engine v(\\d+)\\.");
    /** Settings a lower Đồ họa step changes: when only they changed and the game then refused to start, the screen did it. */
    private static final List<String> SCREEN_KEYS = Arrays.asList("screenSize", AgvnQuality.EXTRA_QUALITY, "nativeFpsLimit",
            "nativeFpsLimiterEnabled");
    static final int ENGINE_LOG_BYTES = 16 * 1024, SMALL_SCREEN_LINES = 480;
    static final long KILL_RECENT_MS = 24 * 3600_000L;
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
        return new File(ctx.getFilesDir(), PENDING).isFile();
    }

    /** The game ends (XServerDisplayActivity.exit), before its session log closes. Never throws. */
    public static void afterGame(Context ctx, Shortcut s) {
        if (s == null) return;
        try {
            Map<String, String> ranWith = AgvnSessionTrack.settings();
            if (ranWith.isEmpty()) ranWith = AgvnGoodConfig.snapshot(s);
            String screen = ranWith.containsKey("screenSize") ? ranWith.get("screenSize") : s.container.getScreenSize();
            AgvnEvidence ev = evidence(s, screen, AgvnWineTail.get().lines(), AgvnWineTail.get().crash(), System.currentTimeMillis());
            ev.lines.addAll(engineLogTails(s, AgvnSessionTrack.startMs()));
            diagnose(ctx, s, ev, ranWith);
        } catch (RuntimeException e) {
            Log.w(TAG, "doctor: game end not read", e);
        }
    }

    /** A session Android ended, found at the next start (AgvnSessionLog.finishPending); its notes name the game. */
    static void afterKill(Context ctx, String notes) {
        try {
            long start = AgvnSessionNotes.value(notes, "start", 0);
            if (System.currentTimeMillis() - start > KILL_RECENT_MS) return; // too long ago to ask about
            AgvnExitReason.Exit exit = AgvnExitReason.exit(ctx, start);
            Shortcut s = exit == null ? null : AgvnRelaunch.find(ctx, (int) AgvnSessionNotes.value(notes, "container", -1),
                    AgvnSessionNotes.text(notes, "shortcut"));
            if (s == null) return;
            AgvnEvidence ev = new AgvnEvidence();
            ev.engine = s.getExtra(AgvnGameImporter.EXTRA_ENGINE);
            ev.killedReason = exit.reason;
            ev.killedImportance = exit.importance;
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
        writePending(ctx, s, f);
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
        String godot = godotMajor(ev.lines);
        if (godot != null) ev.params.put("godot", godot);
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

    /** "4" from Godot's first line ("Godot Engine v4.3.stable.official..."), or null. */
    static String godotMajor(List<String> lines) {
        for (String line : lines) {
            Matcher m = GODOT.matcher(line);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    /** The last lines of the engine's logs (Unity's Player.log...) written during this session. */
    private static List<String> engineLogTails(Shortcut s, long sinceMs) {
        List<String> lines = new java.util.ArrayList<>();
        for (File f : AgvnEngineLogs.of(s)) {
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

    private static void writePending(Context ctx, Shortcut s, AgvnProblemCatalog.Finding f) {
        Properties p = new Properties();
        p.setProperty("container", String.valueOf(s.container.id));
        p.setProperty("shortcut", s.file.getPath());
        p.setProperty("problem", f.id());
        p.setProperty("time", String.valueOf(System.currentTimeMillis()));
        for (Map.Entry<String, String> e : f.params.entrySet()) p.setProperty(PARAM + e.getKey(), e.getValue());
        File file = new File(ctx.getFilesDir(), PENDING);
        File dir = file.getParentFile();
        if (dir != null) dir.mkdirs();
        AgvnPropsFile.store(p, file, "AGVN Player: a problem to ask the player about");
        Log.i(TAG, "doctor: " + f.id() + " for " + s.name);
    }
}
