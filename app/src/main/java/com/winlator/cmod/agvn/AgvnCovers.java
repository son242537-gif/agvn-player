/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.winlator.cmod.core.ExeIconExtractor;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Library artwork without internet: the game's own image (see {@link AgvnCoverSources}) or a full-bleed picture drawn
 * from the exe icon and the game name, as a 600x900 cover or a 1280x720 banner. Runs off the UI thread. Each file AGVN
 * writes gets a "<file>.agvn" note with the drawing version, so older AGVN artwork is redrawn after an update.
 */
public final class AgvnCovers {
    static final String VERSION = "2";
    private static final int COVER_W = 600, COVER_H = 900, BANNER_W = 1280, BANNER_H = 720;

    private static final java.util.concurrent.ExecutorService EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final java.util.Set<String> PENDING = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private AgvnCovers() {}

    /** Background {@link #ensure}; {@code done} runs on the worker thread after the artwork was written. */
    public static void requestAsync(String gameName, String agvnGameDir, File exe, File target, boolean banner, Runnable done) {
        if (target.isFile() || !PENDING.add(target.getPath())) return;
        EXECUTOR.execute(() -> {
            try {
                if (ensure(gameName, gameDirFor(agvnGameDir, exe), exe, target, banner) && done != null) done.run();
            } finally {
                PENDING.remove(target.getPath());
            }
        });
    }

    /** Deletes artwork drawn by an older AGVN version (so it is drawn again); true when it was removed. */
    public static boolean dropIfOutdated(File target) {
        File note = noteFor(target);
        boolean outdated;
        if (note.isFile()) {
            outdated = !VERSION.equals(readNote(note));
        } else {
            // the first AGVN covers (no note) were exactly 600x900
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(target.getPath(), o);
            outdated = o.outWidth == COVER_W && o.outHeight == COVER_H;
        }
        if (!outdated) return false;
        note.delete();
        return target.delete();
    }

    /** The imported game folder, else the Unreal project root above Binaries/Win64, else the exe folder. Pure Java. */
    static File gameDirFor(String agvnGameDir, File exe) {
        if (agvnGameDir != null && !agvnGameDir.isEmpty() && new File(agvnGameDir).isDirectory()) return new File(agvnGameDir);
        if (exe == null) return null;
        File dir = exe.getParentFile();
        if (dir != null && dir.getName().equalsIgnoreCase("Win64") && dir.getParentFile() != null
                && dir.getParentFile().getName().equalsIgnoreCase("Binaries")) {
            File project = dir.getParentFile().getParentFile();
            File root = project != null ? project.getParentFile() : null;
            if (root != null && AgvnCoverSources.findLocal(root) != null) return root;
            return project;
        }
        return dir;
    }

    /** Writes {@code target} (PNG); true when artwork now exists. */
    public static boolean ensure(String gameName, File gameDir, File exe, File target, boolean banner) {
        if (target.isFile()) return true;
        int w = banner ? BANNER_W : COVER_W, h = banner ? BANNER_H : COVER_H;
        try {
            File source = AgvnCoverSources.find(gameDir);
            Bitmap image = source != null ? BitmapFactory.decodeFile(source.getPath()) : null;
            Bitmap out = image != null ? AgvnCoverPainter.fromImage(image, w, h)
                    : AgvnCoverPainter.drawn(gameName, exe != null ? ExeIconExtractor.extractBitmap(exe) : null, w, h);
            File parent = target.getParentFile();
            if (parent != null) parent.mkdirs();
            try (FileOutputStream stream = new FileOutputStream(target)) {
                if (!out.compress(Bitmap.CompressFormat.PNG, 95, stream)) return false;
            }
            try (FileOutputStream note = new FileOutputStream(noteFor(target))) {
                note.write(VERSION.getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (Throwable e) {
            Log.w("AGVN", "artwork for " + gameName + " failed", e);
            return false;
        }
    }

    static File noteFor(File target) {
        return new File(target.getPath() + ".agvn");
    }

    private static String readNote(File note) {
        try {
            return new String(java.nio.file.Files.readAllBytes(note.toPath()), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }

    /** Word wrap to at most {@code maxLines} lines of about {@code width} characters. Pure Java. */
    static List<String> wrap(String text, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : (text == null ? "" : text.trim()).split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
                if (lines.size() == maxLines) break;
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0 && lines.size() < maxLines) lines.add(line.toString());
        return lines;
    }
}
