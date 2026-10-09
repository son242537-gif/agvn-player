/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.EnvVars;

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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.winlator.cmod.agvn.AgvnSessionNotes.files;
import static com.winlator.cmod.agvn.AgvnSessionNotes.text;
import static com.winlator.cmod.agvn.AgvnSessionNotes.value;

/**
 * A folder per play session in AGVN-Player/logs/&lt;game&gt;/&lt;yyyyMMdd-HHmmss&gt;/ with the engine's logs, the graphics
 * environment, events (RAM warnings) and a summary with how the session ended. A session Android killed (no normal
 * exit) is finished at the next app start with the reason Android recorded. The 5 newest sessions per game stay.
 */
public final class AgvnSessionLog {
    private static final String TAG = "AGVN";
    static final String RUNNING = "dang-chay.txt", SUMMARY = "tom-tat.txt", EVENTS = "su-kien.txt", ENV = "moi-truong.txt";
    static final String RAM = "ram.txt", HEAT = "nhiet.txt";
    static final String[] ENV_PREFIXES = {"WRAPPER_", "DXVK_", "VKD3D_", "MESA_", "TU_", "ZINK_", "GALLIUM_", "WINE",
            "PROTON_", "GST_", "BOX64_", "FEX_", "LC_ALL", "VK_", "__GL_", "vblank_mode"};
    private static volatile File current;

    private AgvnSessionLog() {}

    public static File root() {
        return new File(Environment.getExternalStorageDirectory(), "AGVN-Player/logs");
    }

    /** Opens the game's session folder once Wine's environment is set; the engine's log paths are noted now. */
    public static synchronized void start(Shortcut shortcut, EnvVars env) {
        AgvnWineTail.get().reset();
        AgvnSessionTrack.start(System.currentTimeMillis(), AgvnGoodConfig.snapshot(shortcut));
        try {
            File gameLogs = new File(root(), AgvnLogFolders.safeName(shortcut.name));
            File dir = new File(gameLogs, new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()));
            if (!dir.mkdirs() && !dir.isDirectory()) return;
            StringBuilder running = new StringBuilder("start=" + System.currentTimeMillis() + "\ngame=" + shortcut.name + "\n"
                    + "shortcut=" + shortcut.file.getPath() + "\ncontainer=" + shortcut.container.id + "\n");
            for (File f : AgvnEngineLogs.of(shortcut)) running.append("log=").append(f.getPath()).append('\n');
            for (File f : AgvnEngineLogs.scanned(shortcut)) running.append("logscan=").append(f.getPath()).append('\n');
            write(new File(dir, RUNNING), running.toString(), false);
            write(new File(dir, ENV), graphicsEnv(env), false);
            current = dir;
            for (String fact : AgvnGameFacts.of(shortcut)) event(fact); // what runs: exe, engine, DLLs, folders, mods
            String[] notes = {AgvnMemorySaver.takeNote(), AgvnWineMono.takeNote(), AgvnRawMouse.takeNote(),
                    AgvnInputDevices.sessionStart()};
            for (String note : notes) if (note != null) event(note);
            AgvnLogFolders.prune(gameLogs);
            AgvnLogFolders.pruneLoose(root(), System.currentTimeMillis());
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

    /** Wine's own log ("Bật debug Wine"): moved into the session folder when the session ends. */
    public static void addLog(File log) {
        File dir = current;
        if (dir != null && log != null) write(new File(dir, RUNNING), "wine=" + log.getPath() + "\n", true);
    }

    /** Replaces one small file of the running session, e.g. the RAM peaks (no-op without a session). */
    public static void note(String name, String text) {
        File dir = current;
        if (dir != null) write(new File(dir, name), text, false);
    }

    /** End of a game: collects the logs and writes the summary; a crash or Ren'Py error found replaces {@code how}. */
    public static synchronized void finish(Context context, String how) {
        File dir = current;
        current = null;
        if (dir == null) return;
        String notes = read(new File(dir, RUNNING));
        String error = AgvnCrashScan.sessionError(AgvnWineTail.get().crash(), files(notes, "log="), value(notes, "start", 0), dir);
        AgvnWineTail.get().save(dir);
        close(context, dir, error != null ? error : how);
    }

    /** The player swiped AGVN away (NotificationService.onTaskRemoved then ends the process): asked about only for an error. */
    public static void removedByPlayer(Context context) {
        File dir = current;
        if (dir != null) write(new File(dir, RUNNING), AgvnSessionNotes.REMOVED + "=" + System.currentTimeMillis() + "\n", true);
        if (dir != null) AgvnWineTail.get().save(dir); // Wine's last lines: nothing of this process is left after it
        AgvnLightSession.removedByPlayer(context);
    }

    /** At app start: finishes sessions whose app process died, with the reason Android recorded or the game's crash. */
    public static synchronized void finishPending(Context context) {
        File[] games = root().listFiles(File::isDirectory);
        if (games == null) return;
        for (File game : games) {
            File[] sessions = game.listFiles(File::isDirectory);
            if (sessions == null) continue;
            for (File dir : sessions) {
                File running = new File(dir, RUNNING);
                if (!running.isFile() || dir.equals(current)) continue;
                String notes = read(running);
                long start = value(notes, "start", 0);
                String how = AgvnSessionNotes.removedByPlayer(notes) ? AgvnSessionNotes.REMOVED_HOW // the player's end
                        : AgvnExitReason.after(context, start);
                String error = AgvnCrashScan.sessionError(Collections.emptyList(), files(notes, "log="), start, dir);
                close(context, dir, error != null ? error + "; sau đó: " + how : how); // the game's crash comes first
                AgvnDoctor.afterKill(context, notes, dir); // the crash, else Android's end: why, if it can be helped
            }
        }
    }

    private static void close(Context context, File dir, String how) {
        File running = new File(dir, RUNNING);
        String notes = read(running);
        long start = value(notes, "start", 0);
        AgvnEngineLogs.Copied engine = AgvnEngineLogs.copy(AgvnSessionNotes.engineLogs(notes, start), dir, start);
        AgvnEngineLogs.Copied wine = AgvnEngineLogs.copy(files(notes, "wine="), dir, 0);
        for (File f : wine.copied) f.delete(); // a copy is in the session folder; the logs/ root does not grow
        write(new File(dir, SUMMARY), summary(context, dir, notes, how, engine, wine), false);
        running.delete();
    }

    /**
     * A session still running, as it is now ("Gửi nhật ký" while the game plays): its engine and Wine logs so far and a
     * summary go to {@code into}. The session itself goes on untouched.
     */
    static void snapshot(Context context, File dir, File into) {
        String notes = read(new File(dir, RUNNING));
        long start = value(notes, "start", 0);
        AgvnEngineLogs.Copied engine = AgvnEngineLogs.copy(AgvnSessionNotes.engineLogs(notes, start), into, start);
        AgvnEngineLogs.Copied wine = AgvnEngineLogs.copy(files(notes, "wine="), into, 0);
        if (dir.equals(current)) AgvnWineTail.get().save(into);
        String how = "chưa kết thúc, game vẫn đang chạy (nhật ký gom lúc "
                + new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date()) + ")";
        write(new File(into, SUMMARY), summary(context, dir, notes, how, engine, wine), false);
    }

    private static String summary(Context context, File dir, String notes, String how, AgvnEngineLogs.Copied engine,
                                  AgvnEngineLogs.Copied wine) {
        long start = value(notes, "start", 0);
        int copied = engine.copied.size() + wine.copied.size();
        String old = AgvnEngineLogs.oldNote(engine.old);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        return "AGVN Player: nhật ký phiên chơi\n"
                + "Game: " + text(notes, "game") + "\n"
                + "Bắt đầu: " + (start > 0 ? fmt.format(new Date(start)) : "?") + "\n"
                + "Kết thúc (ghi lúc): " + fmt.format(new Date()) + "\n"
                + "Cách kết thúc: " + how + "\n"
                + "App: " + AgvnUpdater.installedName(context) + " (" + AgvnUpdater.installedCode(context) + ")\n"
                + "Máy: " + Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE + "\n"
                + "Nhật ký engine đã chép: " + copied + " file" + (old.isEmpty() ? "" : "; bỏ qua: " + old) + "\n"
                + "RAM: " + (new File(dir, RAM).isFile() ? read(new File(dir, RAM)).trim() : "không đo") + "\n"
                + "Nhiệt: " + (new File(dir, HEAT).isFile() ? read(new File(dir, HEAT)).trim() : "không đo") + "\n"
                + "Sự kiện: " + (new File(dir, EVENTS).isFile() ? EVENTS : "không có") + "; môi trường đồ hoạ: " + ENV + "\n";
    }

    /** Graphics and Wine variables, one per line, sorted. */
    static String graphicsEnv(EnvVars env) {
        List<String> lines = new ArrayList<>();
        for (String name : env) for (String p : ENV_PREFIXES) if (name.startsWith(p)) { lines.add(name + "=" + env.get(name)); break; }
        String[] sorted = lines.toArray(new String[0]);
        Arrays.sort(sorted);
        return String.join("\n", sorted) + "\n";
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
