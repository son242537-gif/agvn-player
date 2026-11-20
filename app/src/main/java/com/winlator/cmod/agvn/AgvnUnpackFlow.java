/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.MainActivity;
import com.winlator.cmod.R;
import com.winlator.cmod.container.ContainerManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What follows an unpacked archive on "Thêm game": its game goes to the usual preview, as if the folder had been found. */
public final class AgvnUnpackFlow {
    private AgvnUnpackFlow() {}

    /**
     * The games of a folder just unpacked (slow: off the main thread). A folder with an exe no rule recognises counts
     * when nothing else is found: the player unpacked it as a game.
     */
    public static List<AgvnImportEntry> games(Context ctx, File dir) {
        AgvnGameScanner.Found found = AgvnGameScanner.find(
                Collections.singletonList(new AgvnGameScanner.Root(dir, AgvnGameRoots.DEPTH, true)));
        AgvnLibraryIndex library = new AgvnLibraryIndex(new ContainerManager(ctx).loadShortcuts());
        AgvnProfileCatalog catalog = AgvnProfileCatalog.get(ctx);
        List<AgvnImportEntry> rows = new ArrayList<>();
        for (File game : found.games.isEmpty() ? found.unsure : found.games) rows.addAll(AgvnImportEntry.build(ctx, game, library, catalog, null));
        return rows;
    }

    /** One game: its settings preview. Several: they are in the list, the player taps one. None: said, with the folder. */
    public static void unpacked(MainActivity a, File dir, List<AgvnImportEntry> found, Runnable onImported) {
        if (a.isFinishing() || a.isDestroyed()) return;
        String place = AgvnGameImporter.getGamesRoot().getName() + "/" + dir.getName();
        if (found.size() == 1) {
            AgvnImportEntry entry = found.get(0);
            Toast.makeText(a, a.getString(R.string.agvn_unpack_done, place), Toast.LENGTH_SHORT).show();
            AgvnImportDialog.preview(a, entry.dir, entry.variant, DeviceTierManager.current(a), entry.existing, onImported);
        } else if (found.isEmpty()) {
            new AlertDialog.Builder(a)
                    .setTitle(R.string.agvn_unpack_no_game_title)
                    .setMessage(a.getString(R.string.agvn_unpack_no_game, place))
                    .setPositiveButton(R.string.agvn_close, null)
                    .show();
        } else {
            Toast.makeText(a, a.getString(R.string.agvn_unpack_done_many, place), Toast.LENGTH_LONG).show();
        }
    }

    /** A scanned folder holds archives and no game: offers to unpack ({@code archives}: their names, one per archive). */
    public static void offer(MainActivity a, List<String> archives, Runnable onAccept) {
        if (a.isFinishing() || a.isDestroyed()) return;
        String names = archives.size() <= 3 ? String.join(", ", archives)
                : String.join(", ", archives.subList(0, 3)) + "…";
        new AlertDialog.Builder(a)
                .setTitle(R.string.agvn_add_folder_nothing)
                .setMessage(a.getString(R.string.agvn_unpack_offer, names))
                .setPositiveButton(R.string.agvn_unpack_start, (d, w) -> onAccept.run())
                .setNegativeButton(R.string.agvn_cancel, null)
                .show();
    }
}
