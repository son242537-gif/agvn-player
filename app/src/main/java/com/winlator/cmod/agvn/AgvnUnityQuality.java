/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.WineRegistryEditor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Starts a Unity game at its first (lowest) quality level, which usually means smaller textures and fewer shadows,
 * by writing the PlayerPrefs value Unity reads at startup:
 * {@code HKCU\Software\<company>\<product>\UnityGraphicsQuality_h1669003810 = 0}. Company and product are the first
 * two lines of {@code <exe>_Data/app.info}. A higher step removes the value again, but only if AGVN wrote it, so a
 * choice the game saved there itself is kept. Games that keep their own settings file override it.
 */
final class AgvnUnityQuality {
    private static final String TAG = "AGVN";
    /** "UnityGraphicsQuality" plus "_h" and its djb2-xor hash, as Unity names PlayerPrefs registry values. */
    static final String VALUE = "UnityGraphicsQuality_h1669003810";
    static final String EXTRA_WRITTEN = "agvnUnityQuality";

    private AgvnUnityQuality() {}

    /** Registry key (relative to HKCU) of the game's PlayerPrefs, or null when app.info lacks company or product. */
    static String prefsKey(File appInfo) throws IOException {
        String[] names = companyProduct(appInfo);
        return names != null ? "Software\\" + names[0] + "\\" + names[1] : null;
    }

    /** {company, product} from the first two lines of app.info, or null when either is missing. */
    static String[] companyProduct(File appInfo) throws IOException {
        List<String> lines = Files.readAllLines(appInfo.toPath(), StandardCharsets.UTF_8);
        if (lines.size() < 2) return null;
        String company = lines.get(0).trim(), product = lines.get(1).trim();
        return company.isEmpty() || product.isEmpty() ? null : new String[]{company, product};
    }

    /** {@code <exe>_Data/app.info} next to the exe, else the only {@code *_Data/app.info} in the exe's folder. */
    static File findAppInfo(File exe) {
        File dir = exe.getParentFile();
        if (dir == null) return null;
        String name = exe.getName();
        int dot = name.lastIndexOf('.');
        File byExe = new File(dir, (dot > 0 ? name.substring(0, dot) : name) + "_Data/app.info");
        if (byExe.isFile()) return byExe;
        File[] data = dir.listFiles((d, n) -> n.endsWith("_Data") && new File(d, n + "/app.info").isFile());
        return data != null && data.length == 1 ? new File(data[0], "app.info") : null;
    }

    static void apply(Shortcut shortcut, File userReg, boolean lowest) throws IOException {
        boolean written = "1".equals(shortcut.getExtra(EXTRA_WRITTEN));
        if (lowest == written || !userReg.isFile()) return;
        File appInfo = findAppInfo(new File(shortcut.path.replace("\"", "")));
        String key = appInfo != null ? prefsKey(appInfo) : null;
        if (key == null) return;
        set(userReg, key, lowest);
        shortcut.putExtra(EXTRA_WRITTEN, lowest ? "1" : null);
        shortcut.saveData();
        Log.i(TAG, "Unity quality " + (lowest ? "set to the lowest level" : "back to the game's own") + ": " + key);
    }

    static void set(File userReg, String key, boolean lowest) {
        try (WineRegistryEditor editor = new WineRegistryEditor(userReg)) {
            if (lowest) editor.setDwordValue(key, VALUE, 0);
            else editor.removeValue(key, VALUE);
        }
    }
}
