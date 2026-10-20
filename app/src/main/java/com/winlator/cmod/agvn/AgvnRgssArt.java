/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * An RPG Maker XP/VX/VX Ace game's title picture: Graphics/Titles (XP), Graphics/System/Title (VX) or
 * Graphics/Titles1 (VX Ace), loose or packed in Game.rgssad/.rgss2a/.rgss3a ({@link AgvnRgssArchive}). The picture
 * Data/System.rxdata/.rvdata2 names comes first (from the RTP on the phone when the game takes it from there), then
 * VX's fixed one, then the others from the largest. Pure Java (JVM-testable).
 */
final class AgvnRgssArt {
    private static final String[] EXTS = {".png", ".jpg", ".jpeg", ".bmp"};
    private static final String[] TITLE_DIRS = {"graphics/titles1/", "graphics/titles/"};
    private static final String VX_TITLE = "graphics/system/title";
    private static final String[] SYSTEM_FILES = {"data/system.rvdata2", "data/system.rxdata"};
    private static final int MAX_SYSTEM = 4 << 20;

    /** A picture with its lower-case path in the game ("graphics/titles1/book.png"). */
    private static final class Pic {
        final String path;
        final AgvnArt art;

        Pic(String path, AgvnArt art) {
            this.path = path;
            this.art = art;
        }
    }

    private AgvnRgssArt() {}

    /** {@code rtpRoot}: AGVN-Player/RTP, for a title the game takes from its RTP; null to look in the game only. */
    static void addCovers(File gameDir, List<AgvnArt> out, File rtpRoot) {
        if (gameDir == null || !gameDir.isDirectory()) return;
        List<Pic> pics = new ArrayList<>();
        for (String dir : new String[]{"Graphics/Titles1", "Graphics/Titles", "Graphics/System"}) {
            File folder = resolve(gameDir, dir);
            File[] files = folder != null ? folder.listFiles(File::isFile) : null;
            if (files == null) continue;
            Arrays.sort(files);
            for (File f : files) {
                String path = dir.toLowerCase(Locale.ROOT) + "/" + f.getName().toLowerCase(Locale.ROOT);
                if (isTitle(path)) pics.add(new Pic(path, AgvnArt.file(f)));
            }
        }
        byte[] system = null;
        for (String name : SYSTEM_FILES) {
            File f = resolve(gameDir, name);
            if (system == null && f != null && f.isFile() && f.length() <= MAX_SYSTEM) system = readQuietly(f);
        }
        File[] archives = gameDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).matches(".*\\.(rgssad|rgss2a|rgss3a)"));
        if (archives != null) {
            Arrays.sort(archives);
            for (File archive : archives) {
                for (AgvnRgssArchive.Entry e : AgvnRgssArchive.list(archive)) {
                    String path = e.name.toLowerCase(Locale.ROOT);
                    if (isTitle(path)) pics.add(new Pic(path, AgvnArt.rgss(archive, e)));
                    else if (system == null && Arrays.asList(SYSTEM_FILES).contains(path))
                        system = AgvnRgssArchive.read(archive, e, MAX_SYSTEM);
                }
            }
        }
        String title = system != null ? titleName(system) : null;
        boolean found = false;
        if (title != null) {
            String wanted = title.toLowerCase(Locale.ROOT);
            for (Pic p : pics) {
                if (inTitles(p.path) && stripExt(p.path.substring(p.path.lastIndexOf('/') + 1)).equals(wanted)) {
                    AgvnCoverSources.add(out, p.art);
                    found = true;
                    break;
                }
            }
        }
        for (Pic p : pics) {
            if (stripExt(p.path).equals(VX_TITLE)) {
                AgvnCoverSources.add(out, p.art);
                found = true;
                break;
            }
        }
        if (!found && rtpRoot != null) AgvnCoverSources.add(out, fromRtp(gameDir, title, rtpRoot));
        List<AgvnArt> titles = new ArrayList<>();
        for (Pic p : pics) if (inTitles(p.path)) titles.add(p.art);
        AgvnCoverSources.addLargest(out, titles);
    }

    /** The title the game takes from an RTP on the phone: the one Data/System names, or VX's Graphics/System/Title. */
    private static AgvnArt fromRtp(File gameDir, String title, File rtpRoot) {
        int rgss = AgvnRgssGame.rgssVersion(gameDir);
        if (rgss == 0) return null; // not an XP/VX/VX Ace game (or no Game.ini naming its RTP)
        List<String> paths = new ArrayList<>();
        if (title != null) paths.addAll(Arrays.asList("Graphics/Titles1/" + title, "Graphics/Titles/" + title));
        else if (rgss == 2) paths.add("Graphics/System/Title");
        for (String name : AgvnRgssGame.rtpNames(AgvnRgssGame.readIni(gameDir))) {
            File rtp = AgvnRgssGame.findRtp(name, rgss, rtpRoot, null);
            for (String path : paths) {
                for (String ext : EXTS) {
                    File f = rtp != null ? resolve(rtp, path + ext) : null;
                    if (f != null && f.isFile()) return AgvnArt.file(f);
                }
            }
        }
        return null;
    }

    /** True for a title picture path: in Graphics/Titles1 or Graphics/Titles, or VX's Graphics/System/Title. */
    static boolean isTitle(String path) {
        String bare = stripExt(path);
        if (bare.equals(path)) return false; // not a picture
        return inTitles(path) || bare.equals(VX_TITLE);
    }

    /** {@code path} under {@code root}, each folder found without regard to case (a game copied from Windows). */
    private static File resolve(File root, String path) {
        File f = root;
        for (String part : path.split("/")) f = AgvnRgssGame.child(f, part);
        return f;
    }

    private static boolean inTitles(String path) {
        for (String dir : TITLE_DIRS) if (path.startsWith(dir) && path.indexOf('/', dir.length()) < 0) return true;
        return false;
    }

    private static String stripExt(String path) {
        for (String ext : EXTS) if (path.endsWith(ext)) return path.substring(0, path.length() - ext.length());
        return path;
    }

    /**
     * The title picture Data/System names (VX Ace @title1_name, XP @title_name), from Ruby's Marshal format: the
     * symbol (':', length + 5, name), then the string ('"', or 'I"' with an encoding after it in Ruby 1.9).
     */
    static String titleName(byte[] data) {
        for (String symbol : new String[]{"@title1_name", "@title_name"}) {
            byte[] sym = symbol.getBytes(StandardCharsets.US_ASCII);
            for (int at = indexOf(data, sym, 2); at >= 0; at = indexOf(data, sym, at + 1)) {
                if (data[at - 2] != ':' || data[at - 1] != sym.length + 5) continue;
                String s = marshalString(data, at + sym.length);
                if (s != null) return s.isEmpty() ? null : s;
            }
        }
        return null;
    }

    private static String marshalString(byte[] d, int p) {
        if (p < d.length && d[p] == 'I') p++;
        if (p >= d.length || d[p] != '"') return null;
        p++;
        if (p >= d.length) return null;
        int c = d[p++], len;
        if (c == 0) len = 0;
        else if (c >= 5) len = c - 5;
        else if (c > 0) { // c bytes of length, little-endian
            if (p + c > d.length) return null;
            len = 0;
            for (int i = 0; i < c; i++) len |= (d[p + i] & 0xFF) << (8 * i);
            p += c;
        } else return null; // a negative length
        if (len < 0 || p + len > d.length) return null;
        return new String(d, p, len, StandardCharsets.UTF_8);
    }

    private static int indexOf(byte[] data, byte[] needle, int from) {
        outer:
        for (int i = Math.max(from, 0); i + needle.length <= data.length; i++) {
            for (int j = 0; j < needle.length; j++) if (data[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    private static byte[] readQuietly(File f) {
        try {
            return AgvnArt.readFile(f);
        } catch (java.io.IOException e) {
            return null;
        }
    }
}
