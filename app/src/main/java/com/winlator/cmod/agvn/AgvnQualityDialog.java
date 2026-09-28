/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.AppUtils;

/**
 * Library action "Chỉnh đồ họa": a 5-step slider from "Mát máy" to "Đẹp hơn" that says in plain words what each step
 * does, marks the step suggested for this phone, and has "Về mức gợi ý" to go back to automatic.
 */
public final class AgvnQualityDialog {
    private AgvnQualityDialog() {}

    public static void show(Activity activity, Shortcut shortcut, Runnable onChanged) {
        float dp = activity.getResources().getDisplayMetrics().density;
        AgvnQuality.Level saved = AgvnQuality.current(shortcut);
        AgvnQuality.Level suggested = AgvnQuality.recommended(activity);
        boolean[] auto = {saved == AgvnQuality.Level.AUTO};

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * dp);
        root.setPadding(pad, (int) (8 * dp), pad, 0);

        TextView name = text(activity, 20, true);
        TextView desc = text(activity, 14, false);
        TextView detail = text(activity, 13, false);
        detail.setAlpha(0.75f);

        LinearLayout ends = new LinearLayout(activity);
        TextView cool = text(activity, 13, false);
        cool.setText(R.string.agvn_quality_cool);
        cool.setTextColor(0xFF6EC6FF);
        TextView pretty = text(activity, 13, false);
        pretty.setText(R.string.agvn_quality_pretty);
        pretty.setTextColor(0xFFFFB74D);
        pretty.setGravity(Gravity.END);
        ends.addView(cool, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ends.addView(pretty, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        SeekBar slider = new SeekBar(activity);
        slider.setMax(4);
        GradientDrawable tick = new GradientDrawable();
        tick.setShape(GradientDrawable.OVAL);
        tick.setColor(0xFFB0B0B0);
        tick.setSize((int) (8 * dp), (int) (8 * dp));
        slider.setTickMark(tick);
        slider.setProgress((auto[0] ? suggested : saved).step());

        TextView hint = text(activity, 13, false);
        hint.setText(activity.getString(R.string.agvn_quality_recommended, name(activity, suggested)));
        hint.setTextColor(0xFFFFD97A);
        TextView note = text(activity, 12, false);
        note.setAlpha(0.7f);

        Runnable refresh = () -> {
            AgvnQuality.Level level = auto[0] ? AgvnQuality.Level.AUTO : AgvnQuality.Level.atStep(slider.getProgress());
            AgvnQuality.Level shown = AgvnQuality.Level.atStep(slider.getProgress());
            name.setText(name(activity, shown) + (shown == suggested ? "  ★" : ""));
            desc.setText(description(activity, shown));
            LaunchPresetResolver.Effective eff = AgvnQuality.effective(activity, shortcut, level);
            String fps = eff.fps > 0 ? eff.fps + " FPS" : activity.getString(R.string.agvn_unlimited);
            String res = eff.resolution != null ? eff.resolution.replace('x', '×') : activity.getString(R.string.agvn_default_value);
            detail.setText(activity.getString(R.string.agvn_quality_detail, res, fps));
            note.setText(auto[0] ? activity.getString(R.string.agvn_quality_auto_note) + "\n" + activity.getString(R.string.agvn_quality_restart_note)
                    : activity.getString(R.string.agvn_quality_restart_note));
        };
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser) auto[0] = false;
                refresh.run();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        root.addView(name);
        root.addView(desc);
        root.addView(detail);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gap.topMargin = (int) (14 * dp);
        root.addView(ends, gap);
        root.addView(slider);
        root.addView(hint);
        root.addView(note);
        refresh.run();

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.agvn_quality_title, shortcut.name))
                .setView(root)
                .setPositiveButton(R.string.agvn_quality_save, (d, w) -> {
                    AgvnQuality.Level level = auto[0] ? AgvnQuality.Level.AUTO : AgvnQuality.Level.atStep(slider.getProgress());
                    AgvnQuality.apply(activity, shortcut, level);
                    AppUtils.showToast(activity, activity.getString(R.string.agvn_quality_applied, name(activity, AgvnQuality.Level.atStep(slider.getProgress()))));
                    if (onChanged != null) onChanged.run();
                })
                .setNeutralButton(R.string.agvn_quality_reset, null)
                .setNegativeButton(R.string.agvn_cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            auto[0] = true;
            slider.setProgress(suggested.step());
            refresh.run();
        }));
        dialog.show();
    }

    private static TextView text(Activity activity, float sp, boolean bold) {
        TextView t = new TextView(activity);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static String name(Activity activity, AgvnQuality.Level level) {
        switch (level) {
            case LOWEST: return activity.getString(R.string.agvn_quality_lowest);
            case LOW: return activity.getString(R.string.agvn_quality_low);
            case HIGH: return activity.getString(R.string.agvn_quality_high);
            case HIGHEST: return activity.getString(R.string.agvn_quality_highest);
            default: return activity.getString(R.string.agvn_quality_medium);
        }
    }

    private static String description(Activity activity, AgvnQuality.Level level) {
        switch (level) {
            case LOWEST: return activity.getString(R.string.agvn_quality_lowest_desc);
            case LOW: return activity.getString(R.string.agvn_quality_low_desc);
            case HIGH: return activity.getString(R.string.agvn_quality_high_desc);
            case HIGHEST: return activity.getString(R.string.agvn_quality_highest_desc);
            default: return activity.getString(R.string.agvn_quality_medium_desc);
        }
    }
}
