/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Another library entry of the same game ({@link AgvnGameVersions#sameGame}: the same exe, and Unity's company and
 * product or the title without its version) whose settings differ. Each entry keeps its own settings, so the fixes a
 * player took on one entry did nothing for the other: Toeha was fixed five times on "Toeha" (driver, DXVK, present
 * mode) while the player went on opening "Toeha Player", the same game in another folder, which kept freezing with
 * its first settings; nothing said so (vivo V2352A, 0.1.97, 20/11/2026).
 *
 * <p>The other entry's settings are never taken by themselves: that entry may be the one that runs well. "Tự sửa lỗi"
 * offers them when this entry shows a problem ({@link #FIX}, put first when the other entry has run well with them),
 * and the session's events name the other entry, so a log tells which settings ran.
 *
 * <p>The library is read once per dialog, and the entry found stays with its button ({@link AgvnFixes.Fix#twin}):
 * 0.1.98 read it up to four times on the UI thread, over a running game too. An entry on "Chạy nhẹ" is no twin: its
 * Wine settings are the import's, never run (audit of 20/11/2026).
 */
public final class AgvnGameTwin {
    private static final String TAG = "AGVN";
    static final String FIX = "twin-config";
    private static volatile String note;

    private AgvnGameTwin() {}

    /** The entry of the same game as {@code s}, run by Wine too, whose settings differ from its own, or null. */
    static Shortcut find(Context ctx, Shortcut s) {
        try {
            List<Shortcut> library = new ArrayList<>();
            for (Shortcut o : new ContainerManager(ctx).loadShortcuts()) {
                if (runsOnWine(o.getExtra(AgvnHtmlGame.EXTRA_RUNNER))) library.add(o);
            }
            Shortcut twin = AgvnGameVersions.twin(library, s);
            return twin != null && differ(AgvnGoodConfig.snapshot(twin), AgvnGoodConfig.snapshot(s)) ? twin : null;
        } catch (RuntimeException e) {
            Log.w(TAG, "same game's other entry not looked for", e);
            return null;
        }
    }

    /** True for the runner of an entry Wine runs ("" or "wine"), whose settings are worth taking. */
    static boolean runsOnWine(String runner) {
        return !AgvnHtmlGame.isLight(runner);
    }

    static boolean differ(Map<String, String> twin, Map<String, String> own) {
        return !AgvnGoodConfig.changed(twin, own).isEmpty();
    }

    /** "Dùng cấu hình của mục «Toeha» (cùng game này)" when there is such an entry; else null. */
    static AgvnFixes.Fix fix(Activity a, Shortcut s) {
        Shortcut twin = find(a, s);
        if (twin == null) return null;
        AgvnFixes.Fix fix = new AgvnFixes.Fix(FIX, a.getString(R.string.agvn_fix_twin_config, twin.name), twin.file.getPath());
        fix.twin = twin;
        return fix;
    }

    /** True when the other entry has run well with the settings it has now: its fix then goes first. */
    static boolean ranWell(Context ctx, Shortcut twin) {
        Properties state = AgvnGoodConfig.load(ctx, twin);
        return ranWell(AgvnGoodConfig.hasGood(state), AgvnGoodConfig.good(state), AgvnGoodConfig.snapshot(twin));
    }

    static boolean ranWell(boolean hasGood, Map<String, String> good, Map<String, String> now) {
        return hasGood && AgvnGoodConfig.changed(good, now).isEmpty();
    }

    /** {@code s} takes the settings of {@code fix}'s entry, read again from its file; false when that entry is gone. */
    static boolean apply(Context ctx, Shortcut s, AgvnFixes.Fix fix) {
        Shortcut twin = fix.twin == null ? byFile(ctx, fix.to)
                : fix.twin.file.isFile() ? new Shortcut(fix.twin.container, fix.twin.file) : null;
        if (twin == null) return false;
        AgvnGoodConfig.restore(s, AgvnGoodConfig.snapshot(twin));
        Log.i(TAG, "settings of " + twin.name + " taken for " + s.name);
        return true;
    }

    /** The entry in {@code file}, by its folder: the one container whose entries are there, and no other file read. */
    private static Shortcut byFile(Context ctx, String file) {
        File entry = new File(file);
        if (!entry.isFile()) return null;
        for (Container c : new ContainerManager(ctx).getContainers()) {
            if (c.getDesktopDir().equals(entry.getParentFile())) return new Shortcut(c, entry);
        }
        return null;
    }

    /** Before the session log starts: the line that names the same game's other entry, if any. */
    public static void prepareNote(Context ctx, Shortcut s) {
        Shortcut twin = s != null ? find(ctx, s) : null;
        note = twin == null ? null : "Thư viện có mục khác của cùng game: «" + twin.name + "», cấu hình khác mục này («"
                + s.name + "»). Sửa lỗi ở mục nào chỉ có tác dụng với mục đó.";
    }

    static String takeNote() {
        String n = note;
        note = null;
        return n;
    }
}
