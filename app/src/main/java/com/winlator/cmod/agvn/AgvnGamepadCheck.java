/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * Whether a device Android calls a gamepad has a gamepad's controls: a stick or a d-pad (joystick axes X/Y or HAT), or
 * its face buttons (A, B, X, or the first button of an older DirectInput one). Xiaomi's "uinput-xiaomi" (the phone's
 * own Home, Power, Menu and Back keys, and BTN_C) has the gamepad source for its BTN_C: it went into Wine as a gamepad
 * plugged in all the time, and a game with an "Auto" control mode (Unity Input System, XInput, SDL, Rewired) took
 * gamepad control and locked the cursor at the centre, so no tap reached its buttons (POCO, Adreno 830, 07/11/2026).
 * A gamepad built into a handheld keeps its stick and buttons, so it stays one.
 */
public final class AgvnGamepadCheck {
    private AgvnGamepadCheck() {}

    /** True when {@code device} has a stick, a d-pad or face buttons (ExternalController.isGameController). */
    public static boolean hasControls(InputDevice device) {
        boolean axes = device.getMotionRange(MotionEvent.AXIS_X, InputDevice.SOURCE_JOYSTICK) != null
                || device.getMotionRange(MotionEvent.AXIS_HAT_X, InputDevice.SOURCE_JOYSTICK) != null;
        boolean buttons = false;
        if (!axes) {
            for (boolean has : device.hasKeys(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
                    KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_1)) buttons |= has;
        }
        return controls(axes, buttons);
    }

    static boolean controls(boolean axes, boolean faceButtons) {
        return axes || faceButtons;
    }
}
