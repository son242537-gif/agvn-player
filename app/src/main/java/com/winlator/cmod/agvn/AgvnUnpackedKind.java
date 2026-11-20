/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What an archive turned out to hold when no game was found in it, so the player is told what to do instead of "không
 * thấy game": the Android version of the game (an .apk), another archive (the download was an archive in an archive),
 * the macOS or Linux version, or nothing the app knows. Whether there is a game is {@link AgvnGameScanner}'s word
 * alone; this only names what is there in its place. Pure Java (JVM-testable).
 */
final class AgvnUnpackedKind {
    enum Kind {
        /** The scanner found a game (or a folder with an exe that may be one). */
        GAME,
        /** Only .apk/.xapk (and .obb): the Android version. */
        ANDROID,
        /** One archive (or one split archive) and nothing else of a game: {@link #file} is the part to open. */
        INNER_ARCHIVE,
        /** A .app bundle, a .dmg or Linux programs, and no .exe. */
        OTHER_OS,
        NOTHING
    }

    private static final int DEPTH = 4, MAX_VISITED = 4000;
    private static final List<String> APK = Arrays.asList(".apk", ".xapk", ".apks", ".apkm");
    private static final List<String> LINUX = Arrays.asList(".x86_64", ".x86", ".appimage", ".sh");

    final Kind kind;
    /** ANDROID: the .apk when there is exactly one (else null); INNER_ARCHIVE: the archive. */
    final File file;
    /** OTHER_OS: "macOS" or "Linux". */
    final String system;

    private AgvnUnpackedKind(Kind kind, File file, String system) {
        this.kind = kind;
        this.file = file;
        this.system = system;
    }

    /** What the folder of an unpacked archive is. */
    static AgvnUnpackedKind classify(File dir) {
        AgvnGameScanner.Found found = AgvnGameScanner.find(Collections.singletonList(new AgvnGameScanner.Root(dir, DEPTH, true)));
        return found.games.isEmpty() && found.unsure.isEmpty() ? withoutGame(dir) : new AgvnUnpackedKind(Kind.GAME, null, null);
    }

    /** What a folder in which the scanner found no game holds instead. */
    static AgvnUnpackedKind withoutGame(File dir) {
        Seen seen = new Seen();
        walk(dir, DEPTH, seen);
        if (!seen.exe && (!seen.apks.isEmpty() || seen.obb))
            return new AgvnUnpackedKind(Kind.ANDROID, seen.apks.size() == 1 && seen.apks.get(0).getName().toLowerCase(Locale.ROOT).endsWith(".apk")
                    ? seen.apks.get(0) : null, null);
        if (!seen.exe && !seen.mac && !seen.linux && seen.archives.size() == 1)
            return new AgvnUnpackedKind(Kind.INNER_ARCHIVE, seen.archives.get(0), null);
        if (!seen.exe && (seen.mac || seen.linux)) return new AgvnUnpackedKind(Kind.OTHER_OS, null, seen.mac ? "macOS" : "Linux");
        return new AgvnUnpackedKind(Kind.NOTHING, null, null);
    }

    private static final class Seen {
        final List<File> apks = new ArrayList<>(), archives = new ArrayList<>();
        boolean obb, exe, mac, linux;
        int visited;
    }

    private static void walk(File dir, int depthLeft, Seen seen) {
        File[] children = dir.listFiles();
        if (children == null || seen.visited++ > MAX_VISITED) return;
        Arrays.sort(children);
        List<String> files = new ArrayList<>();
        for (File child : children) {
            String name = child.getName(), lower = name.toLowerCase(Locale.ROOT);
            if (child.isDirectory()) {
                // a macOS program is a folder "Game.app" with Contents inside
                if (lower.endsWith(".app") && new File(child, "Contents").isDirectory()) seen.mac = true;
                else if (depthLeft > 0) walk(child, depthLeft - 1, seen);
                continue;
            }
            files.add(name);
            if (lower.endsWith(".exe")) seen.exe = true;
            else if (lower.endsWith(".obb")) seen.obb = true;
            else if (lower.endsWith(".dmg")) seen.mac = true;
            else if (ends(lower, APK)) seen.apks.add(child);
            else if (ends(lower, LINUX)) seen.linux = true;
        }
        // one name per split archive, as "Thêm từ tệp nén" lists them
        for (String archive : AgvnArchiveKind.pickable(files.toArray(new String[0]))) seen.archives.add(new File(dir, archive));
    }

    private static boolean ends(String lower, List<String> endings) {
        for (String e : endings) if (lower.endsWith(e)) return true;
        return false;
    }
}
