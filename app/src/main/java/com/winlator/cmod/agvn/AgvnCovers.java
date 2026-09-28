/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.Log;

import com.winlator.cmod.core.ExeIconExtractor;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Library cover art without internet: an image shipped in the game folder (cover/poster/folder/header/capsule/box/
 * banner .png/.jpg/.webp, or agvn-cover.*) is used first; otherwise a 600x900 AGVN cover is drawn from the exe
 * icon and the game name. Runs off the UI thread.
 */
public final class AgvnCovers {
    static final String[] NAMES = {"agvn-cover", "cover", "poster", "folder", "boxart", "box", "capsule", "header", "banner", "key_art", "keyart"};
    static final String[] EXTS = {".png", ".jpg", ".jpeg", ".webp"};
    private static final int W = 600, H = 900;

    private static final java.util.concurrent.ExecutorService EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final java.util.Set<String> PENDING = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private AgvnCovers() {}

    /** Background {@link #ensure}; {@code done} runs on the worker thread after a cover was written. */
    public static void requestAsync(String gameName, String agvnGameDir, File exe, File target, Runnable done) {
        if (target.isFile() || !PENDING.add(target.getPath())) return;
        EXECUTOR.execute(() -> {
            try {
                if (ensure(gameName, gameDirFor(agvnGameDir, exe), exe, target) && done != null) done.run();
            } finally {
                PENDING.remove(target.getPath());
            }
        });
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
            if (root != null && findLocal(root) != null) return root;
            return project;
        }
        return dir;
    }

    /** Writes {@code target} (PNG); true when a cover now exists. */
    public static boolean ensure(String gameName, File gameDir, File exe, File target) {
        if (target.isFile()) return true;
        try {
            File local = findLocal(gameDir);
            Bitmap cover = null;
            if (local != null) cover = scaleCrop(BitmapFactory.decodeFile(local.getPath()));
            if (cover == null) cover = draw(gameName, exe != null ? ExeIconExtractor.extractBitmap(exe) : null);
            File parent = target.getParentFile();
            if (parent != null) parent.mkdirs();
            try (FileOutputStream out = new FileOutputStream(target)) {
                return cover.compress(Bitmap.CompressFormat.PNG, 95, out);
            }
        } catch (Throwable e) {
            Log.w("AGVN", "cover for " + gameName + " failed", e);
            return false;
        }
    }

    /** First matching image directly in {@code gameDir} (case-insensitive name), or null. Pure Java. */
    static File findLocal(File gameDir) {
        File[] files = gameDir != null ? gameDir.listFiles(File::isFile) : null;
        if (files == null) return null;
        for (String name : NAMES) {
            for (File f : files) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                for (String ext : EXTS) if (n.equals(name + ext)) return f;
            }
        }
        return null;
    }

    private static Bitmap scaleCrop(Bitmap src) {
        if (src == null) return null;
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        float scale = Math.max(W / (float) src.getWidth(), H / (float) src.getHeight());
        int sw = Math.round(W / scale), sh = Math.round(H / scale);
        int sx = (src.getWidth() - sw) / 2, sy = (src.getHeight() - sh) / 2;
        new Canvas(out).drawBitmap(src, new Rect(sx, sy, sx + sw, sy + sh), new Rect(0, 0, W, H), new Paint(Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    private static Bitmap draw(String name, Bitmap icon) {
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint bg = new Paint();
        bg.setShader(new LinearGradient(0, 0, 0, H, 0xFF2A2F3A, 0xFF15171C, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, bg);
        Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
        accent.setColor(0xFFFFD97A);
        c.drawRect(0, H - 12, W, H, accent);
        if (icon != null) {
            int size = 300, left = (W - size) / 2, top = 190;
            c.drawBitmap(icon, new Rect(0, 0, icon.getWidth(), icon.getHeight()), new Rect(left, top, left + size, top + size), new Paint(Paint.FILTER_BITMAP_FLAG));
        }
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(0xFFFFFFFF);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(54);
        List<String> lines = wrap(name, 16, 3);
        float y = icon != null ? 610 : 380;
        for (String line : lines) {
            c.drawText(line, W / 2f, y, text);
            y += 66;
        }
        Paint brand = new Paint(Paint.ANTI_ALIAS_FLAG);
        brand.setColor(0xFFFFD97A);
        brand.setTextAlign(Paint.Align.CENTER);
        brand.setTextSize(30);
        c.drawText("AGVN", W / 2f, H - 50, brand);
        return out;
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
