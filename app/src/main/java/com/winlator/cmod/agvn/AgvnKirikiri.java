/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A KiriKiri game's own size, from the ";scWidth = 1280;" and ";scHeight = 720;" lines of its system/Config.tjs (KAG),
 * for the screen it gets ({@link AgvnKirikiriScreen}). Config.tjs is read where the game reads it: the last patch
 * archive (patch3.xp3, patch2.xp3, patch.xp3), data.xp3, the unpacked data folder, then the other archives, smallest
 * first.
 */
public final class AgvnKirikiri {
    private static final String TAG = "AGVN";
    /** The game's own size, "1280x720", read at import or at its first start; "-" when it cannot be read. */
    public static final String EXTRA_GAME_SIZE = "agvnGameSize";
    /** {@link #EXTRA_GAME_SIZE} of a KiriKiri game whose size could not be read. */
    static final String UNKNOWN = "-";
    private static final String CONFIG = "config.tjs";
    static final int MAX_CONFIG = 1 << 20, MAX_ARCHIVES = 6;
    private static final Pattern SIZE = Pattern.compile("(?m)^[ \\t]*;?[ \\t]*sc(Width|Height)[ \\t]*=[ \\t]*(\\d{3,4})\\b");
    private static final Pattern PATCH = Pattern.compile("patch(\\d*)\\.xp3", Pattern.CASE_INSENSITIVE);

    private AgvnKirikiri() {}

    /** A KiriKiri game imported before its size was read gets it at its start. Never throws. */
    public static void recognize(Shortcut s) {
        if (s == null || !GameExeResolver.Engine.KIRIKIRI.name().equals(s.getExtra(AgvnGameImporter.EXTRA_ENGINE))
                || !s.getExtra(EXTRA_GAME_SIZE).isEmpty()) return;
        try {
            String size = gameSize(new File(s.getExtra(AgvnGameImporter.EXTRA_GAME_DIR)));
            s.putExtra(EXTRA_GAME_SIZE, size != null ? size : UNKNOWN);
            s.saveData();
        } catch (RuntimeException e) {
            Log.w(TAG, "KiriKiri size not kept", e);
        }
    }

    /** "1280x720" from the game folder's Config.tjs, or null. Never throws. */
    static String gameSize(File gameDir) {
        File[] files = gameDir.listFiles();
        if (files == null) return null;
        boolean looseRead = false;
        for (File archive : archives(files)) {
            if (!looseRead && !main(archive)) { // the unpacked data folder comes before the other archives
                looseRead = true;
                String size = loose(gameDir);
                if (size != null) return size;
            }
            String size = inArchive(archive);
            if (size != null) return size;
        }
        return looseRead ? null : loose(gameDir);
    }

    private static String inArchive(File archive) {
        try {
            for (AgvnXp3.Entry e : systemFirst(AgvnXp3.find(archive, CONFIG))) {
                String size = fromConfig(AgvnXp3.read(archive, e, MAX_CONFIG));
                if (size != null) return found(size, archive.getName() + ">" + e.path);
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "KiriKiri archive not read: " + archive.getName() + ": " + e);
        }
        return null;
    }

    /** "1280x720" from the bytes of a Config.tjs, or null (an encrypted one too). */
    static String fromConfig(byte[] data) {
        String text = data != null ? AgvnTjsText.decode(data, MAX_CONFIG * 2) : null;
        if (text == null) return null;
        Matcher m = SIZE.matcher(text.replaceAll("(?s)/\\*.*?\\*/", ""));
        String width = null, height = null;
        while (m.find()) {
            if (m.group(1).equals("Width")) width = m.group(2);
            else height = m.group(2);
        }
        return width != null && height != null ? AgvnScreenSize.normalize(width + "x" + height) : null;
    }

    /** Patches (the highest number first), data.xp3, then up to {@link #MAX_ARCHIVES} others, smallest first. */
    static List<File> archives(File[] files) {
        List<File> patches = new ArrayList<>(), data = new ArrayList<>(), others = new ArrayList<>();
        for (File f : files) {
            String name = f.getName();
            if (!f.isFile() || !name.toLowerCase(Locale.ROOT).endsWith(".xp3")) continue;
            Matcher m = PATCH.matcher(name);
            if (m.matches()) patches.add(f);
            else if (name.equalsIgnoreCase("data.xp3")) data.add(f);
            else others.add(f);
        }
        patches.sort(Comparator.comparingInt(AgvnKirikiri::patchNumber).reversed());
        others.sort(Comparator.comparingLong(File::length));
        List<File> out = new ArrayList<>(patches);
        out.addAll(data);
        out.addAll(others.subList(0, Math.min(MAX_ARCHIVES, others.size())));
        return out;
    }

    private static int patchNumber(File f) {
        Matcher m = PATCH.matcher(f.getName());
        return m.matches() && !m.group(1).isEmpty() ? Integer.parseInt(m.group(1)) : 1;
    }

    /** system/Config.tjs before a Config.tjs elsewhere. */
    private static List<AgvnXp3.Entry> systemFirst(List<AgvnXp3.Entry> entries) {
        List<AgvnXp3.Entry> out = new ArrayList<>(entries);
        out.sort(Comparator.comparingInt(e -> e.path.toLowerCase(Locale.ROOT).equals("system/" + CONFIG) ? 0 : 1));
        return out;
    }

    /** A patch or data.xp3: read before the unpacked data folder. */
    private static boolean main(File archive) {
        return PATCH.matcher(archive.getName()).matches() || archive.getName().equalsIgnoreCase("data.xp3");
    }

    /** The unpacked game: data/system/Config.tjs or system/Config.tjs. */
    private static String loose(File gameDir) {
        for (String[] path : new String[][]{{"data", "system", CONFIG}, {"system", CONFIG}}) {
            File f = gameDir;
            for (String part : path) f = f != null ? child(f, part) : null;
            if (f == null || !f.isFile() || f.length() > MAX_CONFIG) continue;
            try {
                String size = fromConfig(Files.readAllBytes(f.toPath()));
                if (size != null) return found(size, f.getPath());
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "KiriKiri config not read: " + f + ": " + e);
            }
        }
        return null;
    }

    /** {@code name} in {@code dir}, in any case (Android's storage tells "System" from "system"). */
    private static File child(File dir, String name) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) if (f.getName().equalsIgnoreCase(name)) return f;
        return null;
    }

    private static String found(String size, String where) {
        Log.i(TAG, "KiriKiri game size " + size + " (" + where + ")");
        return size;
    }

    static int[] parse(String size) {
        String s = AgvnScreenSize.normalize(size);
        if (s == null) return null;
        String[] wh = s.split("x");
        return new int[]{Integer.parseInt(wh[0]), Integer.parseInt(wh[1])};
    }

}
