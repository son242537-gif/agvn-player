/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Library artwork without internet: the game's own image (see {@link AgvnCoverSources}) or a full-bleed picture drawn
 * from the game's icon and name, as a 600x900 cover or a 1280x720 banner. Runs off the UI thread. Each file AGVN
 * writes gets a "<file>.agvn" note with the drawing version, so older AGVN artwork is redrawn after an update
 * (3: pictures packed in Ren'Py and RPG Maker archives, encrypted MV/MZ titles, no RPG Maker stock icon).
 */
public final class AgvnCovers {
    static final String VERSION = "3";
    private static final int COVER_W = 600, COVER_H = 900, BANNER_W = 1280, BANNER_H = 720;
    /** Pictures are decoded at least this large (when larger), so a cover or banner keeps its detail. */
    private static final int FULL_W = 1280, FULL_H = 720;

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
            Bitmap image = firstPicture(AgvnCoverSources.candidates(gameDir, AgvnGameIcons.rtpRoot()), FULL_W, FULL_H);
            Bitmap out = image != null ? AgvnCoverPainter.fromImage(image, w, h)
                    : AgvnCoverPainter.drawn(gameName, AgvnGameIcons.forDrawnCover(gameDir, exe), w, h);
            return save(out, target) && writeNote(target, VERSION);
        } catch (Throwable e) {
            Log.w("AGVN", "artwork for " + gameName + " failed", e);
            return false;
        }
    }

    /** The first picture that decodes and is not one flat colour, kept at least {@code minW}x{@code minH} when larger; or null. */
    static Bitmap firstPicture(List<AgvnArt> candidates, int minW, int minH) {
        for (AgvnArt art : candidates) {
            byte[] data = art.read();
            Bitmap b = data != null ? decode(data, minW, minH) : null;
            if (b != null && b.getWidth() >= 16 && b.getHeight() >= 16 && !isFlat(b)) return b;
            if (b != null) {
                Log.i("AGVN", "artwork skipped (flat or tiny): " + art.name);
                b.recycle();
            }
        }
        return null;
    }

    /** Decodes {@code data}, halving a picture far larger than {@code minW}x{@code minH}; null when it is no picture. */
    static Bitmap decode(byte[] data, int minW, int minH) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= minW && o.outHeight / (sample * 2) >= minH) sample *= 2;
        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, decode);
    }

    /** True for a picture of one colour (Ren'Py's default menu background), from a 16x16 grid of its pixels. */
    static boolean isFlat(Bitmap b) {
        int first = b.getPixel(0, 0);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = b.getPixel(x * (b.getWidth() - 1) / 15, y * (b.getHeight() - 1) / 15);
                for (int shift = 0; shift < 32; shift += 8) {
                    if (Math.abs(((c >>> shift) & 0xFF) - ((first >>> shift) & 0xFF)) > 8) return false;
                }
            }
        }
        return true;
    }

    static boolean save(Bitmap bitmap, File target) throws java.io.IOException {
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileOutputStream stream = new FileOutputStream(target)) {
            return bitmap.compress(Bitmap.CompressFormat.PNG, 95, stream);
        }
    }

    static boolean writeNote(File target, String version) throws java.io.IOException {
        try (FileOutputStream note = new FileOutputStream(noteFor(target))) {
            note.write(version.getBytes(StandardCharsets.UTF_8));
        }
        return true;
    }

    static File noteFor(File target) {
        return new File(target.getPath() + ".agvn");
    }

    static String readNote(File note) {
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
