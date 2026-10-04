/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a Godot game's files say. Its version: Godot prints it into its own log only, not to Wine, so "Tự sửa lỗi" reads
 * the game's pack to choose GLES2 (Godot 3) or Vulkan (Godot 4). A pack starts with "GDPC", the pack format, then the
 * engine's major, minor and patch (32-bit little-endian); it is a .pck beside the exe, or sits at the end of the exe
 * ("Embed PCK"), which then ends with the pack's size and "GDPC". Its log: user://logs/godot.log under %APPDATA%.
 * Pure Java (JVM-testable).
 */
final class AgvnGodotFiles {
    /** "GDPC" read as a little-endian int. */
    static final int MAGIC = 0x43504447;

    private AgvnGodotFiles() {}

    /** {major, minor, patch} of the game of {@code exe}, from a pack beside it, in {@code gameDir} or in it; null when none. */
    static int[] release(File exe, File gameDir) {
        Set<File> packs = new LinkedHashSet<>();
        File exeDir = exe.getParentFile();
        if (exeDir != null) packs.add(new File(exeDir, exe.getName().replaceFirst("(?i)\\.exe$", "") + ".pck")); // Godot's first try
        for (File dir : new File[]{exeDir, gameDir}) {
            File[] found = dir == null ? null : dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".pck"));
            if (found == null) continue;
            Arrays.sort(found);
            packs.addAll(Arrays.asList(found));
        }
        packs.add(exe);
        for (File pack : packs) {
            int[] version = version(pack);
            if (version != null) return version;
        }
        return null;
    }

    /** {major, minor, patch} of the pack {@code f} is or ends with, or null. */
    static int[] version(File f) {
        if (!f.isFile()) return null;
        try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
            int[] version = header(in, 0);
            if (version != null) return version;
            long length = in.length();
            if (length < 32) return null;
            ByteBuffer end = read(in, length - 12, 12);
            if (end.getInt(8) != MAGIC) return null;
            long size = end.getLong(0);
            return size > 0 && size <= length - 12 ? header(in, length - 12 - size) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Godot's logs under {@code roaming} (%APPDATA%) written since {@code sinceMs} (0: any): user://logs/godot.log is
     * Godot/app_userdata/&lt;project&gt;/logs/godot.log, or &lt;custom user dir&gt;/logs/godot.log. The folder is named
     * after the project, which only the game knows, hence the search.
     */
    static List<File> logs(File roaming, long sinceMs) {
        List<File> dirs = new ArrayList<>(children(roaming));
        dirs.addAll(children(new File(roaming, "Godot/app_userdata")));
        List<File> out = new ArrayList<>();
        for (File dir : dirs) {
            File log = new File(dir, "logs/godot.log");
            if (log.isFile() && (sinceMs <= 0 || log.lastModified() >= sinceMs - AgvnEngineLogs.OLD_SLACK_MS)) out.add(log);
        }
        return out;
    }

    private static List<File> children(File dir) {
        File[] found = dir.listFiles(File::isDirectory);
        if (found == null) return new ArrayList<>();
        Arrays.sort(found);
        return Arrays.asList(found);
    }

    private static int[] header(RandomAccessFile in, long at) throws IOException {
        if (at < 0 || at + 20 > in.length()) return null;
        ByteBuffer b = read(in, at, 20);
        if (b.getInt(0) != MAGIC) return null;
        int major = b.getInt(8);
        return major >= 2 && major <= 9 ? new int[]{major, b.getInt(12), b.getInt(16)} : null;
    }

    private static ByteBuffer read(RandomAccessFile in, long at, int count) throws IOException {
        byte[] buf = new byte[count];
        in.seek(at);
        in.readFully(buf);
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
    }
}
