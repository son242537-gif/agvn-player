/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;
import com.winlator.cmod.inputcontrols.ControlElement;
import com.winlator.cmod.widget.InputControlsView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * "Kiểu…" in the controls editor: shape (buttons only) and opacity of the selected control, or of every control with
 * "Áp dụng cho tất cả". Changes show live on the game (the dialog does not darken it and sits away from the control);
 * [Xong] saves, [Hủy] puts everything back.
 */
final class AgvnStyleDialog {
    static final ControlElement.Shape[] SHAPES = {ControlElement.Shape.CIRCLE, ControlElement.Shape.SQUARE,
            ControlElement.Shape.RECT, ControlElement.Shape.ROUND_RECT};
    private static final int[] SHAPE_NAMES = {R.string.agvn_style_circle, R.string.agvn_style_square,
            R.string.agvn_style_rect, R.string.agvn_style_round_rect};
    private static final int CHIP_BG = 0xff2a3038, CHIP_SELECTED_BG = 0xff2184ff;

    private AgvnStyleDialog() {}

    /** Opacity slider position 0..9 for 10%..100%. */
    static int opacityStep(float opacity) {
        return Math.max(0, Math.min(9, Math.round(opacity * 10f) - 1));
    }

    static float opacityAt(int step) {
        return (Math.max(0, Math.min(9, step)) + 1) / 10f;
    }

    static void show(Activity activity, InputControlsView view, ControlElement element, Runnable onSaved) {
        float dp = activity.getResources().getDisplayMetrics().density;
        List<ControlElement> all = new ArrayList<>(view.getProfile().getElements());
        List<ControlElement.Shape> oldShapes = new ArrayList<>();
        List<Float> oldOpacities = new ArrayList<>();
        for (ControlElement e : all) {
            oldShapes.add(e.getShape());
            oldOpacities.add(e.getOpacity());
        }
        boolean button = element.getType() == ControlElement.Type.BUTTON;
        ControlElement.Shape[] shape = {element.getShape()};
        CheckBox everyControl = new CheckBox(activity);
        SeekBar opacity = new SeekBar(activity);
        opacity.setMax(9);
        opacity.setProgress(opacityStep(element.getOpacity()));
        TextView opacityValue = new TextView(activity);

        Runnable apply = () -> {
            float value = opacityAt(opacity.getProgress());
            opacityValue.setText(activity.getString(R.string.agvn_style_opacity_value, Math.round(value * 100)));
            for (ControlElement e : everyControl.isChecked() ? all : Collections.singletonList(element)) {
                if (button && e.getType() == ControlElement.Type.BUTTON) e.setShape(shape[0]);
                e.setOpacity(value);
            }
            view.invalidate();
        };

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * dp);
        root.setPadding(pad, (int) (8 * dp), pad, 0);
        if (button) {
            root.addView(label(activity, R.string.agvn_style_shape));
            LinearLayout chips = new LinearLayout(activity);
            List<TextView> chipViews = new ArrayList<>();
            for (int i = 0; i < SHAPES.length; i++) {
                ControlElement.Shape s = SHAPES[i];
                TextView chip = chip(activity, dp, SHAPE_NAMES[i]);
                chip.setOnClickListener(v -> {
                    shape[0] = s;
                    for (int j = 0; j < chipViews.size(); j++) paint(chipViews.get(j), SHAPES[j] == s);
                    apply.run();
                });
                paint(chip, s == shape[0]);
                chipViews.add(chip);
                chips.addView(chip);
            }
            root.addView(chips);
        }
        LinearLayout opacityRow = new LinearLayout(activity);
        opacityRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label(activity, R.string.agvn_style_opacity);
        opacityRow.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        opacityRow.addView(opacityValue);
        root.addView(opacityRow);
        LinearLayout slider = new LinearLayout(activity);
        slider.setGravity(Gravity.CENTER_VERTICAL);
        slider.addView(small(activity, R.string.agvn_style_faint));
        slider.addView(opacity, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        slider.addView(small(activity, R.string.agvn_style_clear));
        root.addView(slider);
        everyControl.setText(R.string.agvn_style_all);
        root.addView(everyControl);
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) { apply.run(); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        everyControl.setOnCheckedChangeListener((b, checked) -> {
            if (!checked) restore(all, oldShapes, oldOpacities, element);
            apply.run();
        });

        // edit mode shows every control at 35% or more; show the real opacity while choosing it
        view.setEditMode(false);
        view.setOverlayEditStyle(false);
        boolean[] saved = {false};
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.agvn_style_title)
                .setView(root)
                .setPositiveButton(R.string.agvn_edit_done, (d, w) -> {
                    saved[0] = true;
                    onSaved.run();
                })
                .setNegativeButton(R.string.agvn_cancel, null)
                .create();
        dialog.setOnDismissListener(d -> {
            if (!saved[0]) restore(all, oldShapes, oldOpacities, null);
            view.setEditMode(true);
            view.setOverlayEditStyle(true);
            view.invalidate();
        });
        apply.run();
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) { // keep the game visible and the dialog away from the control being styled
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            boolean controlOnTop = element.getBoundingBox().centerY() < view.getHeight() / 2;
            window.setGravity((controlOnTop ? Gravity.BOTTOM : Gravity.TOP) | Gravity.CENTER_HORIZONTAL);
        }
    }

    /** Puts back the saved shapes and opacities (all controls, or all but {@code keep}). */
    private static void restore(List<ControlElement> all, List<ControlElement.Shape> shapes, List<Float> opacities, ControlElement keep) {
        for (int i = 0; i < all.size(); i++) {
            ControlElement e = all.get(i);
            if (e == keep) continue;
            e.setShape(shapes.get(i));
            e.setOpacity(opacities.get(i));
        }
    }

    private static TextView label(Activity activity, int res) {
        TextView t = new TextView(activity);
        t.setText(res);
        t.setTextSize(15);
        t.setPadding(0, (int) (10 * activity.getResources().getDisplayMetrics().density), 0, 0);
        return t;
    }

    private static TextView small(Activity activity, int res) {
        TextView t = new TextView(activity);
        t.setText(res);
        t.setTextSize(13);
        t.setAlpha(0.75f);
        return t;
    }

    private static TextView chip(Activity activity, float dp, int res) {
        TextView chip = new TextView(activity);
        chip.setText(res);
        chip.setTextColor(Color.WHITE);
        chip.setTextSize(14);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding((int) (10 * dp), 0, (int) (10 * dp), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(8 * dp);
        chip.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (int) (40 * dp));
        lp.setMargins(0, (int) (6 * dp), (int) (6 * dp), 0);
        chip.setLayoutParams(lp);
        return chip;
    }

    private static void paint(TextView chip, boolean selected) {
        ((GradientDrawable) chip.getBackground()).setColor(selected ? CHIP_SELECTED_BG : CHIP_BG);
    }
}
