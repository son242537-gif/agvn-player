/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import com.winlator.cmod.container.Container;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * A game in a folder whose name ends in a space or a dot ("Game "): a phone keyboard adds the space after a word it
 * suggested, and Android keeps it. Windows cannot hold such a name: Mono and .NET drop the space from every folder
 * ("WIN32 path components must be trimmed"), Wine drops a last dot, and the game then looks for its files in "Game".
 * Lifeguard Holic (Unity 6, Addressables, Xiaomi 23049RAD8C, 14/10/2026) stood on its loading screen at 29 FPS: "Invalid
 * path in TextDataProvider : 'F:/Game /…/StreamingAssets/aa/settings.json'". Such a game starts through a link in
 * C:\agvn-links with the name trimmed, to that same folder: its files, saves and settings stay where they are. Only the
 * command of the current launch changes, as with {@link AgvnExeRedirect}.
 */
public final class AgvnPathLink {
    private static final String TAG = "AGVN";
    static final String LINKS = "agvn-links";
    private static volatile String note;

    private AgvnPathLink() {}

    /**
     * {@code launch} (the exe as the launch command has it, quoted or not, with its arguments) through a link when a
     * folder on its way has a name Windows cannot hold; else {@code launch} itself. Never throws.
     */
    public static String apply(String launch, Container container) {
        note = null;
        if (launch == null || container == null || container.getRootDir() == null) return launch;
        try {
            return apply(launch, AgvnExeRedirect.driveMap(container), new File(container.getRootDir(), ".wine/drive_c"));
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "no link for " + launch, e);
            return launch;
        }
    }

    static String apply(String launch, Map<String, String> drives, File driveC) throws IOException {
        note = null;
        String[] exeAndArgs = AgvnExeRedirect.splitArgs(launch.replace("\"", "").trim());
        if (exeAndArgs[0].toLowerCase(Locale.ROOT).endsWith(".lnk")) return launch;
        String unix = AgvnExeRedirect.toUnixPath(exeAndArgs[0], drives);
        int end = unix != null ? lastBadFolderEnd(unix) : -1;
        if (end < 0) return launch;
        File folder = new File(unix.substring(0, end));
        Path link = new File(new File(driveC, LINKS), linkName(folder)).toPath();
        Files.createDirectories(link.getParent());
        if (!Files.isSymbolicLink(link) || !Files.readSymbolicLink(link).toString().equals(folder.getPath())) {
            Files.deleteIfExists(link);
            Files.createSymbolicLink(link, folder.toPath());
        }
        String to = "C:\\" + LINKS + "\\" + link.getFileName() + unix.substring(end).replace('/', '\\');
        note = "Thư mục \"" + folder.getName() + "\" có dấu cách hoặc dấu chấm ở cuối tên, Windows không giữ được: mở game qua "
                + to + " (trỏ về đúng thư mục đó)";
        Log.i(TAG, "path link " + exeAndArgs[0] + " -> " + to);
        return "\"" + to + "\"" + exeAndArgs[1];
    }

    /**
     * The end of the last folder in {@code unixPath} whose name ends in a space or a dot (not "." or ".."); -1 when
     * none. The file's own name does not count.
     */
    static int lastBadFolderEnd(String unixPath) {
        int fileStart = unixPath.lastIndexOf('/');
        int found = -1;
        for (int start = 0, end; start < fileStart; start = end + 1) {
            end = unixPath.indexOf('/', start);
            if (end < 0 || end > fileStart) end = fileStart;
            String name = unixPath.substring(start, end);
            if (name.isEmpty() || name.equals(".") || name.equals("..")) continue;
            char last = name.charAt(name.length() - 1);
            if (last == ' ' || last == '.') found = end;
        }
        return found;
    }

    /** "Game-1a2b3c4d" for "/storage/emulated/0/Game ": the name trimmed, and a mark of the folder's whole path. */
    static String linkName(File folder) {
        String name = folder.getName().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replaceAll("[ .]+$", "").trim();
        return (name.isEmpty() ? "game" : name) + "-" + String.format(Locale.ROOT, "%08x", folder.getPath().hashCode());
    }

    /** The line for the session's events, once; null when the game starts from its own path. */
    static String takeNote() {
        String n = note;
        note = null;
        return n;
    }
}
