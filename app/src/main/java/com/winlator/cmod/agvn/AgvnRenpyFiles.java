/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The Ren'Py engine for "Chạy nhẹ" (assets/agvn/renpy8.zip, made by app/agvn-renpy.gradle), unpacked into the app's
 * own files, where librenpython.so runs it from (ANDROID_PRIVATE). Unpacked on the first Ren'Py game, and again only
 * when an app update brings a different engine (assets/agvn/renpy8.txt). It goes into a new folder first, so an unpack
 * cut short (phone full, app closed) is never used. Pure Java (JVM-testable) except prepare().
 */
final class AgvnRenpyFiles {
    private static final String TAG = "AGVN";
    static final String DIR = "renpy8";
    private static final String ZIP = "agvn/renpy8.zip", VERSION = "agvn/renpy8.txt", STAMP = ".agvn-version";

    private AgvnRenpyFiles() {}

    /** The engine folder, unpacked first when missing or from another app version. */
    static File prepare(Context ctx) throws IOException {
        File dir = new File(ctx.getFilesDir(), DIR);
        String version = version(ctx);
        if (ready(dir, version)) return dir;
        Log.i(TAG, "unpacking Ren'Py engine " + version + " into " + dir);
        long start = System.currentTimeMillis();
        try (InputStream zip = ctx.getAssets().open(ZIP)) {
            unpack(zip, dir, version);
        }
        Log.i(TAG, "Ren'Py engine unpacked in " + (System.currentTimeMillis() - start) + " ms");
        return dir;
    }

    /** True when the engine still has to be unpacked (the first Ren'Py game after install or update). */
    static boolean needsUnpack(Context ctx) {
        try {
            return !ready(new File(ctx.getFilesDir(), DIR), version(ctx));
        } catch (IOException e) {
            return true;
        }
    }

    private static String version(Context ctx) throws IOException {
        try (InputStream in = ctx.getAssets().open(VERSION)) {
            return new String(readAll(in), StandardCharsets.UTF_8).trim();
        }
    }

    /** True when {@code dir} holds the engine of {@code version}. */
    static boolean ready(File dir, String version) {
        File stamp = new File(dir, STAMP);
        if (!stamp.isFile() || !new File(dir, "main.py").isFile()) return false;
        try (InputStream in = new FileInputStream(stamp)) {
            return version.equals(new String(readAll(in), StandardCharsets.UTF_8).trim());
        } catch (IOException e) {
            return false;
        }
    }

    /** Unpacks {@code zip} into {@code dir}, replacing what was there, and marks it with {@code version}. */
    static void unpack(InputStream zip, File dir, String version) throws IOException {
        File fresh = new File(dir.getPath() + ".new");
        delete(fresh);
        if (!fresh.mkdirs()) throw new IOException("cannot create " + fresh);
        Path root = fresh.toPath().normalize();
        byte[] buffer = new byte[64 * 1024];
        try (ZipInputStream in = new ZipInputStream(new BufferedInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                Path path = root.resolve(e.getName()).normalize();
                if (!path.startsWith(root) || path.equals(root)) throw new IOException("bad entry " + e.getName());
                File target = path.toFile();
                if (e.isDirectory()) {
                    target.mkdirs();
                    continue;
                }
                File parent = target.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("cannot create " + parent);
                try (OutputStream out = new FileOutputStream(target)) {
                    for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
                }
            }
        }
        try (OutputStream out = new FileOutputStream(new File(fresh, STAMP))) {
            out.write(version.getBytes(StandardCharsets.UTF_8));
        }
        delete(dir);
        if (!fresh.renameTo(dir)) throw new IOException("cannot move " + fresh + " to " + dir);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private static void delete(File f) {
        File[] children = f.isDirectory() ? f.listFiles() : null;
        if (children != null) for (File c : children) delete(c);
        f.delete();
    }
}
