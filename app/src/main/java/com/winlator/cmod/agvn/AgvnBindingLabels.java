/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;

import com.winlator.cmod.R;
import com.winlator.cmod.inputcontrols.Binding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Keys offered by the in-game binding picker, most used first: mouse buttons and wheel, Enter/Esc/Space/Tab/Shift/
 * Ctrl/Alt/Backspace, arrows, WASD, A-Z, 0-9, F1-F12, then every other keyboard key. Gamepad bindings and mouse
 * movement are left out (they do nothing useful on a single button). Pure Java except the two Context methods.
 */
public final class AgvnBindingLabels {
    private static final List<Binding> COMMON = Arrays.asList(
            Binding.MOUSE_LEFT_BUTTON, Binding.MOUSE_RIGHT_BUTTON, Binding.MOUSE_MIDDLE_BUTTON,
            Binding.MOUSE_SCROLL_UP, Binding.MOUSE_SCROLL_DOWN,
            Binding.KEY_ENTER, Binding.KEY_ESC, Binding.KEY_SPACE, Binding.KEY_TAB,
            Binding.KEY_SHIFT_L, Binding.KEY_CTRL_L, Binding.KEY_ALT_L, Binding.KEY_BKSP,
            Binding.KEY_UP, Binding.KEY_DOWN, Binding.KEY_LEFT, Binding.KEY_RIGHT,
            Binding.KEY_W, Binding.KEY_A, Binding.KEY_S, Binding.KEY_D);

    /** Direction sets for a D-pad or stick, each in up, right, down, left order (the order ControlElement uses). */
    static final Binding[][] DIRECTION_SETS = {
            {Binding.KEY_UP, Binding.KEY_RIGHT, Binding.KEY_DOWN, Binding.KEY_LEFT},
            {Binding.KEY_W, Binding.KEY_D, Binding.KEY_S, Binding.KEY_A},
            {Binding.MOUSE_MOVE_UP, Binding.MOUSE_MOVE_RIGHT, Binding.MOUSE_MOVE_DOWN, Binding.MOUSE_MOVE_LEFT}};
    static final int[] DIRECTION_SET_LABELS = {R.string.agvn_dir_arrows, R.string.agvn_dir_wasd, R.string.agvn_dir_mouse};

    private static final List<Binding> BUTTON_BINDINGS = Collections.unmodifiableList(buildButtonBindings());

    private AgvnBindingLabels() {}

    /** Bindings a button can get, in picker order, without duplicates. */
    public static List<Binding> buttonBindings() {
        return BUTTON_BINDINGS;
    }

    private static List<Binding> buildButtonBindings() {
        Set<Binding> ordered = new LinkedHashSet<>(COMMON);
        for (char c = 'A'; c <= 'Z'; c++) ordered.add(Binding.valueOf("KEY_" + c));
        for (int i = 0; i <= 9; i++) ordered.add(Binding.valueOf("KEY_" + i));
        for (int i = 1; i <= 12; i++) ordered.add(Binding.valueOf("KEY_F" + i));
        for (Binding b : Binding.values()) if (offered(b)) ordered.add(b);
        return new ArrayList<>(ordered);
    }

    /** Keyboard keys and mouse buttons/wheel; not NONE, gamepad or mouse movement. */
    static boolean offered(Binding b) {
        return b != Binding.NONE && !b.isGamepad() && !b.isMouseMove() && (b.isKeyboard() || b.isMouse());
    }

    /** Vietnamese name resource for keys that need one, else 0 (the key name is used as it is). */
    static int nameRes(Binding b) {
        switch (b) {
            case MOUSE_LEFT_BUTTON: return R.string.agvn_key_mouse_left;
            case MOUSE_RIGHT_BUTTON: return R.string.agvn_key_mouse_right;
            case MOUSE_MIDDLE_BUTTON: return R.string.agvn_key_mouse_middle;
            case MOUSE_SCROLL_UP: return R.string.agvn_key_scroll_up;
            case MOUSE_SCROLL_DOWN: return R.string.agvn_key_scroll_down;
            case KEY_UP: return R.string.agvn_key_up;
            case KEY_DOWN: return R.string.agvn_key_down;
            case KEY_LEFT: return R.string.agvn_key_left;
            case KEY_RIGHT: return R.string.agvn_key_right;
            case KEY_BKSP: return R.string.agvn_key_backspace;
            case KEY_DEL: return R.string.agvn_key_delete;
            default: return 0;
        }
    }

    /** Plain key name as printed on a keyboard: "Shift", "Ctrl R", "PgUp", "F5", "NP5", "A", "[" ... */
    static String keyName(Binding b) {
        switch (b) {
            case KEY_ENTER: return "Enter";
            case KEY_ESC: return "Esc";
            case KEY_SPACE: return "Space";
            case KEY_TAB: return "Tab";
            case KEY_SHIFT_L: return "Shift";
            case KEY_SHIFT_R: return "Shift R";
            case KEY_CTRL_L: return "Ctrl";
            case KEY_CTRL_R: return "Ctrl R";
            case KEY_ALT_L: return "Alt";
            case KEY_ALT_R: return "Alt R";
            case KEY_INSERT: return "Ins";
            case KEY_DEL: return "Del";
            case KEY_HOME: return "Home";
            case KEY_END: return "End";
            case KEY_PG_UP: return "PgUp";
            case KEY_PG_DOWN: return "PgDn";
            case KEY_PRTSCN: return "PrtSc";
            case KEY_CAPS_LOCK: return "Caps";
            case KEY_NUM_LOCK: return "NumLk";
            default:
                String name = b.toString();
                return name.startsWith("NUMPAD ") ? "NP" + name.substring(7) : name;
        }
    }

    /** Short text printed on the button face; arrows become ↑ ↓ ← →, mouse keys a 6-letter name (face text is small). */
    static String faceText(Context ctx, Binding b) {
        switch (b) {
            case KEY_UP: return "↑";
            case KEY_DOWN: return "↓";
            case KEY_LEFT: return "←";
            case KEY_RIGHT: return "→";
            default:
                return shortRes(b) != 0 ? ctx.getString(shortRes(b)) : keyName(b);
        }
    }

    /** Button-face name resource (at most 6 letters) for keys whose Vietnamese name is too long, else 0. */
    static int shortRes(Binding b) {
        switch (b) {
            case MOUSE_LEFT_BUTTON: return R.string.agvn_key_mouse_left_short;
            case MOUSE_RIGHT_BUTTON: return R.string.agvn_key_mouse_right_short;
            case MOUSE_MIDDLE_BUTTON: return R.string.agvn_key_mouse_middle_short;
            case MOUSE_SCROLL_UP: return R.string.agvn_key_scroll_up_short;
            case MOUSE_SCROLL_DOWN: return R.string.agvn_key_scroll_down_short;
            case KEY_BKSP: return R.string.agvn_key_backspace_short;
            default: return 0;
        }
    }

    /** Line shown in the picker. */
    static String label(Context ctx, Binding b) {
        int res = nameRes(b);
        return res != 0 ? ctx.getString(res) : keyName(b);
    }
}
