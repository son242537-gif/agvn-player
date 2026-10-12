/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * DirectWrite (dwrite.dll, the text of many newer games through Direct2D) knows only the fonts listed under
 * HKLM\Software\Microsoft\Windows NT\CurrentVersion\Fonts, a bare file name meaning C:\windows\Fonts. Proton's
 * prefix lists Tahoma, Marlett, SimSun, MS Gothic… by bare name without their files, and of the fonts the app puts
 * there only Arial, Courier New and Times. Tahoma, DirectWrite's last resort, failed, and a game asking for a font
 * the prefix lacks drew no text (report 12/10/2026). Before Wine starts every listed font gets its file (Wine's own
 * copied in, another file named, an entry without any file removed) and unlisted fonts are listed. Wine's Tahoma
 * lacks 95 of the 134 Vietnamese letters and is DirectWrite's fallback, so DirectWrite alone gets "Tahoma" and
 * "Segoe UI" made from the app's DejaVu Sans, in Fonts\agvn: GDI does not look there, and skips the entry as it
 * knows Wine's Tahoma by that name.
 */
public final class AgvnDwriteFonts {
    private static final String TAG = "AGVN";
    static final String KEY = "Software\\\\Microsoft\\\\Windows NT\\\\CurrentVersion\\\\Fonts";
    static final String EXTERNAL = "Software\\\\Wine\\\\Fonts\\\\External Fonts";
    static final String MS = "usr/share/fonts/truetype/ms", DEJAVU = "usr/share/fonts/truetype/dejavu";
    static final String OURS = "C:\\windows\\Fonts\\agvn\\";
    private static final Map<String, String> WINE_NAMES = Collections.singletonMap("wingdings.ttf", "wingding.ttf");
    /** DirectWrite's Vietnamese faces: entry, Wine's file kept for GDI, the app's source, style. */
    private static final String[][] VIETNAMESE = {
            {"Tahoma (TrueType)", "tahoma.ttf", "DejaVuSans.ttf", "Regular"},
            {"Tahoma Bold (TrueType)", "tahomabd.ttf", "DejaVuSans-Bold.ttf", "Bold"}};
    private static volatile String note;

    private final File wine, fonts, wineFonts, imageFs;
    private final AgvnRegKey key;
    private final Map<String, File> here;
    private final Set<String> served = new HashSet<>();
    private final List<String> copied = new ArrayList<>(), named = new ArrayList<>(), fromApp = new ArrayList<>();
    private final List<String> removed = new ArrayList<>();
    private boolean vietnamese, madeVietnamese;

    private AgvnDwriteFonts(File wine, File fonts, File wineFonts, File imageFs, AgvnRegKey key) {
        this.wine = wine;
        this.fonts = fonts;
        this.wineFonts = wineFonts;
        this.imageFs = imageFs;
        this.key = key;
        here = AgvnFontFiles.listing(fonts);
    }

    /** The changes of the last launch for the session log, once; null when every font was in place. */
    public static String takeNote() {
        String n = note;
        note = null;
        return n;
    }

    /**
     * {@code wine}: the container's .wine; {@code wineFonts}: share/wine/fonts of its Wine (may be missing);
     * {@code imageFs}: the root that Z: shows. Never throws: fonts must not stop a game from starting.
     */
    public static void apply(File wine, File wineFonts, File imageFs) {
        try {
            File windows = AgvnFontFiles.resolve(wine, "drive_c/windows");
            File fonts = windows == null ? null : AgvnFontFiles.resolve(windows, "Fonts");
            File systemReg = new File(wine, "system.reg");
            if (fonts == null || !fonts.isDirectory() || !systemReg.isFile()) return;
            AgvnRegKey key = new AgvnRegKey(AgvnFontFiles.read(systemReg), KEY);
            AgvnDwriteFonts check = new AgvnDwriteFonts(wine, fonts, wineFonts, imageFs, key);
            int listed = check.run();
            String updated = key.text();
            if (updated != null) AgvnFontFiles.writeText(systemReg, updated);
            check.unlistExternal(new File(wine, "user.reg"));
            note = check.said(listed);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "DirectWrite fonts not checked", e);
        }
    }

    private int run() throws IOException {
        for (String[] face : VIETNAMESE) if (!here.containsKey(face[1])) copyWine(face[1], face[1]); // GDI's Tahoma
        for (Map.Entry<String, String> e : key.strings().entrySet()) entry(e.getKey(), e.getValue());
        for (String[] face : VIETNAMESE) vietnamese(face);
        return vietnamese ? AgvnFontFiles.listUnlisted(key, here, served, "Tahoma", "Segoe UI")
                : AgvnFontFiles.listUnlisted(key, here, served);
    }

    /** One entry: its file is there, or is put there, or another is named, or the entry goes. */
    private void entry(String name, String file) throws IOException {
        String low = file.toLowerCase(Locale.ROOT);
        if (low.startsWith(OURS.toLowerCase(Locale.ROOT))) return; // vietnamese() keeps these
        if (!AgvnFontFiles.bare(file)) {
            if (gone(file)) {
                key.remove(name);
                removed.add(file.substring(file.lastIndexOf('\\') + 1));
            }
            return;
        }
        if (here.containsKey(low) || copyWine(low, file)) {
            served.add(name);
            return;
        }
        File sibling = AgvnFontFiles.sibling(here, low);
        File ms = sibling != null ? null : AgvnFontFiles.find(new File(imageFs, MS), low);
        if (sibling != null) {
            key.set(name, sibling.getName());
            named.add(file + " → " + sibling.getName());
            served.add(name);
        } else if (ms != null) {
            key.set(name, "Z:\\" + MS.replace('/', '\\') + "\\" + ms.getName());
            fromApp.add(file);
        } else {
            key.remove(name);
            removed.add(file);
        }
    }

    /** DirectWrite's face from DejaVu Sans, while C:\windows\Fonts keeps Wine's for GDI; a player's Tahoma stays. */
    private void vietnamese(String[] face) throws IOException {
        File gdi = here.get(face[1]), wineOwn = wineFont(face[1]);
        String now = key.get(face[0]), path = OURS + face[1].replace(".ttf", ".ttc");
        File ours = new File(fonts, "agvn/" + face[1].replace(".ttf", ".ttc"));
        File source = AgvnFontFiles.find(new File(imageFs, DEJAVU), face[2]);
        boolean wineIsGdi = gdi != null && wineOwn != null && AgvnFontFiles.same(gdi, wineOwn);
        if (!wineIsGdi || (!ours.isFile() && source == null)) {
            if (gdi != null && path.equalsIgnoreCase(now)) key.set(face[0], gdi.getName());
            return;
        }
        if (!ours.isFile()) {
            try {
                AgvnFontFiles.makeCollection(source, face[3], ours, "Tahoma", "Segoe UI");
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "no Vietnamese " + face[0], e); // the other fixes still count
                if (path.equalsIgnoreCase(now)) key.set(face[0], gdi.getName());
                return;
            }
            madeVietnamese = true;
        }
        if (!path.equalsIgnoreCase(now)) madeVietnamese = true;
        key.set(face[0], path);
        served.add(face[0]);
        vietnamese = true;
    }

    /** Removes the fonts now served from C:\windows\Fonts from Wine's list of fonts found outside it. */
    private void unlistExternal(File userReg) throws IOException {
        if (served.isEmpty() || !userReg.isFile()) return;
        AgvnRegKey external = new AgvnRegKey(AgvnFontFiles.read(userReg), EXTERNAL);
        for (String name : external.strings().keySet())
            for (String s : served) if (s.equalsIgnoreCase(name)) external.remove(name);
        String updated = external.text();
        if (updated != null) AgvnFontFiles.writeText(userReg, updated);
    }

    private String said(int listed) {
        List<String> said = new ArrayList<>();
        if (!copied.isEmpty()) said.add("chép " + AgvnFontFiles.few(copied) + " của Wine vào C:\\windows\\Fonts");
        if (!named.isEmpty()) said.add("ghi " + AgvnFontFiles.few(named));
        if (!fromApp.isEmpty()) said.add("lấy " + AgvnFontFiles.few(fromApp) + " trong app");
        if (!removed.isEmpty()) said.add("bỏ mục không có file: " + AgvnFontFiles.few(removed));
        if (madeVietnamese) said.add("Tahoma, Segoe UI đủ chữ Việt cho DirectWrite (từ DejaVu Sans)");
        if (listed > 0) said.add("thêm " + listed + " phông có sẵn vào danh sách");
        return said.isEmpty() ? null : "Phông DirectWrite: " + String.join("; ", said);
    }

    /** True for a path on C: or Z: (inside the app) whose file is not there. */
    private boolean gone(String path) {
        String p = path.replace('\\', '/');
        if (p.length() < 4 || p.charAt(1) != ':' || p.charAt(2) != '/') return false;
        char drive = Character.toLowerCase(p.charAt(0));
        File root = drive == 'c' ? new File(wine, "drive_c") : drive == 'z' ? imageFs : null;
        return root != null && AgvnFontFiles.resolve(root, p.substring(3)) == null;
    }

    private File wineFont(String low) {
        if (wineFonts == null) return null;
        String file = WINE_NAMES.containsKey(low) ? WINE_NAMES.get(low) : low;
        return AgvnFontFiles.find(wineFonts, file);
    }

    private boolean copyWine(String low, String as) throws IOException {
        File from = wineFont(low);
        if (from == null) return false;
        File to = new File(fonts, as);
        AgvnFontFiles.copy(from, to);
        here.put(as.toLowerCase(Locale.ROOT), to);
        copied.add(as);
        return true;
    }
}
