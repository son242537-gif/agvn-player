/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Moves saves in and out of a game, for "Xuất save" / "Nhập save". An exported zip holds game-folder saves under
 * game/, Windows user folder saves under profile/, Unity PlayerPrefs in registry/playerprefs.txt, and the marker file
 * agvn-save.txt. Such a zip is restored to the same places. Any other file is a save copied into the game's main save
 * place, and any other zip is unpacked there (dropping one top folder they all share). Nothing is written outside a
 * save place: entries that would are skipped.
 */
final class AgvnSaveTransfer {
    static final String MARKER = "agvn-save.txt";
    static final String PREFS = "registry/playerprefs.txt";

    /** A picked file: its display name and a way to read it. */
    interface Source {
        String name();
        InputStream open() throws IOException;
    }

    private AgvnSaveTransfer() {}

    /** Zips every save file of {@code places} plus {@code prefs}; returns the number of save files and values. */
    static int export(List<AgvnSaveLocations.Location> places, List<String> prefs, String game, File zip) throws IOException {
        int count = 0;
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            put(out, MARKER, ("AGVN Player save\ngame=" + game + "\n").getBytes(StandardCharsets.UTF_8));
            for (AgvnSaveLocations.Location place : places) count += zipPlace(out, place, place.dir, place.zipPath);
            if (!prefs.isEmpty()) {
                put(out, PREFS, (String.join("\n", prefs) + "\n").getBytes(StandardCharsets.UTF_8));
                count += prefs.size();
            }
        }
        return count;
    }

    private static int zipPlace(ZipOutputStream out, AgvnSaveLocations.Location place, File dir, String path) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return 0;
        int count = 0;
        for (File f : files) {
            if (f.isDirectory()) {
                if (place.files == null) count += zipPlace(out, place, f, path + "/" + f.getName());
            } else if (place.takes(f.getName())) {
                out.putNextEntry(new ZipEntry(path + "/" + f.getName()));
                java.nio.file.Files.copy(f.toPath(), out);
                out.closeEntry();
                count++;
            }
        }
        return count;
    }

    private static void put(ZipOutputStream out, String name, byte[] data) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(data);
        out.closeEntry();
    }

    /** True when the stream is a zip written by {@link #export}. */
    static boolean isAgvnZip(InputStream in) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(in)) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) if (MARKER.equals(e.getName())) return true;
        }
        return false;
    }

    /** Restores an exported zip into {@code places}; PlayerPrefs lines go to {@code prefsOut}. Returns files written. */
    static int restore(InputStream in, List<AgvnSaveLocations.Location> places, List<String> prefsOut) throws IOException {
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(in)) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                if (e.isDirectory()) continue;
                if (PREFS.equals(e.getName())) {
                    for (String line : new String(readAll(zip), StandardCharsets.UTF_8).split("\n"))
                        if (!line.trim().isEmpty()) prefsOut.add(line);
                    continue;
                }
                File target = targetFor(places, e.getName());
                if (target != null && write(zip, target)) count++;
            }
        }
        return count;
    }

    /** Where a zip entry of an exported save goes, or null when it is outside every save place. */
    static File targetFor(List<AgvnSaveLocations.Location> places, String entry) throws IOException {
        for (AgvnSaveLocations.Location place : places) {
            String prefix = place.zipPath + "/";
            if (!entry.startsWith(prefix)) continue;
            String rel = entry.substring(prefix.length());
            if (place.files != null && (rel.contains("/") || !place.takes(rel))) continue;
            File target = inside(place.dir, rel);
            if (target != null) return target;
        }
        return null;
    }

    /** Copies plain save files, or unpacks other zips, into {@code place}; returns files written. */
    static int copyInto(List<Source> sources, AgvnSaveLocations.Location place) throws IOException {
        int count = 0;
        for (Source source : sources) {
            if (source.name().toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
                count += unzipInto(source, place);
            } else if (place.takes(source.name())) {
                File target = inside(place.dir, source.name());
                try (InputStream in = source.open()) {
                    if (target != null && write(in, target)) count++;
                }
            }
        }
        return count;
    }

    private static int unzipInto(Source source, AgvnSaveLocations.Location place) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(source.open())) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) if (!e.isDirectory()) names.add(e.getName());
        }
        String top = sharedTopFolder(names);
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(source.open())) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                if (e.isDirectory()) continue;
                String rel = e.getName().substring(top.length());
                if (place.files != null && (rel.contains("/") || !place.takes(rel))) continue;
                File target = inside(place.dir, rel);
                if (target != null && write(zip, target)) count++;
            }
        }
        return count;
    }

    /** "folder/" when every name starts with the same top folder, else "". */
    static String sharedTopFolder(List<String> names) {
        if (names.isEmpty() || !names.get(0).contains("/")) return "";
        String top = names.get(0).substring(0, names.get(0).indexOf('/') + 1);
        for (String n : names) if (!n.startsWith(top)) return "";
        return top;
    }

    /** {@code dir/rel} when it stays inside {@code dir} (no "..", no absolute path), else null. */
    static File inside(File dir, String rel) throws IOException {
        if (rel.isEmpty()) return null;
        File base = dir.getCanonicalFile(), target = new File(base, rel).getCanonicalFile();
        return target.getPath().startsWith(base.getPath() + File.separator) ? target : null;
    }

    private static boolean write(InputStream in, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
        try (OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = in.read(buffer)) != -1; ) out.write(buffer, 0, n);
        }
        return true;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        for (int n; (n = in.read(buffer)) != -1; ) out.write(buffer, 0, n);
        return out.toByteArray();
    }
}
