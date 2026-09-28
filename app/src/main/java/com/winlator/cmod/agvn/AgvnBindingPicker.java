/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.os.Build;
import android.text.InputFilter;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.inputcontrols.Binding;
import com.winlator.cmod.inputcontrols.ControlElement;

import java.util.List;

/**
 * In-game key picker for the selected control: a Vietnamese list of keys for a button (plus renaming its label), or
 * a direction set (arrows, WASD, mouse movement) for a D-pad or stick. Changes the element only; the caller saves.
 */
final class AgvnBindingPicker {
    /** {@code changed} is false when the player cancelled. */
    interface Result {
        void done(boolean changed);
    }

    /** Longest label the player can type; longer existing labels are shown whole (the filter only limits typing). */
    private static final int MAX_LABEL = 12;

    private AgvnBindingPicker() {}

    static void pick(Activity activity, ControlElement element, Result result) {
        switch (element.getType()) {
            case BUTTON:
                pickKey(activity, element, result);
                break;
            case D_PAD:
            case STICK:
                pickDirections(activity, element, result);
                break;
            default:
                AppUtils.showToast(activity, R.string.agvn_edit_no_keys_for_type);
                result.done(false);
        }
    }

    private static void pickKey(Activity activity, ControlElement element, Result result) {
        List<Binding> bindings = AgvnBindingLabels.buttonBindings();
        String[] labels = new String[bindings.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = AgvnBindingLabels.label(activity, bindings.get(i));
        boolean[] handled = {false};
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.agvn_edit_pick_key)
                .setItems(labels, (d, which) -> {
                    handled[0] = true;
                    setKey(activity, element, bindings.get(which));
                    result.done(true);
                })
                .setNegativeButton(R.string.agvn_cancel, null)
                .setOnDismissListener(d -> {
                    if (!handled[0]) result.done(false);
                });
        if (element.getBindingAt(0) != Binding.NONE) builder.setNeutralButton(R.string.agvn_edit_rename, (d, w) -> {
            handled[0] = true;
            rename(activity, element, result);
        });
        show(activity, builder.create());
    }

    /** One key on slot 0, the other slots cleared, and the face text set to the key's short name. */
    static void setKey(Activity activity, ControlElement element, Binding binding) {
        element.setBindingAt(0, binding);
        for (int i = 1; i < element.getBindingCount(); i++) element.setBindingAt(i, Binding.NONE);
        element.setMouseMoveMode(false); // a mouse-move button would ignore the key
        element.setText(AgvnBindingLabels.faceText(activity, binding));
    }

    private static void rename(Activity activity, ControlElement element, Result result) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setText(element.getText()); // before the filter, which would cut a longer label
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_LABEL)});
        input.setSelection(input.getText().length());
        boolean[] handled = {false};
        show(activity, new AlertDialog.Builder(activity)
                .setTitle(R.string.agvn_edit_rename_title)
                .setView(input)
                .setPositiveButton(R.string.agvn_edit_save, (d, w) -> {
                    handled[0] = true;
                    element.setText(input.getText().toString().trim());
                    result.done(true);
                })
                .setNegativeButton(R.string.agvn_cancel, null)
                .setOnDismissListener(d -> {
                    if (!handled[0]) result.done(false);
                })
                .create());
    }

    private static void pickDirections(Activity activity, ControlElement element, Result result) {
        String[] labels = new String[AgvnBindingLabels.DIRECTION_SETS.length];
        for (int i = 0; i < labels.length; i++) labels[i] = activity.getString(AgvnBindingLabels.DIRECTION_SET_LABELS[i]);
        boolean[] handled = {false};
        show(activity, new AlertDialog.Builder(activity)
                .setTitle(R.string.agvn_edit_pick_directions)
                .setItems(labels, (d, which) -> {
                    handled[0] = true;
                    Binding[] set = AgvnBindingLabels.DIRECTION_SETS[which];
                    for (int i = 0; i < set.length; i++) element.setBindingAt(i, set[i]);
                    result.done(true);
                })
                .setNegativeButton(R.string.agvn_cancel, null)
                .setOnDismissListener(d -> {
                    if (!handled[0]) result.done(false);
                })
                .create());
    }

    /** Shows the dialog without bringing back the status and navigation bars over the game. */
    static void show(Activity activity, AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) {
            dialog.show();
            return;
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        dialog.show();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            View decor = window.getDecorView();
            decor.setSystemUiVisibility(activity.getWindow().getDecorView().getSystemUiVisibility());
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }
}
