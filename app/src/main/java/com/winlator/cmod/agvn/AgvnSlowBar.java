/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.widget.Toast;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * The bar over a slow game, Windows or "Chạy nhẹ": what is busy ("Máy của bạn đang bị..."), "Bạn có thể:" and the
 * problem's fixes. A fix that changes the game's settings then offers "Mở lại game ngay": the game closes (the player
 * saves first) and the library starts it again ({@link AgvnDoctor#requestRelaunch}). "Để vậy, không hỏi lại" stops the
 * bar for this game.
 */
final class AgvnSlowBar {
    static final String QUIET = "slowQuiet";

    private AgvnSlowBar() {}

    /** Any thread. {@code restartGame}: closes the game for a restart now; null when it cannot be closed from here. */
    static void ask(Activity a, Shortcut s, String problem, Map<String, String> params, Runnable restartGame) {
        Properties state = AgvnGoodConfig.load(a, s);
        if ("1".equals(state.getProperty(QUIET))) return;
        AgvnProblemCatalog.Finding f = AgvnDoctor.catalog(a).finding(problem, params);
        if (f != null) a.runOnUiThread(() -> show(a, s, f, state, restartGame));
    }

    private static void show(Activity a, Shortcut s, AgvnProblemCatalog.Finding f, Properties state, Runnable restartGame) {
        if (a.isFinishing() || a.isDestroyed()) return;
        List<AgvnWarningBar.Choice> choices = new ArrayList<>();
        for (AgvnFixes.Fix fix : AgvnFixes.applicable(a, s, f, state)) {
            choices.add(new AgvnWarningBar.Choice(fix.label, () -> {
                if (AgvnFixApply.apply(a, s, fix, state)) offerRestart(a, s, fix.label, restartGame);
            }));
        }
        String detail = f.cause() + (choices.isEmpty() ? "" : "\n" + a.getString(R.string.agvn_doctor_lead));
        choices.add(new AgvnWarningBar.Choice(a.getString(R.string.agvn_doctor_keep), () -> {
            state.setProperty(QUIET, "1");
            AgvnGoodConfig.save(a, s, state);
        }));
        AgvnWarningBar.show(a, f.title(), detail, choices.toArray(new AgvnWarningBar.Choice[0]));
    }

    /** After a fix that needs a new start: now (the game closes and comes back) or the next time. */
    static void offerRestart(Activity a, Shortcut s, String label, Runnable restartGame) {
        if (restartGame == null) {
            Toast.makeText(a, a.getString(R.string.agvn_doctor_saved, label), Toast.LENGTH_LONG).show();
            return;
        }
        AgvnWarningBar.show(a, a.getString(R.string.agvn_doctor_restart_title, label), a.getString(R.string.agvn_doctor_restart_detail),
                new AgvnWarningBar.Choice(R.string.agvn_doctor_restart_now, () -> {
                    AgvnDoctor.requestRelaunch(a, s);
                    restartGame.run();
                }),
                new AgvnWarningBar.Choice(R.string.agvn_doctor_restart_later, null));
    }
}
