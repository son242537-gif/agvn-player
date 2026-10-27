/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;

import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.core.AppUtils;

/**
 * The Android keyboard for a text field of a Windows game: up when a text caret shows in the game's window (a name
 * box, a launcher's field: agvn-winhandler.exe 2 reports it, {@link AgvnMouseLook#TEXT}), down when the caret goes, if
 * it came up so. A player who put it down keeps it down until the next field. A field the game draws itself (Unity's
 * and Unreal's) has no caret of Windows: [⌨] opens the keyboard there, as before.
 */
public final class AgvnCaretKeyboard {
    private final XServerDisplayActivity activity;
    /** The keyboard came up for a caret, so it goes down with it. */
    private boolean raised;
    private boolean told;

    private AgvnCaretKeyboard(XServerDisplayActivity activity) {
        this.activity = activity;
    }

    /** Listens to the game's caret (after the X server exists). */
    public static void attach(XServerDisplayActivity activity) {
        AgvnCaretKeyboard keyboard = new AgvnCaretKeyboard(activity);
        activity.getXServer().agvnMouseLook.setOnText(text -> activity.runOnUiThread(() -> keyboard.changed(text != 0)));
    }

    private void changed(boolean caret) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        View decor = activity.getWindow().getDecorView();
        boolean up = imeShown(decor);
        if (caret && !up) {
            AppUtils.showKeyboard(activity);
            raised = true;
            if (!told) {
                told = true;
                AgvnSessionLog.event("Game có ô nhập chữ (con trỏ nhập chữ của Windows): mở bàn phím Android");
            }
        } else if (!caret && raised) {
            raised = false;
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (up && imm != null) imm.hideSoftInputFromWindow(decor.getWindowToken(), 0);
        }
    }

    /** Whether the keyboard is up; false when Android cannot say (before 11). */
    static boolean imeShown(View decor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;
        WindowInsets insets = decor.getRootWindowInsets();
        return insets != null && insets.isVisible(WindowInsets.Type.ime());
    }
}
