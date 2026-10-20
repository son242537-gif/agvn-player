/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import org.libsdl.app.SDLActivity;

/**
 * What the "Chạy nhẹ" toolkit asks of Ren'Py ({@link AgvnRenpyActivity}): keys through SDL; the Windows layout's mouse
 * keys as a mouse at the screen's centre (right click: the game menu, wheel up: rollback, as on a PC); Esc for the
 * game menu; SDL's keyboard.
 */
final class AgvnRenpyHost implements AgvnLightTools.Host {
    private final AgvnRenpyActivity activity;

    AgvnRenpyHost(AgvnRenpyActivity activity) {
        this.activity = activity;
    }

    @Override
    public void binding(String binding, boolean down) {
        int action = AgvnLightActions.of(binding);
        if (action > 0) {
            if (down) SDLActivity.onNativeKeyDown(action);
            else SDLActivity.onNativeKeyUp(action);
        } else if (down && action != AgvnLightActions.NONE) {
            mouse(action);
        }
    }

    private void mouse(int action) {
        View screen = activity.getWindow().getDecorView();
        float x = screen.getWidth() / 2f, y = screen.getHeight() / 2f;
        if (action == AgvnLightActions.SCROLL_UP || action == AgvnLightActions.SCROLL_DOWN) {
            SDLActivity.onNativeMouse(0, MotionEvent.ACTION_SCROLL, 0, action == AgvnLightActions.SCROLL_UP ? 1 : -1, false);
            return;
        }
        int button = action == AgvnLightActions.RIGHT_CLICK ? MotionEvent.BUTTON_SECONDARY
                : action == AgvnLightActions.MIDDLE_CLICK ? MotionEvent.BUTTON_TERTIARY : MotionEvent.BUTTON_PRIMARY;
        SDLActivity.onNativeMouse(button, MotionEvent.ACTION_DOWN, x, y, false);
        screen.postDelayed(() -> SDLActivity.onNativeMouse(0, MotionEvent.ACTION_UP, x, y, false), 60);
    }

    @Override
    public void openGameMenu() {
        SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_ESCAPE); // Ren'Py's game menu key
        SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_ESCAPE);
    }

    @Override
    public void showKeyboard() {
        SDLActivity.showTextInput(0, 0, 1, 1);
    }

    @Override
    public void quit() {
        activity.askRenpyToQuit();
    }

    @Override
    public Runnable windows() {
        return AgvnHtmlGame.hasWindowsExe(activity) ? activity::switchToWindows : null;
    }

    @Override
    public int fps() {
        return -1;
    }

    @Override
    public String runner() {
        return AgvnHtmlGame.RUNNER_RENPY;
    }

    @Override
    public int targetFps() {
        return -1; // Ren'Py draws only when something on screen moves
    }

    @Override
    public long gameMb() {
        return AgvnMemoryProbe.processMb();
    }
}
