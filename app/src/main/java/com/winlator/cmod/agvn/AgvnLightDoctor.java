/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import com.winlator.cmod.SettingsFragment;
import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

/**
 * "Tự sửa lỗi" for "Chạy nhẹ" games: when the library comes back after one ({@link AgvnLightSession}), how it ended.
 * The runner's process may have crashed, frozen or been ended by Android, Ren'Py may have written a traceback, an HTML
 * game may have stopped on a script error or lost its page. The problem is left for {@link AgvnDoctorDialog}, like a
 * Windows game's. Errors the runner already explained with its own fixes (mkxp-z's, Ren'Py's first screen) are not
 * asked again.
 */
public final class AgvnLightDoctor {
    private static final String TAG = "AGVN";
    /** ApplicationExitInfo: REASON_CRASH, REASON_CRASH_NATIVE, REASON_ANR. */
    static final int REASON_CRASH = 4, REASON_CRASH_NATIVE = 5, REASON_ANR = 6;

    private AgvnLightDoctor() {}

    /** At the library (or Big Picture), off the UI thread. Never throws. */
    public static synchronized void check(Context ctx) {
        try {
            Properties p = AgvnLightSession.read(ctx);
            if (p.isEmpty()) return;
            boolean ended = "1".equals(p.getProperty(AgvnLightSession.ENDED));
            int pid = (int) number(p.getProperty(AgvnLightSession.PID));
            if (!ended && alive(ctx, pid, p.getProperty(AgvnLightSession.PROCESS, ""))) return; // still playing
            AgvnLightSession.clear(ctx);
            long start = number(p.getProperty(AgvnLightSession.START));
            if (System.currentTimeMillis() - start > AgvnDoctor.KILL_RECENT_MS) return;
            if ("1".equals(p.getProperty(AgvnLightSession.SHOWN))) return; // the runner said it, with its fixes
            Shortcut s = AgvnRelaunch.find(ctx, (int) number(p.getProperty(AgvnLightSession.CONTAINER)),
                    p.getProperty(AgvnLightSession.SHORTCUT, ""));
            if (s == null) return;
            AgvnEvidence ev = evidence(p, ended ? null : exitOf(ctx, start, pid), renpyError(p, start));
            ev.engine = s.getExtra(AgvnGameImporter.EXTRA_ENGINE);
            ev.ramSaved = AgvnMemorySaver.ranOut(s);
            AgvnDoctor.diagnoseLight(ctx, s, ev);
        } catch (RuntimeException e) {
            Log.w(TAG, "doctor: light game end not read", e);
        }
    }

    /** What the session left: its runner, first screen, error, lost page, and how Android ended it ({@code exit}). */
    static AgvnEvidence evidence(Properties p, AgvnExitReason.Exit exit, String renpyError) {
        AgvnEvidence ev = new AgvnEvidence();
        ev.runner = p.getProperty(AgvnLightSession.RUNNER, "");
        ev.params.put("runner", runnerName(ev.runner));
        ev.started = "1".equals(p.getProperty(AgvnLightSession.STARTED));
        ev.pageCrash = "1".equals(p.getProperty(AgvnLightSession.PAGE_CRASH));
        String error = renpyError != null ? renpyError : p.getProperty(AgvnLightSession.ERROR, "");
        if (!error.isEmpty()) {
            ev.scriptError = true;
            ev.params.put("error", error);
        }
        if (exit != null) {
            ev.killedReason = exit.reason;
            ev.killedImportance = exit.importance;
            ev.crashed = exit.reason == REASON_CRASH || exit.reason == REASON_CRASH_NATIVE;
            ev.frozen = exit.reason == REASON_ANR;
            if (ev.crashed) ev.params.put("crash", AgvnExitReason.describe(exit.reason));
        }
        return ev;
    }

    /** How Android ended the runner's process: its record is written a moment after the library comes back. */
    private static AgvnExitReason.Exit exitOf(Context ctx, long start, int pid) {
        for (int i = 0; ; i++) {
            AgvnExitReason.Exit exit = AgvnExitReason.exit(ctx, start, pid);
            if (exit != null || i >= 8 || Build.VERSION.SDK_INT < 30) return exit;
            SystemClock.sleep(250);
        }
    }

    static String runnerName(String runner) {
        switch (runner) {
            case AgvnHtmlGame.RUNNER_RENPY: return "Ren'Py";
            case AgvnHtmlGame.RUNNER_RGSS: return "mkxp-z (RPG Maker)";
            case AgvnHtmlGame.RUNNER_HTML: return "WebView (HTML)";
            default: return runner;
        }
    }

    /** The error of a Ren'Py traceback.txt written during the session, or null. */
    private static String renpyError(Properties p, long start) {
        if (!AgvnHtmlGame.RUNNER_RENPY.equals(p.getProperty(AgvnLightSession.RUNNER))) return null;
        String dir = p.getProperty(AgvnLightSession.GAME_DIR);
        File traceback = new File(AgvnRenpyGame.publicDir(new File(SettingsFragment.DEFAULT_WINLATOR_PATH),
                dir != null ? new File(dir) : null), "traceback.txt");
        if (!traceback.isFile() || traceback.lastModified() < start - AgvnEngineLogs.OLD_SLACK_MS) return null;
        try {
            return AgvnCrashScan.renpyError(new String(Files.readAllBytes(traceback.toPath()), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    /** True while the runner's process (pid, name) still lives: an HTML game in this very process is alive too. */
    static boolean alive(Context ctx, int pid, String process) {
        if (pid <= 0) return false;
        if (pid == Process.myPid()) return true;
        List<ActivityManager.RunningAppProcessInfo> running = ctx.getSystemService(ActivityManager.class).getRunningAppProcesses();
        if (running != null) for (ActivityManager.RunningAppProcessInfo info : running) {
            if (info.pid == pid && info.processName.equals(process)) return true;
        }
        return false;
    }

    private static long number(String value) {
        try {
            return value != null ? Long.parseLong(value.trim()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
