/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.xenvironment.ImageFs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * A folder per play session in AGVN-Player/logs/&lt;game&gt;/&lt;yyyyMMdd-HHmmss&gt;/ with the engine's logs, the graphics
 * environment, events (RAM warnings) and a summary with how the session ended. A session Android killed (no normal
 * exit) is finished at the next app start with the reason Android recorded. The 5 newest sessions per game stay.
 */
public final class AgvnSessionLog {
    private static final String TAG = "AGVN";
    static final String RUNNING = "dang-chay.txt", SUMMARY = "tom-tat.txt", EVENTS = "su-kien.txt", ENV = "moi-truong.txt";
    static final String RAM = "ram.txt";
    static final int KEEP = 5;
    static final String[] ENV_PREFIXES = {"WRAPPER_", "DXVK_", "VKD3D_", "MESA_", "TU_", "ZINK_", "GALLIUM_", "WINE",
            "PROTON_", "GST_", "BOX64_", "FEX_", "LC_ALL", "VK_", "__GL_", "vblank_mode"};
    private static volatile File current;

    private AgvnSessionLog() {}

    public static File root() {
        return new File(Environment.getExternalStorageDirectory(), "AGVN-Player/logs");
    }

    /** Opens the game's session folder once Wine's environment is set; the engine's log paths are noted now. */
    public static synchronized void start(Shortcut shortcut, EnvVars env) {
        try {
            File exe = new File(shortcut.path.replace("\"", ""));
            String gameDirPath = shortcut.getExtra(AgvnGameImporter.EXTRA_GAME_DIR);
            File gameDir = !gameDirPath.isEmpty() ? new File(gameDirPath) : exe.getParentFile();
            File profile = new File(shortcut.container.getRootDir(), ".wine/drive_c/users/" + ImageFs.USER);
            File gameLogs = new File(root(), safeName(shortcut.name));
            File dir = new File(gameLogs, new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()));
            if (!dir.mkdirs() && !dir.isDirectory()) return;
            StringBuilder running = new StringBuilder("start=" + System.currentTimeMillis() + "\ngame=" + shortcut.name + "\n");
            for (File f : AgvnEngineLogs.candidates(exe, gameDir, shortcut.getExtra(AgvnGameImporter.EXTRA_ENGINE), profile))
                running.append("log=").append(f.getPath()).append('\n');
            write(new File(dir, RUNNING), running.toString(), false);
            write(new File(dir, ENV), graphicsEnv(env), false);
            current = dir;
            prune(gameLogs);
        } catch (Exception e) {
            Log.w(TAG, "session log not started", e);
        }
    }

    /** Adds a timestamped line to the running session's events (no-op without a session). */
    public static void event(String text) {
        File dir = current;
        if (dir == null) return;
        String time = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
        write(new File(dir, EVENTS), "[" + time + "] " + text + "\n", true);
    }

    /** Replaces one small file of the running session, e.g. the RAM peaks (no-op without a session). */
    public static void note(String name, String text) {
        File dir = current;
        if (dir != null) write(new File(dir, name), text, false);
    }

    /** Normal end of a game: collects the engine's logs and writes the summary. */
    public static synchronized void finish(Context context, String how) {
        File dir = current;
        current = null;
        if (dir != null) close(context, dir, how);
    }

    /** At app start: finishes sessions whose app process died, with the reason Android recorded. */
    public static synchronized void finishPending(Context context) {
        File[] games = root().listFiles(File::isDirectory);
        if (games == null) return;
        for (File game : games) {
            File[] sessions = game.listFiles(File::isDirectory);
            if (sessions == null) continue;
            for (File dir : sessions) {
                File running = new File(dir, RUNNING);
                if (!running.isFile() || dir.equals(current)) continue;
                close(context, dir, AgvnExitReason.after(context, value(read(running), "start", 0)));
            }
        }
    }

    private static void close(Context context, File dir, String how) {
        File running = new File(dir, RUNNING);
        String notes = read(running);
        List<File> logs = new ArrayList<>();
        for (String line : notes.split("\n")) if (line.startsWith("log=")) logs.add(new File(line.substring(4)));
        int copied = AgvnEngineLogs.copy(logs, dir);
        long start = value(notes, "start", 0);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        String summary = "AGVN Player: nhật ký phiên chơi\n"
                + "Game: " + text(notes, "game") + "\n"
                + "Bắt đầu: " + (start > 0 ? fmt.format(new Date(start)) : "?") + "\n"
                + "Kết thúc (ghi lúc): " + fmt.format(new Date()) + "\n"
                + "Cách kết thúc: " + how + "\n"
                + "App: " + AgvnUpdater.installedName(context) + " (" + AgvnUpdater.installedCode(context) + ")\n"
                + "Máy: " + Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE + "\n"
                + "Nhật ký engine đã chép: " + copied + " file\n"
                + "RAM: " + (new File(dir, RAM).isFile() ? read(new File(dir, RAM)).trim() : "không đo") + "\n"
                + "Sự kiện: " + (new File(dir, EVENTS).isFile() ? EVENTS : "không có") + "; môi trường đồ hoạ: " + ENV + "\n";
        write(new File(dir, SUMMARY), summary, false);
        running.delete();
    }

    /** Graphics and Wine variables, one per line, sorted. */
    static String graphicsEnv(EnvVars env) {
        List<String> lines = new ArrayList<>();
        for (String name : env) for (String p : ENV_PREFIXES) if (name.startsWith(p)) { lines.add(name + "=" + env.get(name)); break; }
        String[] sorted = lines.toArray(new String[0]);
        Arrays.sort(sorted);
        return String.join("\n", sorted) + "\n";
    }

    /** Keeps the {@link #KEEP} newest session folders (their names sort by time). */
    static void prune(File gameLogs) {
        File[] sessions = gameLogs.listFiles(File::isDirectory);
        if (sessions == null || sessions.length <= KEEP) return;
        Arrays.sort(sessions, (a, b) -> b.getName().compareTo(a.getName()));
        for (int i = KEEP; i < sessions.length; i++) deleteTree(sessions[i]);
    }

    static String safeName(String name) {
        String s = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return s.isEmpty() ? "game" : s;
    }

    private static void deleteTree(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteTree(c);
        f.delete();
    }

    private static long value(String notes, String key, long fallback) {
        try {
            return Long.parseLong(text(notes, key));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String text(String notes, String key) {
        for (String line : notes.split("\n")) if (line.startsWith(key + "=")) return line.substring(key.length() + 1);
        return "";
    }

    private static String read(File f) {
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static void write(File f, String text, boolean append) {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f, append), StandardCharsets.UTF_8)) {
            w.write(text);
        } catch (IOException e) {
            Log.w(TAG, "session log not written: " + f, e);
        }
    }
}
