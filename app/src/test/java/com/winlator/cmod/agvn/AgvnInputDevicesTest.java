/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.InputDevice;

import org.junit.Test;

/** Keyboards Android also calls a gamepad keep their keys; the logs say what is plugged in. */
public class AgvnInputDevicesTest {
    @Test
    public void aFullKeyboardKeepsItsTypingKeys() {
        // A wireless keyboard Android also calls a gamepad: letters, arrows, Enter, Esc go to the game's keyboard
        assertFalse(AgvnInputDevices.gamepadKey(false, InputDevice.KEYBOARD_TYPE_ALPHABETIC));
        // its gamepad buttons, if it has any, stay with the gamepad
        assertTrue(AgvnInputDevices.gamepadKey(true, InputDevice.KEYBOARD_TYPE_ALPHABETIC));
    }

    @Test
    public void aRealGamepadKeepsEveryKey() {
        // no letter keys: its d-pad and buttons are the gamepad's, as before
        assertTrue(AgvnInputDevices.gamepadKey(false, InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC));
        assertTrue(AgvnInputDevices.gamepadKey(true, InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC));
        assertTrue(AgvnInputDevices.gamepadKey(false, InputDevice.KEYBOARD_TYPE_NONE));
    }

    @Test
    public void whatEachDeviceIs() {
        assertEquals("bàn phím chữ, chuột, Android gọi là tay cầm: phím chữ vẫn tới game",
                AgvnInputDevices.kinds(true, true, false, true));
        assertEquals("bàn phím chữ", AgvnInputDevices.kinds(true, false, false, false));
        assertEquals("chuột", AgvnInputDevices.kinds(false, true, false, false));
        assertEquals("tay cầm", AgvnInputDevices.kinds(false, false, false, true));
        assertEquals("phím khác", AgvnInputDevices.kinds(false, false, false, false));
    }
}
