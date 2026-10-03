/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.core.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Chạy nhẹ" for RPG Maker XP, VX and VX Ace (RGSS 1, 2 and 3): the game runs on mkxp-z built for Android
 * ({@link AgvnRgssActivity}, scripts/agvn/mkxp-z) instead of Wine. RPG Maker 2000/2003 and MV/MZ are other engines.
 * The game folder is read where it is, and the game saves there as on a PC. Pure Java (JVM-testable).
 */
public final class AgvnRgssGame {
    private AgvnRgssGame() {}

    /** The game's [Game] section: its .ini (Game.ini, or MyGame.ini next to MyGame.exe), or null. */
    static final class Ini {
        final File file;
        final String library, scripts, rtp, rtp1, rtp2, rtp3;

        Ini(File file, List<String> lines) {
            this.file = file;
            String lib = null, scr = null, r = null, r1 = null, r2 = null, r3 = null;
            boolean game = false;
            for (String raw : lines) {
                String line = raw.trim();
                if (line.startsWith("[")) {
                    game = line.equalsIgnoreCase("[Game]");
                    continue;
                }
                int eq = line.indexOf('=');
                if (!game || eq <= 0) continue;
                String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT), value = line.substring(eq + 1).trim();
                if (key.equals("library")) lib = value;
                else if (key.equals("scripts")) scr = value;
                else if (key.equals("rtp")) r = value;
                else if (key.equals("rtp1")) r1 = value;
                else if (key.equals("rtp2")) r2 = value;
                else if (key.equals("rtp3")) r3 = value;
            }
            library = lib;
            scripts = scr;
            rtp = r;
            rtp1 = r1;
            rtp2 = r2;
            rtp3 = r3;
        }

        /** "Game" for Game.ini: mkxp-z's execName, which also names the encrypted archive. */
        String execName() {
            String n = file.getName();
            return n.substring(0, n.length() - 4);
        }
    }

    static Ini readIni(File gameDir) {
        File[] files = list(gameDir);
        File ini = null;
        for (File f : files) {
            String n = f.getName();
            if (!f.isFile() || !n.toLowerCase(Locale.ROOT).endsWith(".ini")) continue;
            if (n.equalsIgnoreCase("game.ini")) return new Ini(f, FileUtils.readLines(f));
            String exe = n.substring(0, n.length() - 4) + ".exe";
            for (File g : files) if (g.getName().equalsIgnoreCase(exe)) ini = f;
        }
        return ini != null ? new Ini(ini, FileUtils.readLines(ini)) : null;
    }

    /** 1 for XP, 2 for VX, 3 for VX Ace; 0 when the folder is not an RGSS game. */
    static int rgssVersion(File gameDir) {
        Ini ini = readIni(gameDir);
        if (ini == null) return 0;
        int v = fromLibrary(ini.library);
        if (v == 0) v = fromExtension(ini.scripts);
        String exec = ini.execName().toLowerCase(Locale.ROOT);
        for (File f : list(gameDir)) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (v == 0 && f.isFile() && n.startsWith(exec + ".")) v = fromExtension(n);
        }
        for (File f : list(new File(gameDir, "Data"))) if (v == 0 && f.isFile()) v = fromExtension(f.getName());
        return v;
    }

    /** "RGSS104E.dll", "System\RGSS301.dll" -> 1, 3. */
    static int fromLibrary(String library) {
        if (library == null) return 0;
        String l = library.toUpperCase(Locale.ROOT);
        int i = l.lastIndexOf("RGSS");
        if (i < 0 || i + 4 >= l.length()) return 0;
        char c = l.charAt(i + 4);
        return c >= '1' && c <= '3' ? c - '0' : 0;
    }

    /** Archives and script files: .rgssad/.rxdata 1, .rgss2a/.rvdata 2, .rgss3a/.rvdata2 3. */
    static int fromExtension(String name) {
        if (name == null) return 0;
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".rgss3a") || n.endsWith(".rvdata2")) return 3;
        if (n.endsWith(".rgss2a") || n.endsWith(".rvdata")) return 2;
        if (n.endsWith(".rgssad") || n.endsWith(".rxdata")) return 1;
        return 0;
    }

    /** True when "Chạy nhẹ" can run the game: an XP, VX or VX Ace game. */
    public static boolean canRun(File gameDir) {
        return gameDir != null && rgssVersion(gameDir) != 0;
    }

    /** The runner at import, as AgvnRenpyGame.useRenpy: "Chạy nhẹ" whenever it can run the game, unless the profile says Wine. */
    public static boolean useRgss(AgvnProfile profile, GameExeResolver.Engine engine, File gameDir) {
        if (engine != GameExeResolver.Engine.RPGMAKER || !canRun(gameDir)) return false;
        return profile == null || profile.runner == null || !AgvnHtmlGame.RUNNER_WINE.equals(profile.runner);
    }

    /** The RTP packs the game asks for: RTP=RPGVXAce (VX, VX Ace) or RTP1..RTP3=Standard (XP). */
    static List<String> rtpNames(Ini ini) {
        List<String> names = new ArrayList<>();
        if (ini == null) return names;
        for (String n : new String[]{ini.rtp, ini.rtp1, ini.rtp2, ini.rtp3})
            if (n != null && !n.isEmpty() && !names.contains(n)) names.add(n);
        return names;
    }

    /** Where Enterbrain's installers put an RTP: Common Files\Enterbrain\RGSS[2|3]\<name>. */
    static String enterbrainDir(int rgss) {
        return rgss == 3 ? "RGSS3" : rgss == 2 ? "RGSS2" : "RGSS";
    }

    /**
     * The folder of RTP {@code name}: AGVN-Player/RTP/<name>, then the one installed in Wine ("Chạy bằng Windows"),
     * 64-bit then 32-bit Program Files. A folder counts when it has Graphics/. Null when the phone has none.
     */
    static File findRtp(String name, int rgss, File agvnRtpDir, File driveC) {
        List<File> candidates = new ArrayList<>();
        candidates.add(child(agvnRtpDir, name));
        if (driveC != null) {
            for (String pf : new String[]{"Program Files (x86)", "Program Files"}) {
                File enterbrain = child(child(child(driveC, pf), "Common Files"), "Enterbrain");
                candidates.add(child(child(enterbrain, enterbrainDir(rgss)), name));
            }
        }
        for (File dir : candidates) if (dir != null && child(dir, "Graphics") != null) return dir;
        return null;
    }

    /** The child of {@code dir} named {@code name}, any letter case (games and RTP copies vary); null if none. */
    static File child(File dir, String name) {
        if (dir == null) return null;
        File exact = new File(dir, name);
        if (exact.exists()) return exact;
        for (File f : list(dir)) if (f.getName().equalsIgnoreCase(name)) return f;
        return null;
    }

    static File[] list(File dir) {
        File[] files = dir != null ? dir.listFiles() : null;
        return files != null ? files : new File[0];
    }
}
