/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.view.KeyEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * What an on-screen key of {@link AgvnLightLayout} does in a "Chạy nhẹ" game: its binding name (as in the .icp, the
 * Windows controls' names) as an Android key code, or a mouse action for the bindings a visual novel uses with a
 * mouse (right click for the menu, wheel up for the history). HTML games get keys as DOM events ({@link #dom}).
 * Pure Java (JVM-testable): KeyEvent's key codes are compile-time constants.
 */
final class AgvnLightActions {
    static final int NONE = 0, LEFT_CLICK = -1, RIGHT_CLICK = -2, MIDDLE_CLICK = -3, SCROLL_UP = -4, SCROLL_DOWN = -5;
    private static final Map<String, Integer> KEYS = new HashMap<>();

    static {
        key("UP", KeyEvent.KEYCODE_DPAD_UP);
        key("DOWN", KeyEvent.KEYCODE_DPAD_DOWN);
        key("LEFT", KeyEvent.KEYCODE_DPAD_LEFT);
        key("RIGHT", KeyEvent.KEYCODE_DPAD_RIGHT);
        key("ENTER", KeyEvent.KEYCODE_ENTER);
        key("ESC", KeyEvent.KEYCODE_ESCAPE);
        key("BKSP", KeyEvent.KEYCODE_DEL);
        key("DEL", KeyEvent.KEYCODE_FORWARD_DEL);
        key("TAB", KeyEvent.KEYCODE_TAB);
        key("SPACE", KeyEvent.KEYCODE_SPACE);
        key("CTRL_L", KeyEvent.KEYCODE_CTRL_LEFT);
        key("CTRL_R", KeyEvent.KEYCODE_CTRL_RIGHT);
        key("SHIFT_L", KeyEvent.KEYCODE_SHIFT_LEFT);
        key("SHIFT_R", KeyEvent.KEYCODE_SHIFT_RIGHT);
        key("ALT_L", KeyEvent.KEYCODE_ALT_LEFT);
        key("ALT_R", KeyEvent.KEYCODE_ALT_RIGHT);
        key("INSERT", KeyEvent.KEYCODE_INSERT);
        key("HOME", KeyEvent.KEYCODE_MOVE_HOME);
        key("END", KeyEvent.KEYCODE_MOVE_END);
        key("PG_UP", KeyEvent.KEYCODE_PAGE_UP);
        key("PG_DOWN", KeyEvent.KEYCODE_PAGE_DOWN);
        key("PRTSCN", KeyEvent.KEYCODE_SYSRQ);
        key("CAPS_LOCK", KeyEvent.KEYCODE_CAPS_LOCK);
        key("NUM_LOCK", KeyEvent.KEYCODE_NUM_LOCK);
        key("MINUS", KeyEvent.KEYCODE_MINUS);
        key("GRAVE", KeyEvent.KEYCODE_GRAVE);
        key("COMMA", KeyEvent.KEYCODE_COMMA);
        key("PERIOD", KeyEvent.KEYCODE_PERIOD);
        key("SLASH", KeyEvent.KEYCODE_SLASH);
        key("BACKSLASH", KeyEvent.KEYCODE_BACKSLASH);
        key("SEMICOLON", KeyEvent.KEYCODE_SEMICOLON);
        key("APOSTROPHE", KeyEvent.KEYCODE_APOSTROPHE);
        key("BRACKET_LEFT", KeyEvent.KEYCODE_LEFT_BRACKET);
        key("BRACKET_RIGHT", KeyEvent.KEYCODE_RIGHT_BRACKET);
        key("KP_ADD", KeyEvent.KEYCODE_NUMPAD_ADD);
        for (int i = 0; i < 26; i++) key(String.valueOf((char) ('A' + i)), KeyEvent.KEYCODE_A + i);
        for (int i = 0; i < 10; i++) {
            key(String.valueOf(i), KeyEvent.KEYCODE_0 + i);
            key("KP_" + i, KeyEvent.KEYCODE_NUMPAD_0 + i);
        }
        for (int i = 1; i <= 12; i++) key("F" + i, KeyEvent.KEYCODE_F1 + i - 1);
        KEYS.put("MOUSE_LEFT_BUTTON", LEFT_CLICK);
        KEYS.put("MOUSE_RIGHT_BUTTON", RIGHT_CLICK);
        KEYS.put("MOUSE_MIDDLE_BUTTON", MIDDLE_CLICK);
        KEYS.put("MOUSE_SCROLL_UP", SCROLL_UP);
        KEYS.put("MOUSE_SCROLL_DOWN", SCROLL_DOWN);
    }

    private static void key(String name, int keyCode) {
        KEYS.put("KEY_" + name, keyCode);
    }

    private AgvnLightActions() {}

    /** An Android key code (positive), a mouse action (negative) or {@link #NONE} (gamepad and mouse-move bindings). */
    static int of(String binding) {
        Integer v = binding != null ? KEYS.get(binding) : null;
        return v != null ? v : NONE;
    }

    /**
     * RPG Maker XP/VX/VX Ace in mkxp-z: OK (Z in the Windows layout) presses Enter, RGSS's confirm key in every version
     * (Z is A, not confirm, in XP).
     */
    static int forRgss(String binding) {
        return "KEY_Z".equals(binding) ? KeyEvent.KEYCODE_ENTER : of(binding);
    }

    /** {key, code, keyCode} of the DOM keyboard event for an Android key code, as a browser sends it; null if unknown. */
    static String[] dom(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            char c = (char) ('a' + keyCode - KeyEvent.KEYCODE_A);
            return new String[]{String.valueOf(c), "Key" + Character.toUpperCase(c), String.valueOf((int) Character.toUpperCase(c))};
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            int d = keyCode - KeyEvent.KEYCODE_0;
            return new String[]{String.valueOf(d), "Digit" + d, String.valueOf(48 + d)};
        }
        if (keyCode >= KeyEvent.KEYCODE_F1 && keyCode <= KeyEvent.KEYCODE_F12) {
            int f = keyCode - KeyEvent.KEYCODE_F1 + 1;
            return new String[]{"F" + f, "F" + f, String.valueOf(111 + f)};
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return new String[]{"ArrowUp", "ArrowUp", "38"};
            case KeyEvent.KEYCODE_DPAD_DOWN: return new String[]{"ArrowDown", "ArrowDown", "40"};
            case KeyEvent.KEYCODE_DPAD_LEFT: return new String[]{"ArrowLeft", "ArrowLeft", "37"};
            case KeyEvent.KEYCODE_DPAD_RIGHT: return new String[]{"ArrowRight", "ArrowRight", "39"};
            case KeyEvent.KEYCODE_ENTER: return new String[]{"Enter", "Enter", "13"};
            case KeyEvent.KEYCODE_ESCAPE: return new String[]{"Escape", "Escape", "27"};
            case KeyEvent.KEYCODE_SPACE: return new String[]{" ", "Space", "32"};
            case KeyEvent.KEYCODE_TAB: return new String[]{"Tab", "Tab", "9"};
            case KeyEvent.KEYCODE_DEL: return new String[]{"Backspace", "Backspace", "8"};
            case KeyEvent.KEYCODE_SHIFT_LEFT: return new String[]{"Shift", "ShiftLeft", "16"};
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return new String[]{"Shift", "ShiftRight", "16"};
            case KeyEvent.KEYCODE_CTRL_LEFT: return new String[]{"Control", "ControlLeft", "17"};
            case KeyEvent.KEYCODE_CTRL_RIGHT: return new String[]{"Control", "ControlRight", "17"};
            case KeyEvent.KEYCODE_ALT_LEFT: return new String[]{"Alt", "AltLeft", "18"};
            case KeyEvent.KEYCODE_PAGE_UP: return new String[]{"PageUp", "PageUp", "33"};
            case KeyEvent.KEYCODE_PAGE_DOWN: return new String[]{"PageDown", "PageDown", "34"};
            case KeyEvent.KEYCODE_INSERT: return new String[]{"Insert", "Insert", "45"};
            case KeyEvent.KEYCODE_FORWARD_DEL: return new String[]{"Delete", "Delete", "46"};
            case KeyEvent.KEYCODE_MOVE_HOME: return new String[]{"Home", "Home", "36"};
            case KeyEvent.KEYCODE_MOVE_END: return new String[]{"End", "End", "35"};
            default: return null;
        }
    }
}
