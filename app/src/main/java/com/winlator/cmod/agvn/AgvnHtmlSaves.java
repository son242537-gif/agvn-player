/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;
import android.webkit.JavascriptInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

/**
 * An RPG Maker MV/MZ game's saves as files in its save folder (save/ beside its index.html), where the game keeps them
 * on a PC: MV's file1.rpgsave, global.rpgsave, config.rpgsave, MZ's file1.rmmzsave... In a phone browser the engine
 * keeps them in the page's own storage instead, which neither "Nhập save" / "Xuất save" nor the game's Windows version
 * reaches. html-compat.js hands them here (window.{@value #NAME}) in the same text the PC writes (UTF-8, as NW.js does),
 * so the files go both ways. Only save names, never a path. WebView calls it on its own thread.
 */
final class AgvnHtmlSaves {
    static final String NAME = "AgvnSaves", FOLDER = "save";
    private static final String TAG = "AGVN";
    private static final Pattern FILE = Pattern.compile("[A-Za-z0-9_-]{1,40}\\.(?:rpgsave|rmmzsave)");

    private final File dir;

    /** {@code indexDir}: the folder of the game's index.html (www/ for an MV game made for PC). */
    AgvnHtmlSaves(File indexDir) {
        dir = new File(indexDir, FOLDER);
    }

    /** True for a save's file name ("file1.rpgsave"): no folder, no other kind of file. */
    static boolean valid(String name) {
        return name != null && FILE.matcher(name).matches();
    }

    /** The save's text, or null when there is none or it cannot be read. */
    @JavascriptInterface
    public String read(String name) {
        if (!valid(name)) return null;
        File f = new File(dir, name);
        try {
            return f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            Log.w(TAG, "save " + f + " not read", e);
            return null;
        }
    }

    /** Writes a save; the one it replaces stays whole until the new one is on disk. False when it could not. */
    @JavascriptInterface
    public boolean write(String name, String text) {
        if (!valid(name) || text == null) return false;
        File f = new File(dir, name), part = new File(dir, name + ".agvn-part");
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
            try (FileOutputStream out = new FileOutputStream(part)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            // a rename replaces the old save at once; a storage that will not rename over a file gets it removed first
            if (!part.renameTo(f) && !(f.delete() && part.renameTo(f))) throw new IOException("cannot replace " + f);
            return true;
        } catch (IOException e) {
            Log.w(TAG, "save " + f + " not written", e);
            part.delete();
            return false;
        }
    }

    @JavascriptInterface
    public boolean exists(String name) {
        return valid(name) && new File(dir, name).isFile();
    }

    @JavascriptInterface
    public void remove(String name) {
        File f = valid(name) ? new File(dir, name) : null;
        if (f != null && f.isFile() && !f.delete()) Log.w(TAG, "save " + f + " not deleted");
    }

    /** True when saves can be written here; else the game keeps saving in its browser storage, as before. */
    @JavascriptInterface
    public boolean writable() {
        File probe = new File(dir, ".agvn-probe");
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return false;
            try (FileOutputStream out = new FileOutputStream(probe)) {
                out.write(0);
            }
            return probe.delete();
        } catch (IOException e) {
            Log.w(TAG, "save folder " + dir + " cannot be written: saves stay in the browser", e);
            return false;
        }
    }

    /** True when the folder holds a save: the game's saves are then these files, not what the browser kept before. */
    @JavascriptInterface
    public boolean any() {
        String[] names = dir.list();
        if (names != null) for (String n : names) if (valid(n)) return true;
        return false;
    }
}
