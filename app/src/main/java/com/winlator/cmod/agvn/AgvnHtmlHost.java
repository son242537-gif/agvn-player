/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.view.KeyEvent;
import android.webkit.WebView;

import com.winlator.cmod.R;

import java.io.File;

/**
 * What the "Chạy nhẹ" toolkit asks of an HTML game ({@link AgvnHtmlActivity}): keys and mouse actions as page events
 * ({@link AgvnHtmlKeys}), Esc for the game menu (RPG Maker's menu and cancel key), the keyboard through
 * {@link AgvnHtmlTyping}, and the page's frame rate for the HUD.
 */
final class AgvnHtmlHost implements AgvnLightTools.Host {
    private final AgvnHtmlActivity activity;
    private final WebView web;
    private final AgvnHtmlTyping typing;
    private final File root;
    private volatile int fps = -1;

    AgvnHtmlHost(AgvnHtmlActivity activity, WebView web, File root) {
        this.activity = activity;
        this.web = web;
        this.root = root;
        typing = new AgvnHtmlTyping(activity, web);
    }

    @Override
    public void binding(String binding, boolean down) {
        int action = AgvnLightActions.of(binding);
        if (action > 0) {
            String[] dom = AgvnLightActions.dom(action);
            if (dom != null) web.evaluateJavascript(AgvnHtmlKeys.press(dom, down), null);
        } else if (action == AgvnLightActions.RIGHT_CLICK) {
            web.evaluateJavascript(AgvnHtmlKeys.rightButton(down), null); // held as long as its key is
        } else if (down && action != AgvnLightActions.NONE) {
            web.evaluateJavascript(AgvnHtmlKeys.mouse(action), null);
        }
    }

    /** A game whose plugins give the right mouse button a key gets a key for it ({@link AgvnHtmlRightButton}). */
    @Override
    public void addKeys(AgvnLightLayout layout) {
        if (AgvnHtmlRightButton.wanted(root) && AgvnHtmlRightButton.add(layout, activity.getString(R.string.agvn_light_right_button)))
            android.util.Log.i("AGVN", "HTML game uses the right mouse button (mouse2): a key for it added");
    }

    @Override
    public void openGameMenu() {
        String[] esc = AgvnLightActions.dom(KeyEvent.KEYCODE_ESCAPE);
        web.evaluateJavascript(AgvnHtmlKeys.press(esc, true), null);
        web.postDelayed(() -> web.evaluateJavascript(AgvnHtmlKeys.press(esc, false), null), 120);
    }

    @Override
    public void showKeyboard() {
        typing.show();
    }

    @Override
    public void quit() {
        activity.finish();
    }

    @Override
    public Runnable windows() {
        return AgvnHtmlGame.hasWindowsExe(activity) ? activity::switchToWindows : null;
    }

    /** The last reading; asks the page for the next one (the HUD calls this off the UI thread). */
    @Override
    public int fps() {
        activity.runOnUiThread(() -> {
            if (!activity.isFinishing()) web.evaluateJavascript(AgvnHtmlKeys.FPS, v -> fps = parse(v));
        });
        return fps;
    }

    @Override
    public long gameMb() {
        return -1; // the page runs in WebView's own process, which the app cannot measure
    }

    @Override
    public String runner() {
        return AgvnHtmlGame.RUNNER_HTML;
    }

    @Override
    public int targetFps() {
        return 60; // RPG Maker MV/MZ and Tyrano move 60 times a second
    }

    private static int parse(String value) {
        try {
            return value != null ? Integer.parseInt(value.trim()) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
