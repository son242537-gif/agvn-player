/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.util.Locale;
import java.util.Map;

/**
 * A game that ships its exe twice: "&lt;Name&gt;.exe", 64-bit, and "&lt;Name&gt;-32.exe" for 32-bit Windows (Ren'Py 7).
 * A 32-bit game cannot map the GPU's memory with a driver that only maps it where a 64-bit program reaches (Mali):
 * DXVK logs "Mapping memory failed with VK_ERROR_MEMORY_MAP_FAILED" and makes no D3D11 device. Being Adik 0.12.1
 * (Ren'Py 7.4.10, realme RMX3686, Mali-G68, 17/10/2026) was added as BeingADIK-32.exe, which sorts before
 * BeingADIK.exe: OpenGL did not start (Zink), ANGLE then had no Direct3D, and every renderer of Ren'Py failed.
 * Games are added with the 64-bit exe ({@link GameExeResolver}); one added with the 32-bit exe is offered "exe-64",
 * which starts the 64-bit exe instead ({@link #EXTRA}). Only the command of the launch changes, as with
 * {@link AgvnExeRedirect}: the shortcut keeps its path.
 */
public final class AgvnExe64 {
    static final String EXTRA = "agvnExe64", FIX = "exe-64", SUFFIX = "-32.exe";
    private static final String TAG = "AGVN";
    private static final int AMD64 = 0x8664;
    private static volatile String note;

    private AgvnExe64() {}

    /** "&lt;Name&gt;.exe" when {@code exe} is "&lt;Name&gt;-32.exe" and that one is 64-bit and can start; else null. */
    static File twin(File exe) {
        String name = exe.getName();
        if (name.length() <= SUFFIX.length() || !name.toLowerCase(Locale.ROOT).endsWith(SUFFIX)) return null;
        File dir = exe.getParentFile();
        File twin = new File(dir, name.substring(0, name.length() - SUFFIX.length()) + name.substring(name.length() - 4));
        return twin.isFile() && AgvnPeFacts.machine(twin) == AMD64 && libsFor64(dir) ? twin : null;
    }

    /** False for a Ren'Py game whose lib holds the 32-bit Windows files only: its 64-bit exe would not start. */
    static boolean libsFor64(File dir) {
        File[] libs = new File(dir, "lib").listFiles();
        if (libs == null) return true;
        boolean i686 = false;
        for (File lib : libs) {
            String n = lib.getName().toLowerCase(Locale.ROOT);
            if (n.contains("windows-x86_64")) return true;
            i686 |= n.contains("windows-i686");
        }
        return !i686;
    }

    /** The exe {@code s} starts: {@code exe}, or its 64-bit twin once "exe-64" is on. */
    static File launched(Shortcut s, File exe) {
        File twin = on(s) ? twin(exe) : null;
        return twin != null ? twin : exe;
    }

    static boolean on(Shortcut s) {
        return "1".equals(s.getExtra(EXTRA));
    }

    /** "Chạy bản 64-bit của game (&lt;Name&gt;.exe)" for a game started with its 32-bit exe beside it; else null. */
    static AgvnFixes.Fix fix(Context a, Shortcut s) {
        String unix = on(s) ? null : AgvnExeRedirect.toUnixPath(s.path, s.container);
        File twin = unix != null ? twin(new File(unix)) : null;
        return twin != null ? new AgvnFixes.Fix(FIX, a.getString(R.string.agvn_fix_exe64, twin.getName()), "1") : null;
    }

    /**
     * {@code launch} (the exe as the launch command has it, quoted or not, with its arguments) with the 64-bit exe for
     * a game whose "exe-64" is on; else {@code launch} itself. Never throws.
     */
    public static String apply(String launch, Shortcut s, Container container) {
        note = null;
        if (launch == null || s == null || container == null || !on(s)) return launch;
        try {
            return apply(launch, AgvnExeRedirect.driveMap(container));
        } catch (RuntimeException e) {
            Log.w(TAG, "no 64-bit exe for " + launch, e);
            return launch;
        }
    }

    static String apply(String launch, Map<String, String> drives) {
        note = null;
        String[] exeAndArgs = AgvnExeRedirect.splitArgs(launch.replace("\"", "").trim());
        String from = exeAndArgs[0];
        String unix = AgvnExeRedirect.toUnixPath(from, drives);
        File twin = unix != null ? twin(new File(unix)) : null;
        int cut = Math.max(from.lastIndexOf('/'), from.lastIndexOf('\\'));
        if (twin == null || cut < 0) return launch;
        String to = from.substring(0, cut + 1) + twin.getName();
        note = "Chạy bản 64-bit của game: " + twin.getName() + " thay cho " + from.substring(cut + 1)
                + " (đã chọn trong Tự sửa lỗi)";
        Log.i(TAG, "64-bit exe " + from + " -> " + to);
        return "\"" + to + "\"" + exeAndArgs[1];
    }

    /** The line for the session's events, once; null when the game starts its own exe. */
    static String takeNote() {
        String n = note;
        note = null;
        return n;
    }
}
