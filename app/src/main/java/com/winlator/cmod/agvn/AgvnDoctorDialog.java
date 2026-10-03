/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
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

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * At the library, after a game ended badly: "what happened, what you can do", with one button per fix. A fix changes
 * the game's settings and starts it again. Asked once: the problem is forgotten as soon as it is shown.
 */
public final class AgvnDoctorDialog {
    private AgvnDoctorDialog() {}

    public static void showIfPending(Activity a) {
        if (a == null || a.isFinishing() || a.isDestroyed()) return;
        File file = new File(a.getFilesDir(), AgvnDoctor.PENDING);
        if (!file.isFile()) return;
        Properties p = AgvnPropsFile.load(file);
        file.delete();
        int container;
        try {
            container = Integer.parseInt(p.getProperty("container", "-1"));
            long age = System.currentTimeMillis() - Long.parseLong(p.getProperty("time", "0"));
            if (age > AgvnDoctor.KILL_RECENT_MS) return; // a day old: the player has moved on
        } catch (NumberFormatException e) {
            return;
        }
        Shortcut s = AgvnRelaunch.find(a, container, p.getProperty("shortcut", ""));
        Map<String, String> params = new LinkedHashMap<>();
        for (String key : p.stringPropertyNames()) {
            if (key.startsWith(AgvnDoctor.PARAM)) params.put(key.substring(AgvnDoctor.PARAM.length()), p.getProperty(key));
        }
        AgvnProblemCatalog.Finding f = AgvnDoctor.catalog(a).finding(p.getProperty("problem", ""), params);
        if (s != null && f != null) show(a, s, f);
    }

    static void show(Activity a, Shortcut s, AgvnProblemCatalog.Finding f) {
        Properties state = AgvnGoodConfig.load(a, s);
        List<AgvnFixes.Fix> fixes = AgvnFixes.applicable(a, s, f, state);
        int pad = dp(a, 20);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, dp(a, 8), pad, 0);
        TextView game = text(a, a.getString(R.string.agvn_doctor_game, s.name), 13, false);
        game.setAlpha(0.7f);
        box.addView(game);
        box.addView(text(a, f.cause(), 15, false));
        if (!fixes.isEmpty()) box.addView(text(a, a.getString(R.string.agvn_doctor_lead), 15, true));
        ScrollView scroll = new ScrollView(a);
        scroll.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle(f.title())
                .setView(scroll)
                .setNegativeButton(R.string.agvn_doctor_later, null)
                .create();
        boolean restarts = false;
        for (AgvnFixes.Fix fix : fixes) {
            Button b = new Button(a);
            b.setAllCaps(false);
            b.setText(fix.label);
            b.setOnClickListener(v -> {
                dialog.dismiss();
                if (AgvnFixApply.apply(a, s, fix, state)) {
                    Toast.makeText(a, a.getString(R.string.agvn_doctor_fixed, fix.label), Toast.LENGTH_LONG).show();
                    AgvnRelaunch.start(a, s);
                }
            });
            box.addView(b);
            restarts |= fix.changesGame();
        }
        if (restarts) {
            TextView hint = text(a, a.getString(R.string.agvn_doctor_hint), 13, false);
            hint.setAlpha(0.7f);
            box.addView(hint);
        }
        dialog.show();
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
