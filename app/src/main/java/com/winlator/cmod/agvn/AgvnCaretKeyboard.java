/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

import com.winlator.cmod.XServerDisplayActivity;

/**
 * The Android keyboard for a text field of a Windows game: up when the field takes typing, down when it no longer
 * does, if it came up so. A field of Windows (a name box, a launcher's) shows a text caret; a field the game draws
 * itself (Ren'Py's and SDL's, Unity's, Unreal's, Godot's) has none, but the game turns its text input on for it: an
 * input context for its window, after it had none (agvn-winhandler.exe 5). Both are told by
 * {@link AgvnMouseLook#TEXT} and {@link AgvnMouseLook#TYPING}. A player who put the keyboard down keeps it down until
 * the next field. A game whose own field got the keyboard {@link #UNUSED} times and nothing was typed gets no more of
 * it in that session: it turns its text input on for something else. [⌨] opens the keyboard at any time. Either way
 * the keyboard is {@link AgvnWinTyping}'s.
 */
public final class AgvnCaretKeyboard {
    /** The game's own fields that got the keyboard with nothing typed, after which they get it no more. */
    static final int UNUSED = 2;
    private final XServerDisplayActivity activity;
    /** The keyboard came up for a field, so it goes down with it; {@link #byGame}: for a field the game draws. */
    private boolean raised, byGame;
    private boolean toldCaret, toldGame;
    private int unused;

    private AgvnCaretKeyboard(XServerDisplayActivity activity) {
        this.activity = activity;
    }

    /** Listens to the game's text fields (after the X server exists). */
    public static void attach(XServerDisplayActivity activity) {
        AgvnCaretKeyboard keyboard = new AgvnCaretKeyboard(activity);
        activity.getXServer().agvnMouseLook.setOnText(text -> activity.runOnUiThread(() -> keyboard.changed(text)));
    }

    /** Whether a field ({@code state}: RC_AGVN_STATE's TEXT and TYPING) brings the keyboard up. Pure Java. */
    static boolean raises(int state, boolean up, int unused) {
        if (state == 0 || up) return false;
        return (state & AgvnMouseLook.TEXT) != 0 || unused < UNUSED;
    }

    private void changed(int state) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        boolean up = imeShown(activity.getWindow().getDecorView());
        if (raises(state, up, unused)) {
            AgvnWinTyping.of(activity).show();
            raised = true;
            byGame = (state & AgvnMouseLook.TEXT) == 0;
            if (!byGame && !toldCaret) {
                toldCaret = true;
                AgvnSessionLog.event("Game có ô nhập chữ (con trỏ nhập chữ của Windows): mở bàn phím");
            } else if (byGame && !toldGame) {
                toldGame = true;
                AgvnSessionLog.event("Game bật nhập chữ cho ô nó tự vẽ (Ren'Py, SDL, Unity, Godot): mở bàn phím");
            }
        } else if (state == 0 && raised) {
            raised = false;
            AgvnWinTyping typing = AgvnWinTyping.of(activity);
            if (byGame && !typing.used() && ++unused == UNUSED) {
                AgvnSessionLog.event("Bàn phím mở " + UNUSED + " lần cho ô game tự vẽ mà không gõ gì: thôi tự mở"
                        + " cho các ô đó lần chơi này ([⌨] vẫn mở được)");
            }
            typing.hide();
        }
    }

    /** Whether the keyboard is up; false when Android cannot say (before 11). */
    static boolean imeShown(View decor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;
        WindowInsets insets = decor.getRootWindowInsets();
        return insets != null && insets.isVisible(WindowInsets.Type.ime());
    }
}
