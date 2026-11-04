/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.widget.InputControlsView;
import com.winlator.cmod.xserver.XKeycode;
import com.winlator.cmod.xserver.XServer;

/**
 * The Android keyboard for a Windows game: [⌨] of the ⌨ ✎ 👁 bar, "Bàn phím" of the sidebar, and a text caret of the
 * game ({@link AgvnCaretKeyboard}). A hidden 1x1 field holds it, as {@link AgvnHtmlTyping} does for HTML games, and
 * what the field gets reaches the game through the X server ({@link AgvnWinKeys}), Vietnamese letters included.
 * <p>The keyboard used to come up with no field ("toggleSoftInput"), and Gboard put it away at the next change of
 * focus: the bar tucking itself away 3 s later, or a tap on the game. Body Pay OMG (Unity, a name field of its own,
 * POCO F8 Pro, 05/11/2026) showed it four times, for 2 to 6 s, and no letter reached the name.
 * <p>While the keyboard is up, the field takes back the focus a tap on the game took, so the player can tap the game's
 * field and type. Back, or the keyboard's own key, puts it away; then the game's views get the focus again.
 */
public final class AgvnWinTyping {
    /** A keyboard gone within this of the field losing the focus went because of it: it comes back. */
    private static final long REFOCUS_MS = 600;
    private static AgvnWinTyping current;

    private final XServerDisplayActivity activity;
    private final EditText field;
    private final AgvnWinKeys.Keys keys = new AgvnWinKeys.Keys() {
        @Override
        public void key(XKeycode key) {
            XServer x = activity.getXServer();
            if (x == null) return;
            x.injectKeyPress(key);
            x.injectKeyRelease(key);
        }

        @Override
        public void character(byte keycode, int keysym) {
            XServer x = activity.getXServer();
            if (x != null) x.agvnTypeKey(keycode, keysym);
        }
    };
    private String sent = "";
    private boolean typing, imeSeen, clearing, toldShown, toldTyped;
    private long focusLostAt = -REFOCUS_MS;

    /** The activity's, made at its first use. */
    public static AgvnWinTyping of(XServerDisplayActivity activity) {
        if (current == null || current.activity != activity) current = new AgvnWinTyping(activity);
        return current;
    }

    private AgvnWinTyping(XServerDisplayActivity activity) {
        this.activity = activity;
        field = new EditText(activity);
        field.setAlpha(0f);
        setFocusable(false);
        // plain text, so a Vietnamese keyboard composes its letters; no suggestions, so nothing is corrected later
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!clearing) typed(s);
            }
        });
        field.setOnEditorActionListener((v, action, event) -> {
            keys.key(XKeycode.KEY_ENTER);
            return true;
        });
        field.setOnFocusChangeListener((v, focused) -> {
            if (focused || !typing) return;
            focusLostAt = SystemClock.uptimeMillis();
            field.post(() -> { // a tap on the game took the focus: the field takes it back
                if (typing && gameFocused()) focusField();
            });
        });
        field.getViewTreeObserver().addOnGlobalLayoutListener(() -> { // the keyboard came or went
            if (!typing) return;
            boolean up = imeUp();
            if (up) imeSeen = true;
            else if (imeSeen && SystemClock.uptimeMillis() - focusLostAt < REFOCUS_MS && gameFocused()) focusField();
            else if (imeSeen) release();
        });
        activity.addContentView(field, new FrameLayout.LayoutParams(1, 1));
    }

    /** The keyboard up for the game. */
    public void show() {
        typing = true;
        imeSeen = false;
        focusField();
        if (!toldShown) {
            toldShown = true;
            AgvnSessionLog.event("Bàn phím Android cho game: ô nhận chữ của app giữ bàn phím, chữ gửi vào game");
        }
    }

    /** The keyboard away, if this put it up. */
    public void hide() {
        if (!typing) return;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
        release();
    }

    /** [⌨]: up, or away when it is up. */
    public void toggle() {
        if (typing && imeUp()) hide();
        else show();
    }

    /** The focus is on the game (its touchpad or on-screen keys) or nowhere: not on the sidebar or a menu. */
    private boolean gameFocused() {
        View now = activity.getCurrentFocus();
        return now == null || now == field || now == activity.getInputControlsView()
                || now == activity.agvnTouchpadView();
    }

    /** Whether the keyboard is up (androidx tells it on Android 9 and 10 too). */
    private boolean imeUp() {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(field);
        return insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
    }

    private void focusField() {
        setFocusable(true);
        field.requestFocus();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) field.post(() -> imm.showSoftInput(field, 0));
    }

    /** The keyboard is gone: the game's views take the focus again, and with it a gamepad's sticks. */
    private void release() {
        typing = false;
        imeSeen = false;
        clear();
        setFocusable(false);
        InputControlsView controls = activity.getInputControlsView();
        if (controls != null && controls.getVisibility() == View.VISIBLE && controls.getProfile() != null) {
            controls.requestFocus();
        }
        else if (activity.agvnTouchpadView() != null) activity.agvnTouchpadView().requestFocus();
    }

    private void setFocusable(boolean on) {
        if (!on) field.clearFocus();
        field.setFocusable(on);
        field.setFocusableInTouchMode(on);
    }

    /** The field now holds {@code text}: the game gets what changed; once nothing is being composed, it starts over. */
    private void typed(Editable text) {
        String now = text.toString();
        AgvnWinKeys.changed(sent, now, keys);
        sent = now;
        if (!now.isEmpty() && !toldTyped) {
            toldTyped = true;
            AgvnSessionLog.event("Bàn phím Android gửi chữ vào game");
        }
        // empty, the field lets the keyboard's Backspace come as a key, which the game gets as it is; after the
        // keyboard's edit, not inside it
        field.post(() -> {
            if (BaseInputConnection.getComposingSpanStart(field.getText()) < 0) clear();
        });
    }

    private void clear() {
        if (field.getText().length() == 0 && sent.isEmpty()) return;
        clearing = true;
        field.getText().clear();
        clearing = false;
        sent = "";
    }
}
