/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The log files game engines write, copied into the session folder when a game ends. They sit inside the app's
 * private container (Unity's Player.log is under the Windows user folder), where the player and adb cannot reach
 * them. Big logs keep their start (engine and renderer setup) and their end (the error).
 */
final class AgvnEngineLogs {
    static final long MAX_BYTES = 2L * 1024 * 1024;
    static final int HEAD_BYTES = 256 * 1024;

    private AgvnEngineLogs() {}

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
        return out;
    }

    /** Copies each existing candidate into {@code dir} (a big one shortened), returns how many were copied. */
    static int copy(List<File> files, File dir) {
        int copied = 0;
        for (File src : files) {
            if (!src.isFile()) continue;
            String name = uniqueName(dir, src.getName());
            try (OutputStream out = new FileOutputStream(new File(dir, name))) {
                copyShortened(src, out);
                copied++;
            } catch (IOException ignored) {
                // an unreadable log is skipped; the summary still lists the session
            }
        }
        return copied;
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
