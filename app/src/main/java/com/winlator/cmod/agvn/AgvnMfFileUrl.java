/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.WineRegistryEditor;

import java.io.File;
import java.util.Arrays;

/**
 * Media Foundation opens "file:///F:/AGVN/A-0.mp4" as Windows does. Wine's "file:" handler (mfplat, Proton 9) drops
 * only "file://" and opens "/F:/AGVN/A-0.mp4", which fails (0x80070003): a game that hands Media Foundation its movie
 * as such a URL never plays it, whatever its format. AVPro Video's Media Engine then said "Failed to create source
 * object, hr 0x80004003" and NZGY waited on "buffering" (POCO F8 Pro, 30/10/2026).
 *
 * <p>agvn-mfurl.dll (app/src/main/cpp/agvn/mf-file-url.c) is a "file:" handler that turns such a URL into its path
 * and hands it to Wine's handler; every other URL goes there unchanged. It goes into the container's system32 (64-bit)
 * and syswow64 (32-bit), its class into system.reg for both views, and it is listed as the "file:" handler in
 * user.reg: Wine's source resolver reads HKCU before HKLM, so Wine's own entry stays as it was. The registry is
 * written once per container ({@link #EXTRA}); the DLLs are checked at every start. Tested in Wine 9.0 by
 * scripts/agvn/mfurl/run-test.sh.
 */
public final class AgvnMfFileUrl {
    private static final String TAG = "AGVN";
    static final String CLSID = "{D241A047-AB6F-43EF-8C31-78A96B9D1EA2}";
    static final String DLL = "agvn-mfurl.dll", ASSET_64 = "agvn/agvn-mfurl64.dll", ASSET_32 = "agvn/agvn-mfurl32.dll";
    static final String PATH = "C:\\windows\\system32\\" + DLL;
    /** HKCU: the "file:" scheme's handlers, each a value named by its CLSID. */
    static final String HANDLERS = "Software\\Microsoft\\Windows Media Foundation\\SchemeHandlers\\file:";
    /** HKLM, the 64-bit and the 32-bit view. */
    static final String[] CLASS_KEYS = {"Software\\Classes\\CLSID\\" + CLSID + "\\InprocServer32",
            "Software\\Classes\\Wow6432Node\\CLSID\\" + CLSID + "\\InprocServer32"};
    /** Container extra: "1" once the registry holds the handler. */
    static final String EXTRA = "agvnMfFileUrl";

    private AgvnMfFileUrl() {}

    /**
     * Before Wine starts. Returns true when the container's extras changed (the caller saves). Never throws: a movie
     * must not stop a game from starting.
     */
    public static boolean apply(Context context, Container container) {
        try {
            File wine = new File(container.getRootDir(), ".wine");
            File windows = new File(wine, "drive_c/windows");
            if (!install(FileUtils.read(context, ASSET_64), new File(windows, "system32/" + DLL))) return false;
            File wow = new File(windows, "syswow64");
            if (wow.isDirectory()) install(FileUtils.read(context, ASSET_32), new File(wow, DLL));
            if ("1".equals(container.getExtra(EXTRA))) return false;
            File systemReg = new File(wine, "system.reg"), userReg = new File(wine, "user.reg");
            if (!systemReg.isFile() || !userReg.isFile()) return false;
            register(systemReg, userReg);
            container.putExtra(EXTRA, "1");
            Log.i(TAG, "Media Foundation file: URL handler registered");
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Media Foundation file: URL handler not set up", e);
            return false;
        }
    }

    /** The handler's class in {@code systemReg} (both views) and its place as the "file:" handler in {@code userReg}. */
    static void register(File systemReg, File userReg) {
        try (WineRegistryEditor editor = new WineRegistryEditor(systemReg)) {
            for (String key : CLASS_KEYS) {
                editor.setStringValue(key, null, PATH);
                editor.setStringValue(key, "ThreadingModel", "Both");
            }
        }
        try (WineRegistryEditor editor = new WineRegistryEditor(userReg)) {
            editor.setStringValue(HANDLERS, CLSID, "AGVN file: URL");
        }
    }

    /** Writes {@code dll} to {@code target} unless it is there already; false when it cannot be written. */
    static boolean install(byte[] dll, File target) {
        if (dll == null || dll.length == 0) return false;
        if (target.isFile() && target.length() == dll.length && Arrays.equals(dll, FileUtils.read(target))) return true;
        boolean ok = FileUtils.write(target, dll);
        if (!ok) Log.w(TAG, DLL + " not written to " + target);
        return ok;
    }
}
