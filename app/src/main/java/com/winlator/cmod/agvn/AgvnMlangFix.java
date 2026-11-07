/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.FileUtils;

import java.io.File;
import java.util.Arrays;

/**
 * "Sửa lỗi vẽ chữ Việt của .NET" in "Tự sửa lỗi", for a .NET game that crashed: Proton 9's GDI+ (System.Drawing)
 * reads a NULL font for a string that is one Vietnamese letter, or ends with one ("ắ", "ệ"), and the game ends with
 * 0xC0000005 at its first Vietnamese menu, with no line in Wine's log (POCO F8 Pro, 07/11/2026). AGVN's mlang.dll
 * (app/src/main/cpp/agvn/mlang-fontlink.c) goes beside the game's exe; GameDllOverrides loads it before Wine's, and it
 * keeps the game's font for such letters. The fix is a setting of the game ({@link #EXTRA}), which "Vẫn còn lỗi" puts
 * back as it does the others (AgvnRepair.MORE_KEYS): at each start {@link #atLaunch} puts the file there while it is on
 * and takes AGVN's file away when it is off. A mlang.dll of the game's own is never touched, and none is offered.
 */
public final class AgvnMlangFix {
    static final String FIX = "gdiplus-text", EXTRA = "agvnMlang", DLL = "mlang.dll";
    static final String ASSET_32 = "agvn/agvn-mlang32.dll", ASSET_64 = "agvn/agvn-mlang64.dll";
    private static final String TAG = "AGVN";
    private static volatile String note;

    private AgvnMlangFix() {}

    static boolean on(Shortcut s) {
        return "1".equals(s.getExtra(EXTRA));
    }

    /** The button for a .NET game without a mlang.dll of its own beside its exe, or null. */
    static AgvnFixes.Fix fix(Context a, Shortcut s) {
        if (on(s)) return null;
        File exe = exe(s);
        if (exe == null || !AgvnWineMono.isDotNet(exe)) return null;
        File dll = new File(exe.getParentFile(), DLL);
        if (dll.exists() && !ours(a, dll)) return null;
        return new AgvnFixes.Fix(FIX, a.getString(R.string.agvn_fix_gdiplus_text), "1");
    }

    /**
     * Before Wine starts (XServerDisplayActivity, before GameDllOverrides reads the exe's folder): AGVN's mlang.dll
     * beside {@code exePath}, the exe the game starts, while the fix is on, and not there when it is off. Never throws.
     */
    public static void atLaunch(Context ctx, Shortcut s, String exePath, Container container) {
        note = null;
        if (s == null || exePath == null) return;
        try {
            String unix = AgvnExeRedirect.toUnixPath(exePath, container);
            File dir = unix != null ? new File(unix).getParentFile() : null;
            if (dir == null) return;
            File dll = new File(dir, DLL);
            if (!on(s)) {
                if (dll.isFile() && ours(ctx, dll) && dll.delete()) Log.i(TAG, "AGVN mlang.dll taken away: " + dll);
                return;
            }
            byte[] want = FileUtils.read(ctx, AgvnClrHeader.runs64(new File(unix)) ? ASSET_64 : ASSET_32);
            if (dll.isFile() && want != null && Arrays.equals(want, FileUtils.read(dll))) {
                note = "Sửa lỗi vẽ chữ Việt của .NET: đang dùng mlang.dll của AGVN cạnh file chạy của game";
            } else if (dll.exists() && !ours(ctx, dll)) {
                note = "Sửa lỗi vẽ chữ Việt của .NET: game có mlang.dll riêng, app để nguyên";
            } else if (want != null && FileUtils.write(dll, want)) {
                note = "Sửa lỗi vẽ chữ Việt của .NET: đã đặt mlang.dll của AGVN cạnh file chạy của game (" + dll + ")";
            } else {
                note = "Sửa lỗi vẽ chữ Việt của .NET: không ghi được " + dll + " (thư mục game chỉ đọc?)";
            }
            Log.i(TAG, note);
        } catch (RuntimeException e) {
            Log.w(TAG, "mlang.dll not prepared", e);
        }
    }

    /** True when {@code dll} is one of AGVN's two mlang.dll files. */
    static boolean ours(Context ctx, File dll) {
        byte[] now = FileUtils.read(dll);
        return now != null && (same(now, FileUtils.read(ctx, ASSET_32)) || same(now, FileUtils.read(ctx, ASSET_64)));
    }

    static boolean same(byte[] a, byte[] b) {
        return a != null && b != null && Arrays.equals(a, b);
    }

    /** {@code ev} for a .NET game that ended with {@code crashCode} (0: none, {@link AgvnGameEnd#crashCode}). */
    static void evidence(AgvnEvidence ev, Shortcut s, int crashCode) {
        File exe = exe(s);
        ev.dotNet = exe != null && AgvnWineMono.isDotNet(exe);
        ev.exitCrash = crashCode != 0;
        if (crashCode != 0) ev.params.putIfAbsent("crash", "mã thoát " + AgvnGameEnd.code(crashCode));
    }

    /** The exe the game starts (its 64-bit or main exe when a fix picked one), or null. */
    static File exe(Shortcut s) {
        String unix = AgvnExeRedirect.toUnixPath(s.path, s.container);
        if (unix == null) return null;
        return AgvnExeMain.launched(s, AgvnExe64.launched(s, new File(unix)));
    }

    /** The line for the session's events, once; null when the fix is off. */
    static String takeNote() {
        String n = note;
        note = null;
        return n;
    }
}
