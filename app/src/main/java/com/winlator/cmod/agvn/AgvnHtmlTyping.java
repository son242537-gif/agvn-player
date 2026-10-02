/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * [⌨] on an HTML game: the Android keyboard, whose letters reach the page as key events, for games that read keys
 * (an RPG Maker MV/MZ name input plugin, Tyrano's key settings). A hidden 1x1 field holds the keyboard; what it gets
 * is compared with what it had, so the keyboard's own corrections become Backspace and new letters. The field takes
 * focus only while the keyboard is up, so a real keyboard or controller always reaches the page. A text field of the
 * page itself gets the keyboard from WebView as usual.
 */
final class AgvnHtmlTyping {
    private final Activity activity;
    private final WebView web;
    private final EditText field;
    private String sent = "";
    /** The keyboard came up for the field; when it goes, the page gets the focus back. */
    private boolean imeSeen;

    AgvnHtmlTyping(Activity activity, WebView web) {
        this.activity = activity;
        this.web = web;
        field = new EditText(activity);
        field.setAlpha(0f);
        setFocusable(false);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                typed(s.toString());
            }
        });
        field.setOnEditorActionListener((v, action, event) -> {
            key(KeyEvent.KEYCODE_ENTER);
            return true;
        });
        field.getViewTreeObserver().addOnGlobalLayoutListener(() -> { // the keyboard came or went
            if (!field.hasFocus()) return;
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(field);
            boolean up = insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
            if (up) imeSeen = true;
            else if (imeSeen) release();
        });
        field.setOnKeyListener((v, keyCode, event) -> { // Backspace on an empty field
            if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN && sent.isEmpty()) key(KeyEvent.KEYCODE_DEL);
            return false;
        });
        activity.addContentView(field, new FrameLayout.LayoutParams(1, 1));
    }

    void show() {
        setFocusable(true);
        field.requestFocus();
        imeSeen = false;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) field.post(() -> imm.showSoftInput(field, 0));
    }

    /** The keyboard is gone: the page takes the focus again (and with it a real keyboard's and controller's keys). */
    private void release() {
        imeSeen = false;
        setFocusable(false);
        web.requestFocus();
    }

    private void setFocusable(boolean on) {
        if (!on) field.clearFocus();
        field.setFocusable(on);
        field.setFocusableInTouchMode(on);
    }

    /** The field now holds {@code now}: Backspace for what went, keys for what came. */
    private void typed(String now) {
        int same = 0;
        while (same < sent.length() && same < now.length() && sent.charAt(same) == now.charAt(same)) same++;
        for (int i = same; i < sent.length(); i++) key(KeyEvent.KEYCODE_DEL);
        for (int i = same; i < now.length(); i++) letter(now.charAt(i));
        sent = now;
    }

    private void letter(char c) {
        if (c == '\n') {
            key(KeyEvent.KEYCODE_ENTER);
            return;
        }
        String[] dom = Character.isLetterOrDigit(c) && c < 128 ? AgvnLightActions.dom(Character.isDigit(c)
                ? KeyEvent.KEYCODE_0 + (c - '0') : KeyEvent.KEYCODE_A + (Character.toLowerCase(c) - 'a')) : null;
        String code = dom != null ? dom[1] : c == ' ' ? "Space" : "";
        int keyCode = dom != null ? Integer.parseInt(dom[2]) : c == ' ' ? 32 : 0;
        web.evaluateJavascript(AgvnHtmlKeys.typed(String.valueOf(c), code, keyCode), null);
    }

    private void key(int androidKey) {
        String[] dom = AgvnLightActions.dom(androidKey);
        if (dom == null) return;
        web.evaluateJavascript(AgvnHtmlKeys.press(dom, true) + AgvnHtmlKeys.press(dom, false), null);
    }
}
