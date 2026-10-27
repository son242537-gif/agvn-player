/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;

/**
 * "Chạy bằng Wine mới (Proton 10)" in "Tự sửa lỗi", for an Electron or NW.js game that froze on Proton 9. An Electron
 * 18.3 game (Chromium 100, Cocos Creator 2.4, Steam through greenworks) stayed on its first page on Proton 9 at every
 * try, while the app's Proton 10.0-4 ran it at 30 FPS (POCO F8 Pro) and GameHub's Proton 11 ran it too (27/10/2026).
 * The game moves to Proton 10's container at its next start ({@link AgvnGameMove}). When Proton 10 is not unpacked yet
 * (about a minute, 900 MB), the next start from the library unpacks it first and shows how far ({@link #prepare}). A
 * game that fails on Proton 10 goes back ("wine-old") and is not offered it again.
 */
final class AgvnWine10Fix {
    static final String ID = "wine-new";
    /** "1": the game moves to Proton 10 at its next start from the library, once Proton 10 is unpacked. */
    static final String EXTRA_WANTED = "agvnWine10Wanted";

    private AgvnWine10Fix() {}

    /** The button for {@code s}, or null: a Chromium game (Electron, NW.js) on Proton 9 that did not come back from 10. */
    static AgvnFixes.Fix make(Activity a, Shortcut s) {
        if (AgvnWine10.runs(s.container) || "1".equals(s.getExtra(AgvnGameMove.EXTRA_NOT_TEN))) return null;
        if (!GameExeResolver.chromiumShell(AgvnEngineLogs.gameDir(s))) return null;
        return new AgvnFixes.Fix(ID, a.getString(R.string.agvn_fix_wine_new), null);
    }

    /** The move when Proton 10's container is there, else the mark {@link #prepare} acts on; the caller saves. */
    static void apply(Context ctx, Shortcut s) {
        Container ten = AgvnWine10.find(new ContainerManager(ctx).getContainers(), true);
        if (ten != null) s.putExtra(AgvnGameMove.EXTRA_TO, String.valueOf(ten.id));
        else s.putExtra(EXTRA_WANTED, "1");
    }

    /** Before a library start (PreLaunchCheck): Proton 10 unpacked for a game that wants it, then {@code next}. */
    static void prepare(Activity a, Shortcut s, Runnable next) {
        if (!"1".equals(s.getExtra(EXTRA_WANTED))) {
            next.run();
            return;
        }
        Log.i("AGVN", "Proton 10 unpacked before " + s.name + " starts");
        AlertDialog dialog = new AlertDialog.Builder(a)
                .setMessage(a.getString(R.string.agvn_wine10_installing, 0))
                .setCancelable(false)
                .show();
        Context app = a.getApplicationContext();
        new Thread(() -> {
            Container ten = AgvnWine10.container(app, percent -> a.runOnUiThread(() -> {
                if (dialog.isShowing()) dialog.setMessage(a.getString(R.string.agvn_wine10_installing, percent));
            }));
            a.runOnUiThread(() -> {
                if (dialog.isShowing()) dialog.dismiss();
                s.putExtra(EXTRA_WANTED, null);
                if (ten != null) s.putExtra(AgvnGameMove.EXTRA_TO, String.valueOf(ten.id));
                else Toast.makeText(a, R.string.agvn_wine10_failed, Toast.LENGTH_LONG).show();
                s.saveData();
                if (!a.isFinishing() && !a.isDestroyed()) next.run(); // on Proton 9 as before when it could not
            });
        }, "AgvnWine10").start();
    }
}
