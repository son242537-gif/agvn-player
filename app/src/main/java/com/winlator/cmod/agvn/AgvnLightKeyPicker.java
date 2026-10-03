/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputFilter;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;

import com.winlator.cmod.R;
import com.winlator.cmod.inputcontrols.Binding;

import java.util.function.Consumer;

/**
 * The dialogs of the "Chạy nhẹ" key editor ({@link AgvnLightEditor}), as the Windows games' editor has them: a key
 * tapped on the drawn keyboard ({@link AgvnKeyboardPicker#board}), showing only the keys the runner's game gets;
 * arrows or W A S D for a pad; the text on a key. Android's own dialogs: the Ren'Py and mkxp-z screens have no
 * AppCompat theme.
 */
final class AgvnLightKeyPicker {
    /** A key the player tapped: its binding name and the short text for the key's face. */
    interface Picked {
        void key(String binding, String face);
    }

    static final String[] ARROWS = {"KEY_UP", "KEY_RIGHT", "KEY_DOWN", "KEY_LEFT"};
    static final String[] WASD = {"KEY_W", "KEY_D", "KEY_S", "KEY_A"};
    /** Longest text the player can type, as on Windows (AgvnBindingPicker). */
    private static final int MAX_LABEL = 12;
    private static final int THEME = android.R.style.Theme_DeviceDefault_Dialog_Alert;

    private AgvnLightKeyPicker() {}

    /** {@code rename}: "Đổi tên" beside the keys, or null (a new key). */
    static void key(Activity a, String runner, Runnable rename, Picked picked) {
        AlertDialog[] dialog = new AlertDialog[1];
        ScrollView scroll = new ScrollView(a);
        int pad = Math.round(6 * a.getResources().getDisplayMetrics().density);
        scroll.setPadding(pad, pad, pad, pad);
        scroll.addView(AgvnKeyboardPicker.board(a, b -> AgvnLightActions.sendable(runner, b.name()), v -> {
            Binding b = (Binding) v.getTag();
            if (dialog[0] != null) dialog[0].dismiss();
            picked.key(b.name(), AgvnBindingLabels.faceText(a, b));
        }));
        AlertDialog.Builder builder = new AlertDialog.Builder(a, THEME)
                .setTitle(R.string.agvn_edit_pick_key)
                .setView(scroll)
                .setNegativeButton(R.string.agvn_cancel, null);
        if (rename != null) builder.setNeutralButton(R.string.agvn_edit_rename, (d, w) -> rename.run());
        dialog[0] = builder.create();
        AgvnBindingPicker.show(a, dialog[0]);
        if (dialog[0].getWindow() != null) {
            dialog[0].getWindow().setLayout(Math.round(a.getResources().getDisplayMetrics().widthPixels * 0.96f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /** Arrows or W A S D for a pad, in its order: up, right, down, left. */
    static void directions(Activity a, Consumer<String[]> picked) {
        String[] labels = {a.getString(R.string.agvn_dir_arrows), a.getString(R.string.agvn_dir_wasd)};
        AgvnBindingPicker.show(a, new AlertDialog.Builder(a, THEME)
                .setTitle(R.string.agvn_edit_pick_directions)
                .setItems(labels, (d, which) -> picked.accept((which == 0 ? ARROWS : WASD).clone()))
                .setNegativeButton(R.string.agvn_cancel, null)
                .create());
    }

    /** The text on a key. */
    static void label(Activity a, String current, Consumer<String> saved) {
        EditText input = new EditText(a);
        input.setSingleLine(true);
        input.setText(current); // before the filter, which would cut a longer text
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_LABEL)});
        input.setSelection(input.getText().length());
        AgvnBindingPicker.show(a, new AlertDialog.Builder(a, THEME)
                .setTitle(R.string.agvn_edit_rename_title)
                .setView(input)
                .setPositiveButton(R.string.agvn_edit_save, (d, w) -> saved.accept(input.getText().toString().trim()))
                .setNegativeButton(R.string.agvn_cancel, null)
                .create());
    }

    /** "Mặc định" asks first: the keys added or changed go. */
    static void confirmReset(Activity a, Runnable reset) {
        AgvnBindingPicker.show(a, new AlertDialog.Builder(a, THEME)
                .setMessage(R.string.agvn_light_reset_ask)
                .setPositiveButton(R.string.agvn_light_reset, (d, w) -> reset.run())
                .setNegativeButton(R.string.agvn_cancel, null)
                .create());
    }
}
