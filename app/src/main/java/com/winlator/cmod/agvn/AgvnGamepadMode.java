/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.WineRegistryEditor;

import java.io.File;

/**
 * What the gamepad switches do. The app hands gamepads to Wine as virtual devices (FakeInputWriter), which winebus
 * shows to games. So:
 * - "no gamepad" means the app writes no virtual device ("Tắt XInput", or both XInput and DInput off);
 * - DInput can be hidden per device (WineUtils.setJoystickRegistryKeys);
 * - XInput can only be hidden by not mapping the pad as an Xbox controller: winebus "Map Controllers" = 0.
 */
public final class AgvnGamepadMode {
    /** WinHandler.FLAG_INPUT_TYPE_XINPUT / FLAG_INPUT_TYPE_DINPUT. */
    static final int XINPUT = 0x04, DINPUT = 0x08;
    static final String WINEBUS_KEY = "System\\CurrentControlSet\\Services\\winebus";
    static final String MAP_CONTROLLERS = "Map Controllers";
    /** Container extra: the "Map Controllers" value last written to system.reg; missing = Wine's default (1). */
    static final String EXTRA_MAPPED = "agvnMapControllers";

    private AgvnGamepadMode() {}

    /** The game's "Nhập độc quyền": its own choice, else the environment's. */
    public static boolean exclusive(Container container, Shortcut shortcut) {
        String extra = shortcut != null ? shortcut.getExtra("exclusiveXInput") : "";
        return extra.isEmpty() ? container.isExclusiveXInput() : extra.equals("1");
    }

    /** True when the game gets no gamepad: "Tắt XInput" in the app or the game, or both kinds off with "Nhập độc quyền". */
    public static boolean noGamepad(boolean appOff, boolean gameOff, boolean exclusive, int inputType) {
        return appOff || gameOff || (exclusive && (inputType & (XINPUT | DINPUT)) == 0);
    }

    /** False only for "Nhập độc quyền" with DInput alone: then games must not see the pad as an Xbox (XInput) controller. */
    public static boolean mapToXInput(boolean exclusive, int inputType) {
        return !exclusive || (inputType & XINPUT) != 0;
    }

    /** Writes "Map Controllers" before Wine starts, only when it changes. Returns true when the container extra changed. */
    public static boolean applyMapping(Container container, boolean map) {
        String want = map ? "1" : "0";
        if (want.equals(container.getExtra(EXTRA_MAPPED, "1"))) return false;
        try (WineRegistryEditor editor = new WineRegistryEditor(new File(container.getRootDir(), ".wine/system.reg"))) {
            editor.setCreateKeyIfNotExist(false);
            editor.setDwordValue(WINEBUS_KEY, MAP_CONTROLLERS, map ? 1 : 0);
        }
        container.putExtra(EXTRA_MAPPED, want);
        return true;
    }
}
