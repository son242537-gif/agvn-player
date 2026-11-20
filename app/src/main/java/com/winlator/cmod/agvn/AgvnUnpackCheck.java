/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.MainActivity;
import com.winlator.cmod.R;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Right after an archive is unpacked: is it a game the app can add? A game goes to the settings preview
 * ({@link AgvnUnpackFlow#unpacked}). When {@link AgvnGameScanner} finds none, the player is told what the archive held
 * instead ({@link AgvnUnpackedKind}): the Android version (an .apk, installed like any app, not added here), an archive
 * inside the archive (unpacked too when the player agrees, with the same password tried first; the outer folder then
 * goes), the macOS or Linux version, or nothing the app knows.
 */
final class AgvnUnpackCheck {
    private static final String TAG = "AGVN";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    /** Archives in archives are followed this deep. */
    private static final int MAX_INNER = 2;

    private AgvnUnpackCheck() {}

    /** {@code outer}: the folder {@code archive} was found in after it was unpacked itself, removed once this one is out. */
    static void step(MainActivity a, File archive, String password, File outer, int depth, Runnable onImported, AgvnUnpackFlow.After after) {
        AgvnUnpackDialog.Done done = (result, used) -> IO.execute(() -> {
            File dir = outer != null ? replaceOuter(outer, result.dir, AgvnGameImporter.getGamesRoot()) : result.dir;
            List<AgvnImportEntry> games = Collections.emptyList();
            AgvnUnpackedKind kind = null;
            try {
                games = AgvnUnpackFlow.games(a, dir);
                if (games.isEmpty()) kind = AgvnUnpackedKind.withoutGame(dir);
            } catch (RuntimeException e) {
                Log.w(TAG, "unpacked folder not looked at", e);
            }
            List<AgvnImportEntry> found = games;
            AgvnUnpackedKind what = kind;
            a.runOnUiThread(() -> {
                if (a.isFinishing() || a.isDestroyed()) return;
                if (after != null) after.unpacked(found.size());
                if (!found.isEmpty() || what == null || !tell(a, dir, what, used, depth, onImported, after))
                    AgvnUnpackFlow.unpacked(a, dir, found, onImported);
            });
        });
        if (outer == null) AgvnUnpackDialog.start(a, archive, done);
        else AgvnUnpackDialog.startInner(a, archive, password, done);
    }

    /** Says what a folder without a game holds; false when there is nothing to say beyond "no game found". */
    private static boolean tell(MainActivity a, File dir, AgvnUnpackedKind what, String password, int depth, Runnable onImported,
                                AgvnUnpackFlow.After after) {
        String place = AgvnGameImporter.getGamesRoot().getName() + "/" + dir.getName();
        AlertDialog.Builder dialog = new AlertDialog.Builder(a).setNegativeButton(R.string.agvn_close, null);
        switch (what.kind) {
            case ANDROID:
                dialog.setTitle(R.string.agvn_unpacked_apk_title).setMessage(a.getString(R.string.agvn_unpacked_apk, place));
                // Android's own installer, which asks the player; only for the one .apk of the folder
                if (what.file != null) dialog.setPositiveButton(R.string.agvn_unpacked_apk_install, (d, w) -> {
                    try {
                        a.startActivity(AgvnUpdater.installIntent(a, what.file));
                    } catch (RuntimeException e) { // a place the app cannot share from (an SD card), no installer
                        Log.w(TAG, "installer not opened for " + what.file, e);
                    }
                });
                break;
            case INNER_ARCHIVE:
                if (depth >= MAX_INNER) return false;
                dialog.setTitle(R.string.agvn_unpacked_inner_title)
                        .setMessage(a.getString(R.string.agvn_unpacked_inner, what.file.getName()))
                        .setPositiveButton(R.string.agvn_unpack_start, (d, w) -> step(a, what.file, password, dir, depth + 1, onImported, after));
                break;
            case OTHER_OS:
                dialog.setTitle(R.string.agvn_unpacked_other_os_title).setMessage(a.getString(R.string.agvn_unpacked_other_os, what.system, place));
                break;
            default:
                return false;
        }
        dialog.show();
        return true;
    }

    /**
     * The inner archive is out: the folder it lay in ({@code outer}, made by the app a moment ago, directly in the games
     * folder) goes, and the game takes its name when it only got "name (2)" because of it. Returns the game's folder.
     */
    static File replaceOuter(File outer, File game, File gamesRoot) {
        if (!gamesRoot.equals(outer.getParentFile()) || outer.equals(game) || !gamesRoot.equals(game.getParentFile())) return game;
        AgvnUnpackFiles.delete(outer);
        if (!outer.exists() && game.getName().equals(outer.getName() + " (2)") && game.renameTo(outer)) return outer;
        return game;
    }
}
