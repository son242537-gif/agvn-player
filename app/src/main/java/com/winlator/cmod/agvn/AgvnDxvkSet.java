/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.container.Container;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What one DXVK set leaves for the next. DXVK 1.10.3 has a d3d10.dll and a d3d10_1.dll of its own; DXVK 2 has neither,
 * and works under Wine's. Unpacked over 1.10.3 (the Doctor's "Đổi DXVK sang bản 1.10.3", or a phone driver without
 * Vulkan 1.3), 2.3.1 kept them, and they ask d3d10core.dll for "D3D10CoreCreateDevice@20", a name only 1.10.3's
 * d3d10core has: an SRPG Studio game (Direct3D 10, 32-bit) closed with "unimplemented function
 * D3D10CORE.DLL.D3D10CoreCreateDevice@20" and "ゲームの起動に失敗しました。 B-Phese 2" (POCO, 05/11/2026). Wine's own
 * copies now go back before a DXVK set is unpacked, which puts its own over them when it has them. Pure Java.
 */
public final class AgvnDxvkSet {
    /** Wine's own, put back before any DXVK set. */
    static final String[] WINE_OWN = {"d3d10.dll", "d3d10_1.dll"};
    /** Container extra: its DXVK set was unpacked by an app that puts {@link #WINE_OWN} back first. */
    static final String EXTRA = "agvnDxvkSet", DONE = "1";
    /** Wine's file and the one it replaced in the prefix, until the set is unpacked; then the line for the log. */
    private static final List<File[]> replaced = new ArrayList<>();
    private static volatile String note;

    private AgvnDxvkSet() {}

    /** True once for a container whose set an older app unpacked: the set is unpacked again, as for a new one. */
    public static boolean stale(Container container) {
        return !DONE.equals(container.getExtra(EXTRA));
    }

    /** After a set is unpacked: the container is marked, and the files the set did not put its own over are noted. */
    public static void unpacked(Container container, File windowsDir) {
        container.putExtra(EXTRA, DONE);
        noteKept(windowsDir);
    }

    static synchronized void noteKept(File windowsDir) {
        List<String> kept = kept(replaced, windowsDir);
        replaced.clear();
        if (!kept.isEmpty()) {
            note = "Bộ DXVK: " + String.join(", ", kept) + " của bản DXVK trước còn lại, đã thay bằng bản của Wine"
                    + " (bộ DXVK đang dùng không có các file này)";
        }
    }

    /**
     * Puts Wine's {@link #WINE_OWN} back in {@code windowsDir}'s system32 and syswow64, from {@code winePath}'s
     * lib/wine (aarch64-windows for an arm64ec Wine, else x86_64-windows; i386-windows for 32-bit games).
     */
    public static synchronized void putWineOwnBack(File windowsDir, String winePath, boolean arm64ec) {
        File lib = new File(winePath, "lib/wine");
        replaced.addAll(putBack(windowsDir, new File(lib, arm64ec ? "aarch64-windows" : "x86_64-windows"),
                new File(lib, "i386-windows")));
    }

    /** {Wine's, the prefix's} for each file put back in place of another copy; a missing one is put back unlisted. */
    static List<File[]> putBack(File windowsDir, File wine64, File wine32) {
        List<File[]> back = new ArrayList<>();
        for (String dll : WINE_OWN) {
            File[][] pairs = {{new File(wine64, dll), new File(windowsDir, "system32/" + dll)},
                    {new File(wine32, dll), new File(windowsDir, "syswow64/" + dll)}};
            for (File[] pair : pairs) if (putBack(pair[0], pair[1])) back.add(pair);
        }
        return back;
    }

    /** Of {@code pairs}, the ones still Wine's copy ("syswow64/d3d10.dll"): the new set had none of its own. */
    static List<String> kept(List<File[]> pairs, File windowsDir) {
        List<String> kept = new ArrayList<>();
        String prefix = windowsDir.getPath() + File.separator;
        for (File[] pair : pairs) {
            try {
                if (!same(pair[0], pair[1])) continue;
            } catch (IOException e) {
                continue;
            }
            String path = pair[1].getPath();
            kept.add(path.startsWith(prefix) ? path.substring(prefix.length()) : path);
        }
        return kept;
    }

    /** Copies {@code wine} over {@code there} unless it is the same file; true when another file was replaced. */
    static boolean putBack(File wine, File there) {
        if (!wine.isFile()) return false;
        boolean other = there.isFile();
        try {
            if (other && same(wine, there)) return false;
            File dir = there.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return false;
            Files.copy(wine.toPath(), there.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return other;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean same(File a, File b) throws IOException {
        if (a.length() != b.length()) return false;
        return Arrays.equals(Files.readAllBytes(a.toPath()), Files.readAllBytes(b.toPath()));
    }

    /** The line for the session's events, once; null when nothing was left over. */
    static String takeNote() {
        String n = note;
        note = null;
        return n;
    }
}
