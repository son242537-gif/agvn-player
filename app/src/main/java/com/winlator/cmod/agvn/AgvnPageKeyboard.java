/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.winlator.cmod.core.FileUtils;

/**
 * window.AgvnPageKeyboard of a page game: the Android keyboard when a text field of the page takes the focus, also one
 * a script focused, which WebView brings no keyboard up for (assets/agvn/html-keyboard.js), and away when no field has
 * it. The keyboard of [⌨] ({@link AgvnHtmlTyping}, its own field holds the focus then) is never put away.
 */
final class AgvnPageKeyboard {
    static final String NAME = "AgvnPageKeyboard", SCRIPT = "agvn/html-keyboard.js";
    private final Activity activity;
    private final WebView web;
    private boolean told;

    AgvnPageKeyboard(Activity activity, WebView web) {
        this.activity = activity;
        this.web = web;
    }

    /** html-keyboard.js, to run on each page once it has loaded; "" when it cannot be read. */
    static String script(Context ctx) {
        String js = FileUtils.readString(ctx, SCRIPT);
        return js != null ? js : "";
    }

    @JavascriptInterface
    public void field(boolean focused) {
        web.post(() -> changed(focused));
    }

    private void changed(boolean focused) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        if (focused) {
            if (!web.hasFocus()) web.requestFocus();
            imm.showSoftInput(web, 0);
            if (!told) {
                told = true;
                android.util.Log.i("AGVN", "page text field focused: Android keyboard up");
            }
        } else if (activity.getCurrentFocus() == web) {
            imm.hideSoftInputFromWindow(web.getWindowToken(), 0); // not [⌨]'s: its own field has the focus then
        }
    }
}
