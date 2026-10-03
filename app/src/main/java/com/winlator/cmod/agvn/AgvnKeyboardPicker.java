/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;
import com.winlator.cmod.inputcontrols.Binding;
import com.winlator.cmod.inputcontrols.ControlElement;

import java.util.function.Predicate;

/**
 * Picks a button's key on a drawn PC keyboard plus a mouse panel (tap the key itself, no scrolling list), with a
 * "Giữ (bật/tắt)" switch that makes the button latch, e.g. to hold the left mouse button while dragging.
 */
final class AgvnKeyboardPicker {
    private AgvnKeyboardPicker() {}

    static void pick(Activity activity, ControlElement element, AgvnBindingPicker.Result result) {
        float dp = activity.getResources().getDisplayMetrics().density;
        boolean[] handled = {false};
        AlertDialog[] dialog = new AlertDialog[1];

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (6 * dp);
        root.setPadding(pad, pad, pad, pad);

        CheckBox hold = new CheckBox(activity);
        hold.setText(R.string.agvn_kb_hold);
        hold.setChecked(element.isToggleSwitch());

        View.OnClickListener onKey = v -> {
            Binding binding = (Binding) v.getTag();
            handled[0] = true;
            AgvnBindingPicker.setKey(activity, element, binding);
            element.setToggleSwitch(hold.isChecked());
            if (dialog[0] != null) dialog[0].dismiss();
            result.done(true);
        };

        root.addView(board(activity, b -> true, onKey));
        root.addView(hold);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(root);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.agvn_edit_pick_key)
                .setView(scroll)
                .setNegativeButton(R.string.agvn_cancel, null)
                .setOnDismissListener(d -> {
                    if (!handled[0]) result.done(false);
                });
        if (element.getBindingAt(0) != Binding.NONE) builder.setNeutralButton(R.string.agvn_edit_rename, (d, w) -> {
            handled[0] = true;
            element.setToggleSwitch(hold.isChecked());
            AgvnBindingPicker.rename(activity, element, result);
        });
        dialog[0] = builder.create();
        AgvnBindingPicker.show(activity, dialog[0]);
        if (dialog[0].getWindow() != null) {
            dialog[0].getWindow().setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.96f),
                    LinearLayout.LayoutParams.WRAP_CONTENT);
        }
    }

    /**
     * The drawn keyboard and the mouse panel beside it; a tap calls {@code onKey} with the key's Binding as the view's
     * tag. Keys {@code offered} turns down are gaps, so the rows keep their shape; with no mouse key, no mouse panel.
     */
    static LinearLayout board(Activity activity, Predicate<Binding> offered, View.OnClickListener onKey) {
        float dp = activity.getResources().getDisplayMetrics().density;
        LinearLayout board = new LinearLayout(activity);
        board.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout keys = new LinearLayout(activity);
        keys.setOrientation(LinearLayout.VERTICAL);
        LinearLayout mouse = new LinearLayout(activity);
        mouse.setOrientation(LinearLayout.VERTICAL);
        board.addView(keys, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 5f));
        for (String[] row : AgvnKeyboardRows.ROWS) {
            LinearLayout line = new LinearLayout(activity);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (String cell : row) addKey(activity, line, cell, offered, onKey, dp);
            keys.addView(line);
        }
        boolean anyMouse = false;
        for (String cell : AgvnKeyboardRows.MOUSE) {
            LinearLayout line = new LinearLayout(activity);
            anyMouse |= addKey(activity, line, cell, offered, onKey, dp);
            mouse.addView(line);
        }
        if (anyMouse) board.addView(mouse, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return board;
    }

    /**
     * Cell format "BINDING_NAME|label|width"; an empty binding name is a gap, and so are unknown bindings and keys not
     * {@code offered}. Returns true when a key was added.
     */
    private static boolean addKey(Activity activity, LinearLayout line, String cell, Predicate<Binding> offered,
                                  View.OnClickListener onKey, float dp) {
        String[] parts = cell.split("\\|", -1);
        float weight = parts.length > 2 ? Float.parseFloat(parts[2]) : 1f;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (34 * dp), weight);
        int m = (int) (1.5f * dp);
        lp.setMargins(m, m, m, m);
        TextView key = new TextView(activity);
        key.setGravity(Gravity.CENTER);
        key.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        key.setSingleLine(true);
        Binding binding = AgvnKeyboardRows.binding(parts[0]);
        if (binding == null || !offered.test(binding)) {
            line.addView(new View(activity), lp);
            return false;
        }
        key.setText(parts.length > 1 && !parts[1].isEmpty() ? parts[1] : AgvnBindingLabels.label(activity, binding));
        key.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(parts[0].startsWith("MOUSE") ? 0xFF2E4A6B : 0xFF3A3F4A);
        bg.setCornerRadius(5 * dp);
        key.setBackground(bg);
        key.setTag(binding);
        key.setOnClickListener(onKey);
        line.addView(key, lp);
        return true;
    }
}
