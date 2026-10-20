/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Files for {@link AgvnDwriteFonts}: found in any case as Wine finds them, copied, made, listed. */
final class AgvnFontFiles {
    private static final String WINDOWS_FONTS = "c:\\windows\\fonts\\";

    private AgvnFontFiles() {}

    /**
     * {@code dir}'s file named {@code name} in any case, or null, under its name on disk: the registry gets that
     * name, and on a disk that ignores case new File(dir, "nirmala.ttf") finds Nirmala.ttf but keeps "nirmala.ttf".
     */
    static File find(File dir, String name) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        File any = null;
        for (File f : files) {
            if (!f.isFile()) continue;
            if (f.getName().equals(name)) return f;
            if (any == null && f.getName().equalsIgnoreCase(name)) any = f;
        }
        return any;
    }

    /** What {@code path} ("/" between parts) names below {@code root}, each part in any case; null when nothing. */
    static File resolve(File root, String path) {
        File at = root;
        for (String part : path.split("/")) {
            if (part.isEmpty()) continue;
            File next = new File(at, part);
            if (!next.exists()) {
                next = null;
                File[] files = at.listFiles();
                if (files != null) for (File f : files) if (f.getName().equalsIgnoreCase(part)) next = f;
            }
            if (next == null) return null;
            at = next;
        }
        return at;
    }

    /** A file name alone, which Windows looks for in C:\windows\Fonts. */
    static boolean bare(String file) {
        return !file.isEmpty() && file.indexOf('\\') < 0 && file.indexOf('/') < 0 && file.indexOf(':') < 0;
    }

    /** The same font under another extension in {@code here}: msyh.ttc for msyh.ttf. */
    static File sibling(Map<String, File> here, String low) {
        int dot = low.lastIndexOf('.');
        if (dot < 0) return null;
        for (String ext : new String[]{".ttc", ".ttf", ".otf"}) {
            File f = here.get(low.substring(0, dot) + ext);
            if (f != null && !f.getName().equalsIgnoreCase(low)) return f;
        }
        return null;
    }

    /** The files of a folder by lower-case name, sorted. */
    static Map<String, File> listing(File dir) {
        Map<String, File> out = new TreeMap<>();
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) if (f.isFile()) out.put(f.getName().toLowerCase(Locale.ROOT), f);
        return out;
    }

    static boolean same(File a, File b) throws IOException {
        return a.length() == b.length()
                && Arrays.equals(Files.readAllBytes(a.toPath()), Files.readAllBytes(b.toPath()));
    }

    static void copy(File from, File to) throws IOException {
        File tmp = new File(to.getPath() + ".agvn");
        Files.copy(from.toPath(), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.move(tmp.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    static void write(File file, byte[] data) throws IOException {
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot make " + dir);
        File tmp = new File(file.getPath() + ".agvn");
        Files.write(tmp.toPath(), data);
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    static void writeText(File file, String text) throws IOException {
        write(file, text.getBytes(StandardCharsets.ISO_8859_1));
    }

    static String read(File file) throws IOException {
        // ISO-8859-1 keeps every byte as it is; Wine writes anything past ASCII as \x escapes
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
    }

    /** One collection of {@code source} in {@code style} under each of {@code families}, sharing its glyphs. */
    static void makeCollection(File source, String style, File to, String... families) throws IOException {
        AgvnFontFile font = AgvnFontFile.read(source);
        List<byte[]> names = new ArrayList<>();
        for (String family : families) names.add(font.renamed(family, style));
        write(to, font.collection(names));
    }

    /**
     * Lists the fonts of C:\windows\Fonts that no entry names, under their full names, so that DirectWrite finds
     * Verdana, Trebuchet MS, Source Han Sans… too; GDI reads them from the folder and skips such an entry. With
     * {@code skip} (Wine's Tahoma while DirectWrite has the app's), fonts of these families stay unlisted.
     */
    static int listUnlisted(AgvnRegKey key, Map<String, File> here, Set<String> served, String... skip) {
        Map<String, String> values = key.strings();
        Set<String> named = new HashSet<>(), used = new HashSet<>();
        for (Map.Entry<String, String> e : values.entrySet()) {
            String low = e.getValue().toLowerCase(Locale.ROOT);
            named.add(low.startsWith(WINDOWS_FONTS) ? low.substring(WINDOWS_FONTS.length()) : low);
            used.add(e.getKey().toLowerCase(Locale.ROOT));
        }
        int listed = 0;
        for (Map.Entry<String, File> e : here.entrySet()) {
            String low = e.getKey();
            boolean font = low.endsWith(".ttf") || low.endsWith(".ttc") || low.endsWith(".otf");
            if (!font || named.contains(low)) continue;
            String family = AgvnFontFile.name(e.getValue(), 0, 1), full = AgvnFontFile.name(e.getValue(), 0, 4);
            if (family == null || family.isEmpty() || anyIgnoringCase(skip, family)) continue;
            String name = (full != null && !full.isEmpty() ? full : family)
                    + (low.endsWith(".otf") ? " (OpenType)" : " (TrueType)");
            if (!used.add(name.toLowerCase(Locale.ROOT))) continue;
            key.set(name, e.getValue().getName());
            served.add(name);
            listed++;
        }
        return listed;
    }

    private static boolean anyIgnoringCase(String[] names, String name) {
        for (String n : names) if (n.equalsIgnoreCase(name)) return true;
        return false;
    }

    /** Up to eight names, then how many more; each name once. */
    static String few(List<String> names) {
        List<String> once = new ArrayList<>(new LinkedHashSet<>(names));
        if (once.size() <= 8) return String.join(", ", once);
        return String.join(", ", once.subList(0, 8)) + " và " + (once.size() - 8) + " mục khác";
    }
}
