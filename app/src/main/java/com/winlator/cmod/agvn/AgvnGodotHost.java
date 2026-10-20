/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.view.KeyEvent;

import org.godotengine.godot.Godot;

/**
 * What the "Chạy nhẹ" toolkit ({@link AgvnLightTools}) asks of a Godot game ({@link AgvnGodotActivity}): keys and mouse
 * buttons as a PC sends them ({@link AgvnGodotInput}), Esc for the game's menu, Godot's own on-screen keyboard.
 */
final class AgvnGodotHost implements AgvnLightTools.Host {
    private final AgvnGodotActivity activity;

    AgvnGodotHost(AgvnGodotActivity activity) {
        this.activity = activity;
    }

    @Override
    public void binding(String binding, boolean down) {
        AgvnGodotInput.send(activity.getGodot(), AgvnLightActions.of(binding), down, activity.touchX, activity.touchY);
    }

    @Override
    public void openGameMenu() {
        AgvnGodotInput.tap(activity.getGodot(), KeyEvent.KEYCODE_ESCAPE); // ui_cancel: most PC games' menu, or "back"
    }

    @Override
    public void showKeyboard() {
        Godot godot = activity.getGodot();
        if (godot != null) godot.getIo().showKeyboard("", 0, -1, 0, 0); // typed letters reach the game as keys
    }

    @Override
    public void quit() {
        activity.finish(); // Godot stops its engine first (onDestroy), then the process ends
    }

    @Override
    public Runnable windows() {
        return AgvnHtmlGame.hasWindowsExe(activity) ? activity::switchToWindows : null;
    }

    @Override
    public int fps() {
        return -1; // Godot draws on its own thread, whose frames Android does not count for the app
    }

    @Override
    public long gameMb() {
        return AgvnMemoryProbe.processMb();
    }

    @Override
    public String runner() {
        return AgvnHtmlGame.RUNNER_GODOT;
    }

    @Override
    public int targetFps() {
        return -1;
    }
}
