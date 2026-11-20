/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.Environment;
import android.text.format.DateUtils;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.MainActivity;
import com.winlator.cmod.R;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Thêm game" on a game the library already has from another folder ({@link AgvnGameVersions#older}: the same exe, and
 * Unity's company and product or the title without its version): asked once before it is added, saying where the copy
 * in the library is. A player who downloaded or unpacked a game twice got two entries, each with its own settings, and
 * fixed one while playing the other ({@link AgvnGameTwin}). Nothing is refused: "Thêm cả hai" adds it exactly as
 * before (a new version still joins the old one's container and saves). The same folder added again is no second copy
 * and is not asked about.
 */
final class AgvnImportTwin {
    private static final String TAG = "AGVN";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private AgvnImportTwin() {}

    /** {@code proceed}: adds the game; runs at once when the library has no other copy. */
    static void ask(MainActivity a, AgvnGameImporter.Candidate c, AgvnLibraryIndex.Existing existing, Runnable proceed) {
        if (existing != null) { // this folder's own entry, updated
            proceed.run();
            return;
        }
        File exe = new File(c.gameDir, c.exe);
        IO.execute(() -> { // the library is read from storage
            Shortcut found = null;
            try {
                found = AgvnGameVersions.older(new ContainerManager(a).loadShortcuts(), c.profile.name, exe);
            } catch (RuntimeException e) {
                Log.w(TAG, "library not searched for another copy", e);
            }
            Shortcut twin = found;
            File dir = twin != null ? folderOf(twin) : null;
            boolean there = dir != null && dir.isDirectory();
            a.runOnUiThread(() -> {
                if (a.isFinishing() || a.isDestroyed()) return;
                if (twin == null) proceed.run();
                else show(a, c, twin, dir, there, proceed);
            });
        });
    }

    private static void show(MainActivity a, AgvnGameImporter.Candidate c, Shortcut twin, File dir, boolean there, Runnable proceed) {
        String internal = Environment.getExternalStorageDirectory().getAbsolutePath(), label = a.getString(R.string.agvn_internal_storage);
        String old = dir != null ? where(dir.getAbsolutePath(), internal, label) : "?";
        String now = where(c.gameDir.getAbsolutePath(), internal, label);
        new AlertDialog.Builder(a)
                .setTitle(R.string.agvn_dup_title)
                .setMessage(a.getString(there ? R.string.agvn_dup_message : R.string.agvn_dup_message_gone, twin.name, old, now))
                .setPositiveButton(R.string.agvn_dup_keep_both, (d, w) -> proceed.run())
                .setNeutralButton(R.string.agvn_dup_show, (d, w) -> info(a, twin, dir, there, old, proceed))
                .setNegativeButton(R.string.agvn_cancel, null)
                .show();
    }

    /** The copy in the library: its folder, its exe, when it was last played. */
    private static void info(MainActivity a, Shortcut twin, File dir, boolean there, String old, Runnable proceed) {
        long last = 0;
        try {
            last = Long.parseLong(twin.getExtra("lastRunAt"));
        } catch (NumberFormatException e) {
            // never played
        }
        String played = last > 0 ? DateUtils.formatDateTime(a, last, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR)
                : a.getString(R.string.agvn_dup_never_played);
        String message = a.getString(R.string.agvn_dup_info, old, AgvnGameVersions.exeOf(twin).getName(), played);
        if (!there) message += "\n\n" + a.getString(R.string.agvn_dup_info_gone);
        new AlertDialog.Builder(a)
                .setTitle(twin.name)
                .setMessage(message)
                .setPositiveButton(R.string.agvn_dup_keep_both, (d, w) -> proceed.run())
                .setNegativeButton(R.string.agvn_cancel, null)
                .show();
    }

    /** The game folder of a library entry: the one it was added from, else its exe's. */
    static File folderOf(Shortcut s) {
        String dir = s.getExtra(AgvnGameImporter.EXTRA_GAME_DIR);
        return !dir.isEmpty() ? new File(dir) : AgvnGameVersions.exeOf(s).getParentFile();
    }

    /** "Bộ nhớ trong/Download/Game" for a folder of internal storage ({@code internalRoot}), else the path as it is. */
    static String where(String path, String internalRoot, String internalLabel) {
        if (path.equals(internalRoot)) return internalLabel;
        return path.startsWith(internalRoot + "/") ? internalLabel + path.substring(internalRoot.length()) : path;
    }
}
