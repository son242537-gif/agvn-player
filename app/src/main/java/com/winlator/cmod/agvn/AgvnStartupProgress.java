/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.ProcessHelper;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * How a slow Windows game start is going. A Ren'Py game can spend minutes in its init code on a phone (Lo Se Sb: 141 s
 * on a PC, about 520 s here) while the screen shows its presplash and the FPS reads 0. Once the game has not drawn for
 * {@link #QUIET_MS}, the status line shows the time so far every {@link #TICK_MS} ("Đang khởi động 4:12"), the game's
 * last slow start ("· lần trước 1:58", {@link AgvnStartTimes}) and, with "Bật debug Wine" on, the engine log's last
 * line. It goes when the game draws steadily, its log says the interface is up, the game draws in answer to the player
 * (a visual novel that redraws only when tapped), or after {@link #MAX_MS}. A log that has not moved for
 * {@link #STALL_MS} while Wine used under 5% of a core reads "Có thể đang treo". The static helpers are pure Java.
 */
public final class AgvnStartupProgress {
    private static final String TAG = "AGVN";
    static final long TICK_MS = 2000, QUIET_MS = 10_000, STALL_MS = 60_000, MAX_MS = 15 * 60_000;
    /** Window updates per tick, two ticks running, that mean the game draws (a presplash draws once). */
    static final int DRAWING = 4;
    static final int TAIL_BYTES = 4096, LINE_CHARS = 80;
    /** A window update this soon after the player's input is the game answering them: it is up. */
    static final long ANSWER_MS = 1500;
    /** Ren'Py's log once its first screen is up (Ren'Py 6 to 8). */
    private static final String[] READY = {"Interface start took", "Total time until interface ready"};
    /** Why the game does not go on, said instead of "Đang khởi động" (a string with the time so far), or 0. */
    private static volatile int problem;

    private final Activity activity;
    private final AgvnStatusLine line;
    private final List<File> logs;
    private final String game;
    private final long lastStartMs;
    private final long startMs = System.currentTimeMillis(), startUptimeMs = SystemClock.uptimeMillis();
    private final AtomicInteger updates = new AtomicInteger();
    private final ArrayDeque<long[]> cpu = new ArrayDeque<>(); // {time ms, Wine's CPU ticks}
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "AgvnStartupProgress");
        t.setDaemon(true);
        return t;
    });
    private int drawingTicks;
    private String lastLine = "";
    private long lastChangeMs = startMs;
    private volatile boolean done, answered;

    private AgvnStartupProgress(Activity activity, AgvnStatusLine line, List<File> logs, String game) {
        this.activity = activity;
        this.line = line;
        this.logs = logs;
        this.game = game;
        lastStartMs = AgvnStartTimes.last(activity, game);
    }

    /** Starts watching as Wine starts the game. */
    public static AgvnStartupProgress start(Activity activity, Shortcut shortcut, AgvnStatusLine line) {
        problem = 0;
        AgvnStartupProgress p = new AgvnStartupProgress(activity, line, AgvnEngineLogs.of(shortcut), shortcut.file.getPath());
        p.timer.scheduleWithFixedDelay(p::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
        return p;
    }

    /** A game window drew (any thread, every frame: kept cheap). */
    public void onWindowUpdate() {
        if (done) return;
        updates.incrementAndGet();
        if (answers(AgvnMemoryWatch.playerInputMs(), SystemClock.uptimeMillis(), startUptimeMs)) answered = true;
    }

    /** True when a window update at {@code nowMs} answers the player's input at {@code inputMs} (uptime clock). */
    static boolean answers(long inputMs, long nowMs, long startMs) {
        return inputMs > startMs && nowMs - inputMs >= 0 && nowMs - inputMs <= ANSWER_MS;
    }

    /** Why the game does not go on: {@code stringRes} ("Phim trong game không phát được (%1$s)"), 0 for none. */
    public static void problem(int stringRes) {
        problem = stringRes;
    }

    public void stop() {
        if (done) return;
        done = true;
        timer.shutdownNow();
        activity.runOnUiThread(() -> line.setProgress(null));
    }

    private void tick() {
        try {
            long now = System.currentTimeMillis(), elapsed = now - startMs;
            drawingTicks = updates.getAndSet(0) >= DRAWING ? drawingTicks + 1 : 0;
            String tail = tail(log());
            boolean up = drawingTicks >= 2 || answered || ready(tail);
            if (up || elapsed >= MAX_MS) {
                if (up) AgvnStartTimes.remember(activity, game, elapsed);
                stop();
                return;
            }
            String last = lastLine(tail);
            if (!last.equals(lastLine)) {
                lastLine = last;
                lastChangeMs = now;
            }
            cpu.addLast(new long[]{now, wineCpuTicks()});
            while (cpu.size() > 2 && cpu.peekFirst()[0] < now - STALL_MS) cpu.removeFirst();
            if (elapsed < QUIET_MS) return;
            long[] first = cpu.peekFirst(), latest = cpu.peekLast();
            boolean stalled = now - lastChangeMs >= STALL_MS
                    && share(latest[1] - first[1], latest[0] - first[0], Os.sysconf(OsConstants._SC_CLK_TCK)) < 0.05;
            // the log's last line only for "Bật debug Wine": with logs off, no log text on the game
            boolean debug = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("enable_wine_debug", false);
            int lead = problem != 0 ? problem : stalled ? R.string.agvn_startup_stalled : R.string.agvn_startup_progress;
            String text = activity.getString(lead, clock(elapsed))
                    + (lastStartMs > 0 ? " · " + activity.getString(R.string.agvn_startup_last, clock(lastStartMs)) : "")
                    + (last.isEmpty() || !debug ? "" : " · " + last);
            activity.runOnUiThread(() -> {
                if (!done) line.setProgress(text);
            });
        } catch (Throwable t) {
            Log.w(TAG, "startup progress", t); // never in the game's way
        }
    }

    /** The engine log written during this start (Ren'Py's log.txt is rewritten at each start), or null. */
    private File log() {
        for (File f : logs) if (f.isFile() && f.lastModified() >= startMs - AgvnEngineLogs.OLD_SLACK_MS) return f;
        return null;
    }

    private static String tail(File f) throws IOException {
        if (f == null) return "";
        try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
            long size = in.length();
            byte[] buf = new byte[(int) Math.min(size, TAIL_BYTES)];
            in.seek(size - buf.length);
            in.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        }
    }

    private static long wineCpuTicks() {
        long sum = 0;
        for (String pid : ProcessHelper.listRunningWineProcesses()) {
            try {
                sum += cpuTicks(new String(Files.readAllBytes(new File("/proc/" + pid + "/stat").toPath()), StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException ignored) {
                // the process just ended
            }
        }
        return sum;
    }

    /** utime + stime of a /proc/PID/stat line, in clock ticks (the name in parentheses may hold spaces). */
    static long cpuTicks(String stat) {
        String[] f = stat.substring(stat.lastIndexOf(')') + 2).trim().split("\\s+");
        return Long.parseLong(f[11]) + Long.parseLong(f[12]); // fields 14 and 15 of the whole line
    }

    /** CPU used over a span, in cores (1.0 = one core busy all the time). */
    static double share(long ticks, long ms, long clockTicksPerSecond) {
        return ms <= 0 || clockTicksPerSecond <= 0 ? 1 : ticks * 1000.0 / clockTicksPerSecond / ms;
    }

    static boolean ready(String tail) {
        for (String marker : READY) if (tail.contains(marker)) return true;
        return false;
    }

    /** The last line with text, at most {@link #LINE_CHARS} long. */
    static String lastLine(String tail) {
        String[] lines = tail.split("\r?\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String s = lines[i].trim();
            if (!s.isEmpty()) return s.length() > LINE_CHARS ? s.substring(0, LINE_CHARS) + "…" : s;
        }
        return "";
    }

    /** "4:12". */
    static String clock(long ms) {
        long s = ms / 1000;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
