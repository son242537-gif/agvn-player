/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.view.KeyEvent;
import android.webkit.WebView;

/**
 * What the "Chạy nhẹ" toolkit asks of an HTML game ({@link AgvnHtmlActivity}): keys and mouse actions as page events
 * ({@link AgvnHtmlKeys}), Esc for the game menu (RPG Maker's menu and cancel key), the keyboard through
 * {@link AgvnHtmlTyping}, and the page's frame rate for the HUD.
 */
final class AgvnHtmlHost implements AgvnLightTools.Host {
    private final AgvnHtmlActivity activity;
    private final WebView web;
    private final AgvnHtmlTyping typing;
    private volatile int fps = -1;

    AgvnHtmlHost(AgvnHtmlActivity activity, WebView web) {
        this.activity = activity;
        this.web = web;
        typing = new AgvnHtmlTyping(activity, web);
    }

    @Override
    public void binding(String binding, boolean down) {
        int action = AgvnLightActions.of(binding);
        if (action > 0) {
            String[] dom = AgvnLightActions.dom(action);
            if (dom != null) web.evaluateJavascript(AgvnHtmlKeys.press(dom, down), null);
        } else if (down && action != AgvnLightActions.NONE) {
            web.evaluateJavascript(AgvnHtmlKeys.mouse(action), null);
        }
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

    private static int parse(String value) {
        try {
            return value != null ? Integer.parseInt(value.trim()) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
