/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * "Tự sửa lỗi" ({@link AgvnRepair}): from the game's menu in the library ({@code restartGame} null: the library starts
 * the game) and from the AGVN bar over a running game ({@code restartGame} closes it, after "Mở lại game ngay").
 * While a fix is being tried it asks whether the problem is gone; else "Game đang bị gì?", then the problem's plan:
 * what it may be, the fixes in the order they are tried, and the first one. UI thread.
 */
public final class AgvnRepairDialog {
    private AgvnRepairDialog() {}

    public static void pick(Activity a, Shortcut s, Runnable restartGame) {
        if (a == null || s == null || a.isFinishing() || a.isDestroyed()) return;
        Properties state = AgvnGoodConfig.load(a, s);
        if (!AgvnRepair.trying(state).isEmpty()) status(a, s, state, restartGame);
        else symptoms(a, s, restartGame);
    }

    /** The problems a player can report, in the catalog's order. */
    static List<AgvnProblemCatalog.Problem> reported(Activity a) {
        List<AgvnProblemCatalog.Problem> out = new ArrayList<>();
        for (AgvnProblemCatalog.Problem p : AgvnDoctor.catalog(a).all()) {
            if (p.when != null && p.when.contains("reported")) out.add(p);
        }
        return out;
    }

    private static void symptoms(Activity a, Shortcut s, Runnable restartGame) {
        List<AgvnProblemCatalog.Problem> problems = reported(a);
        String[] titles = new String[problems.size()];
        for (int i = 0; i < titles.length; i++) titles[i] = problems.get(i).title;
        new AlertDialog.Builder(a)
                .setTitle(R.string.agvn_repair_pick)
                .setItems(titles, (d, which) -> {
                    String id = problems.get(which).id;
                    AgvnProblemCatalog.Finding f = AgvnDoctor.catalog(a).finding(id, AgvnGodotGame.params(s));
                    if (f != null) plan(a, s, f, restartGame);
                })
                .setNegativeButton(R.string.agvn_doctor_later, null)
                .show();
    }

    /** What the problem may be, the fixes left in the order they are tried, and a button for the first. */
    static void plan(Activity a, Shortcut s, AgvnProblemCatalog.Finding f, Runnable restartGame) {
        Properties state = AgvnGoodConfig.load(a, s);
        List<AgvnFixes.Fix> left = AgvnRepair.left(a, s, f, state);
        LinearLayout box = box(a);
        box.addView(text(a, a.getString(R.string.agvn_doctor_game, s.name), 13, false));
        box.addView(text(a, f.cause(), 15, false));
        int intro = left.isEmpty() ? R.string.agvn_repair_no_fix : R.string.agvn_repair_plan;
        StringBuilder steps = new StringBuilder(a.getString(intro));
        for (int i = 0; i < left.size(); i++) steps.append('\n').append(i + 1).append(". ").append(left.get(i).label);
        box.addView(text(a, steps.toString(), 14, !left.isEmpty()));
        AlertDialog dialog = dialog(a, f.title(), box);
        if (!left.isEmpty()) {
            AgvnFixes.Fix first = left.get(0);
            box.addView(button(a, a.getString(R.string.agvn_repair_start, first.label), dialog, () -> {
                if (AgvnRepair.start(a, s, f, first)) restart(a, s, first, restartGame);
            }));
        }
        for (AgvnFixes.Fix other : AgvnFixes.applicable(a, s, f, Collections.emptySet(), state)) {
            if (other.changesGame()) continue; // "Gửi nhật ký", Android's settings: right away, no new start
            box.addView(button(a, other.label, dialog, () -> AgvnFixApply.apply(a, s, other, state)));
        }
        dialog.show();
    }

    /** A fix on trial: gone? Asked once the game has run with it, else the game starts ({@link AgvnRepair#ran}). */
    private static void status(Activity a, Shortcut s, Properties state, Runnable restartGame) {
        AgvnProblemCatalog.Finding f =
                AgvnDoctor.catalog(a).finding(AgvnRepair.symptom(state), AgvnGodotGame.params(s));
        boolean ran = AgvnRepair.ran(state, s.getExtra("lastRunAt"));
        if (ran && f != null && AgvnRepairCheck.offAtLibrary(a, s, state)) { // not on the game: the fixes left again
            plan(a, s, f, restartGame);
            return;
        }
        String title = f != null ? f.title() : a.getString(R.string.agvn_repair_title);
        LinearLayout box = box(a);
        box.addView(text(a, a.getString(R.string.agvn_doctor_game, s.name), 13, false));
        box.addView(text(a, a.getString(ran ? R.string.agvn_repair_status : R.string.agvn_repair_not_run,
                AgvnRepair.label(state)), 15, false));
        AlertDialog dialog = dialog(a, title, box);
        if (ran) {
            box.addView(button(a, a.getString(R.string.agvn_repair_gone), dialog, () -> gone(a, s)));
            box.addView(button(a, a.getString(R.string.agvn_repair_still), dialog, () -> still(a, s, restartGame)));
        } else {
            int play = restartGame != null ? R.string.agvn_doctor_restart_now : R.string.agvn_repair_play;
            box.addView(button(a, a.getString(play), dialog, () -> startNow(a, s, restartGame)));
        }
        box.addView(button(a, a.getString(R.string.agvn_repair_other), dialog, () -> {
            AgvnRepair.abandon(a, s); // the fix on trial goes, the settings are as before
            symptoms(a, s, restartGame);
        }));
        dialog.show();
    }

    static void gone(Activity a, Shortcut s) {
        String label = AgvnRepair.label(AgvnGoodConfig.load(a, s));
        AgvnRepair.fixed(a, s);
        Toast.makeText(a, a.getString(R.string.agvn_repair_kept, label), Toast.LENGTH_LONG).show();
    }

    /** Still there: the next fix, with a new start; or, none left, the settings as before and "Gửi nhật ký". */
    static void still(Activity a, Shortcut s, Runnable restartGame) {
        AgvnFixes.Fix next = AgvnRepair.next(a, s);
        if (next != null) {
            restart(a, s, next, restartGame);
            return;
        }
        AgvnFixes.Fix logs = new AgvnFixes.Fix("send-logs", a.getString(R.string.agvn_fix_send_logs), null);
        if (restartGame != null) {
            AgvnWarningBar.show(a, a.getString(R.string.agvn_repair_done_title), a.getString(R.string.agvn_repair_none),
                    new AgvnWarningBar.Choice(logs.label, () -> AgvnLogShare.share(a, s)),
                    new AgvnWarningBar.Choice(R.string.agvn_movie_ok, null));
            return;
        }
        new AlertDialog.Builder(a)
                .setTitle(R.string.agvn_repair_done_title)
                .setMessage(R.string.agvn_repair_none)
                .setPositiveButton(logs.label, (d, w) -> AgvnLogShare.share(a, s))
                .setNegativeButton(R.string.agvn_movie_ok, null)
                .show();
    }

    /** The game starts with the fix on trial: at once over the game ("Mở lại game ngay"), else from the library. */
    private static void startNow(Activity a, Shortcut s, Runnable restartGame) {
        if (restartGame == null) {
            AgvnRelaunch.start(a, s);
            return;
        }
        AgvnDoctor.requestRelaunch(a, s);
        restartGame.run();
    }

    /** The game starts again with {@code fix}: now from the library, or after "Mở lại game ngay" over the game. */
    private static void restart(Activity a, Shortcut s, AgvnFixes.Fix fix, Runnable restartGame) {
        if (restartGame != null) {
            AgvnSlowBar.offerRestart(a, s, fix.label, restartGame);
            return;
        }
        Toast.makeText(a, a.getString(R.string.agvn_repair_trying, fix.label), Toast.LENGTH_LONG).show();
        AgvnRelaunch.start(a, s);
    }

    private static AlertDialog dialog(Activity a, String title, LinearLayout box) {
        ScrollView scroll = new ScrollView(a);
        scroll.addView(box);
        return new AlertDialog.Builder(a).setTitle(title).setView(scroll)
                .setNegativeButton(R.string.agvn_doctor_later, null).create();
    }

    private static LinearLayout box(Activity a) {
        int pad = dp(a, 20);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, dp(a, 8), pad, 0);
        return box;
    }

    /** A button that closes {@code dialog}, then does {@code action}. */
    private static Button button(Activity a, String label, AlertDialog dialog, Runnable action) {
        Button b = new Button(a);
        b.setAllCaps(false);
        b.setText(label);
        b.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
        return b;
    }

    private static TextView text(Activity a, String s, int sp, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(a, 6), 0, dp(a, 6));
        return t;
    }

    private static int dp(Activity a, int value) {
        return Math.round(value * a.getResources().getDisplayMetrics().density);
    }
}
