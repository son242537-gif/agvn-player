/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.xenvironment.ImageFs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The log files game engines write, copied into the session folder when a game ends. They sit inside the app's
 * private container (Unity's Player.log is under the Windows user folder), where the player and adb cannot reach
 * them. Big logs keep their start (engine and renderer setup) and their end (the error).
 */
final class AgvnEngineLogs {
    static final long MAX_BYTES = 2L * 1024 * 1024;
    static final int HEAD_BYTES = 256 * 1024;
    /** A log of the session may be this much older than its start (the engine opens it as Wine starts). */
    static final long OLD_SLACK_MS = 5000;

    /** What {@link #copy} did. */
    static final class Copied {
        final List<File> copied = new ArrayList<>();
        /** Written before the session began, so not about it (a traceback.txt of last month): not copied. */
        final List<File> old = new ArrayList<>();
    }

    private AgvnEngineLogs() {}

    /** The log files the shortcut's game engine may write. */
    static List<File> of(Shortcut shortcut) {
        return candidates(exe(shortcut), gameDir(shortcut), shortcut.getExtra(AgvnGameImporter.EXTRA_ENGINE), profile(shortcut));
    }

    /** The game's exe as a file on the phone. */
    static File exe(Shortcut shortcut) {
        return new File(shortcut.path.replace("\"", ""));
    }

    /** The game's folder: the one it was imported from, else the exe's. */
    static File gameDir(Shortcut shortcut) {
        String path = shortcut.getExtra(AgvnGameImporter.EXTRA_GAME_DIR);
        return !path.isEmpty() ? new File(path) : exe(shortcut).getParentFile();
    }

    /** Windows' users/xuser of the game's container. */
    private static File profile(Shortcut shortcut) {
        return new File(shortcut.container.getRootDir(), ".wine/drive_c/users/" + ImageFs.USER);
    }

    /**
     * Folders searched for the engine's logs when the session ends, as their names are the project's, which only the
     * game knows: a Godot game's %APPDATA% ({@link AgvnGodotFiles#logs}).
     */
    static List<File> scanned(Shortcut shortcut) {
        List<File> out = new ArrayList<>();
        if ("GODOT".equals(shortcut.getExtra(AgvnGameImporter.EXTRA_ENGINE))) out.add(new File(profile(shortcut), "AppData/Roaming"));
        return out;
    }

    /** Files the engine may write; missing ones are skipped when copying. {@code profile} = Windows users/xuser. */
    static List<File> candidates(File exe, File gameDir, String engine, File profile) {
        List<File> out = new ArrayList<>();
        File exeDir = exe.getParentFile();
        switch (engine) {
            case "UNITY": {
                String[] names = AgvnSaveLocations.unityNames(exe);
                if (names != null) {
                    File dir = new File(profile, "AppData/LocalLow/" + names[0] + "/" + names[1]);
                    out.add(new File(dir, "Player.log"));
                    out.add(new File(dir, "Player-prev.log"));
                }
                String base = exe.getName().replaceFirst("(?i)\\.exe$", "");
                out.add(new File(exeDir, base + "_Data/output_log.txt")); // Unity 5 to 2017
                break;
            }
            case "UNREAL": {
                if (gameDir != null) {
                    String exePath = exe.getPath(), root = gameDir.getPath() + "/";
                    String project = UeIniWriter.projectName(gameDir, exePath.startsWith(root) ? exePath.substring(root.length()) : exe.getName());
                    out.add(new File(profile, "AppData/Local/" + project + "/Saved/Logs/" + project + ".log"));
                    out.add(new File(gameDir, project + "/Saved/Logs/" + project + ".log"));
                }
                break;
            }
            case "RENPY":
                for (String name : new String[]{"log.txt", "traceback.txt", "errors.txt"}) out.add(new File(exeDir, name));
                break;
            default:
                break;
        }
        out.add(new File(exeDir, "Log.txt")); // DxLib (WOLF RPG and many doujin engines) logs next to the exe
        out.add(new File(exeDir, "BepInEx/LogOutput.log")); // mod loaders next to the exe: BepInEx (winhttp.dll),
        out.add(new File(exeDir, "MelonLoader/Latest.log")); // MelonLoader (version.dll)
        out.add(new File(exeDir, "ue4ss/UE4SS.log")); // and UE4SS (dwmapi.dll) of Unreal games
        out.add(new File(exeDir, "AGVN-cheat.log")); // AGVN's cheat (agvncheat.dll) of AGVN game packages
        return out;
    }

    /**
     * Copies each existing candidate into {@code dir} (a big one shortened). Leaves out a file last written before
     * {@code sinceMs} (0: no limit), and a file already met under another name: on /sdcard, Ren'Py's "log.txt" and
     * DxLib's "Log.txt" are one file.
     */
    static Copied copy(List<File> files, File dir, long sinceMs) {
        Copied result = new Copied();
        List<File> seen = new ArrayList<>();
        for (File src : files) {
            if (!src.isFile() || sameAsAny(src, seen)) continue;
            seen.add(src);
            if (sinceMs > 0 && src.lastModified() < sinceMs - OLD_SLACK_MS) {
                result.old.add(src);
                continue;
            }
            String name = uniqueName(dir, src.getName());
            try (OutputStream out = new FileOutputStream(new File(dir, name))) {
                copyShortened(src, out);
                result.copied.add(src);
            } catch (IOException ignored) {
                // an unreadable log is skipped; the summary still lists the session
            }
        }
        return result;
    }

    /** For the summary: "traceback.txt (cũ, 22/08/2026 – không phải lỗi phiên này)", or "" when none was old. */
    static String oldNote(List<File> old) {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat day = new SimpleDateFormat("dd/MM/yyyy", Locale.ROOT);
        for (File f : old) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(f.getName()).append(" (cũ, ").append(day.format(new Date(f.lastModified()))).append(" – không phải lỗi phiên này)");
        }
        return sb.toString();
    }

    private static boolean sameAsAny(File file, List<File> seen) {
        for (File other : seen) {
            try {
                if (Files.isSameFile(file.toPath(), other.toPath())) return true;
            } catch (IOException | RuntimeException ignored) {
                // cannot tell: copy both
            }
        }
        return false;
    }

    private static String uniqueName(File dir, String name) {
        if (!new File(dir, name).exists()) return name;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; ; i++) if (!new File(dir, base + "-" + i + ext).exists()) return base + "-" + i + ext;
    }

    /** The whole file, or its first {@link #HEAD_BYTES} and last bytes up to {@link #MAX_BYTES} in total. */
    static void copyShortened(File src, OutputStream out) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(src, "r")) {
            long size = in.length();
            if (size <= MAX_BYTES) {
                pump(in, out, size);
                return;
            }
            pump(in, out, HEAD_BYTES);
            long tail = MAX_BYTES - HEAD_BYTES;
            out.write(("\n\n[... AGVN: bỏ bớt " + (size - MAX_BYTES) + " byte ở giữa ...]\n\n").getBytes(StandardCharsets.UTF_8));
            in.seek(size - tail);
            pump(in, out, tail);
        }
    }

    private static void pump(RandomAccessFile in, OutputStream out, long count) throws IOException {
        byte[] buf = new byte[64 * 1024];
        while (count > 0) {
            int n = in.read(buf, 0, (int) Math.min(buf.length, count));
            if (n < 0) return;
            out.write(buf, 0, n);
            count -= n;
        }
    }
}
