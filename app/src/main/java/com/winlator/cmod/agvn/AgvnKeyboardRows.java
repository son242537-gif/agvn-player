/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.inputcontrols.Binding;

/** Layout of the on-screen key picker: PC keyboard rows and the mouse panel. Pure data, JVM-testable. */
final class AgvnKeyboardRows {
    private AgvnKeyboardRows() {}

    /** "BINDING|label|weight"; "|" alone (empty binding) is a gap. Labels are left empty to use the Vietnamese names. */
    static final String[][] ROWS = {
            {"KEY_ESC|Esc|1.2", "|", "KEY_F1|F1", "KEY_F2|F2", "KEY_F3|F3", "KEY_F4|F4", "KEY_F5|F5", "KEY_F6|F6",
                    "KEY_F7|F7", "KEY_F8|F8", "KEY_F9|F9", "KEY_F10|F10", "KEY_F11|F11", "KEY_F12|F12", "|",
                    "KEY_PRTSCN|PrtSc", "KEY_INSERT|Ins", "KEY_DEL|Del"},
            {"KEY_GRAVE|`", "KEY_1|1", "KEY_2|2", "KEY_3|3", "KEY_4|4", "KEY_5|5", "KEY_6|6", "KEY_7|7", "KEY_8|8",
                    "KEY_9|9", "KEY_0|0", "KEY_MINUS|-", "KEY_KP_ADD|+", "KEY_BKSP|Xóa|2", "|", "KEY_HOME|Home",
                    "KEY_PG_UP|PgUp"},
            {"KEY_TAB|Tab|1.5", "KEY_Q|Q", "KEY_W|W", "KEY_E|E", "KEY_R|R", "KEY_T|T", "KEY_Y|Y", "KEY_U|U", "KEY_I|I",
                    "KEY_O|O", "KEY_P|P", "KEY_BRACKET_LEFT|[", "KEY_BRACKET_RIGHT|]", "KEY_BACKSLASH|\\|1.5", "|",
                    "KEY_END|End", "KEY_PG_DOWN|PgDn"},
            {"KEY_CAPS_LOCK|Caps|1.8", "KEY_A|A", "KEY_S|S", "KEY_D|D", "KEY_F|F", "KEY_G|G", "KEY_H|H", "KEY_J|J",
                    "KEY_K|K", "KEY_L|L", "KEY_SEMICOLON|;", "KEY_APOSTROPHE|'", "KEY_ENTER|Enter|2.2", "|", "|",
                    "KEY_NUM_LOCK|Num"},
            {"KEY_SHIFT_L|Shift|2.3", "KEY_Z|Z", "KEY_X|X", "KEY_C|C", "KEY_V|V", "KEY_B|B", "KEY_N|N", "KEY_M|M",
                    "KEY_COMMA|,", "KEY_PERIOD|.", "KEY_SLASH|/", "KEY_SHIFT_R|Shift|2.7", "|", "|", "KEY_UP|↑", "|"},
            {"KEY_CTRL_L|Ctrl|1.5", "KEY_ALT_L|Alt|1.3", "KEY_SPACE|Space|6", "KEY_ALT_R|Alt|1.3", "KEY_CTRL_R|Ctrl|1.5",
                    "|", "KEY_LEFT|←", "KEY_DOWN|↓", "KEY_RIGHT|→"},
    };

    /** Mouse panel, one key per row. Labels come from the Vietnamese strings (empty label). */
    static final String[] MOUSE = {
            "MOUSE_LEFT_BUTTON||1", "MOUSE_RIGHT_BUTTON||1", "MOUSE_MIDDLE_BUTTON||1", "MOUSE_SCROLL_UP||1",
            "MOUSE_SCROLL_DOWN||1",
    };

    /** The Binding for a cell's name, or null for gaps and names this build does not know. */
    static Binding binding(String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            return Binding.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
