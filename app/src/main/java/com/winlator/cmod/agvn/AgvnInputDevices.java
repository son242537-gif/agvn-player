/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.Build;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.winlator.cmod.inputcontrols.ExternalController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keyboards, mice and gamepads plugged into the phone, and whose key it is: the game's keyboard or its gamepad.
 *
 * Android also calls many wireless keyboards and keyboard+mouse receivers a gamepad (their report lists gamepad
 * buttons). Winlator gave every key of such a device to the gamepad, which drops letters, Enter and Esc: the game got
 * no keyboard at all. A key goes to the gamepad only when a gamepad would send it: a gamepad button, or any key of a
 * device without letter keys (a real gamepad). The logs say which devices were plugged in and whether their keys and
 * clicks reached the game, once per device and session, so that "the keyboard does nothing" can be told apart.
 */
public final class AgvnInputDevices {
    private static final Set<String> logged = new HashSet<>();

    private AgvnInputDevices() {}

    /** The key goes to the gamepad (Winlator's ExternalController), not to the game's keyboard. */
    public static boolean gamepadKey(KeyEvent event) {
        InputDevice device = event.getDevice();
        return ExternalController.isGameController(device)
                && gamepadKey(KeyEvent.isGamepadButton(event.getKeyCode()), device.getKeyboardType());
    }

    static boolean gamepadKey(boolean gamepadButton, int keyboardType) {
        return gamepadButton || keyboardType != InputDevice.KEYBOARD_TYPE_ALPHABETIC;
    }

    /**
     * A key pressed on a plugged-in keyboard: whether it reached the game (its keyboard or a key set in "Điều khiển"),
     * logged once per device and outcome. Android's own keys (back, volume) and a gamepad's buttons are not.
     */
    public static void key(KeyEvent event, boolean toGame) {
        InputDevice device = event.getDevice();
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.isSystem() || !plugged(device)) return;
        if (device.getKeyboardType() != InputDevice.KEYBOARD_TYPE_ALPHABETIC) return; // a gamepad's button
        String lost = gamepadKey(event) ? "gửi cho tay cầm" : "bàn phím game không có phím này";
        String why = toGame ? "phím tới game"
                : "phím " + KeyEvent.keyCodeToString(event.getKeyCode()) + " không tới game (" + lost + ")";
        once(device, toGame, "Bàn phím \"" + device.getName() + "\": " + why);
    }

    /** A button of a plugged-in mouse: the first click of each session the app got, and if "Tắt chuột" ate it. */
    public static void click(MotionEvent event, boolean captured, boolean mouseOff) {
        if (event.getActionMasked() != MotionEvent.ACTION_BUTTON_PRESS) return;
        InputDevice device = event.getDevice();
        if (!plugged(device)) return;
        once(device, !mouseOff, "Chuột \"" + device.getName() + "\": app nhận cú bấm ("
                + (captured ? "app đang giữ chuột" : "chuột không bị giữ")
                + (mouseOff ? "), nhưng \"Tắt chuột\" đang bật nên game không nhận" : ")"));
    }

    /** At a game's start: forgets what was logged and lists what is plugged in (null when nothing is). */
    public static String sessionStart() {
        synchronized (logged) {
            logged.clear();
        }
        List<String> found = describeAll();
        return found.isEmpty() ? null : "Bàn phím, chuột, tay cầm đang nối: " + String.join(" · ", found);
    }

    /** thiet-bi.txt: the plugged-in keyboards, mice and gamepads as Android reports them. */
    static String line() {
        List<String> found = describeAll();
        return "Bàn phím, chuột, tay cầm ngoài: " + (found.isEmpty() ? "không có" : String.join(" · ", found));
    }

    private static List<String> describeAll() {
        List<String> found = new ArrayList<>();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (plugged(device)) found.add(describe(device));
        }
        return found;
    }

    /** "Rapoo 2.4G (bàn phím chữ, chuột, Android gọi là tay cầm: phím chữ vẫn tới game; nguồn 0x2507)". */
    static String describe(InputDevice device) {
        int sources = device.getSources();
        boolean alpha = device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC;
        boolean pad = ExternalController.isGameController(device);
        return device.getName() + " (" + kinds(alpha, has(sources, InputDevice.SOURCE_MOUSE),
                has(sources, InputDevice.SOURCE_TOUCHPAD), pad) + "; nguồn 0x" + Integer.toHexString(sources) + ")";
    }

    static String kinds(boolean alphabetic, boolean mouse, boolean touchpad, boolean gamepad) {
        List<String> kinds = new ArrayList<>();
        if (alphabetic) kinds.add("bàn phím chữ");
        if (mouse) kinds.add("chuột");
        if (touchpad) kinds.add("bàn di chuột");
        if (gamepad) kinds.add(alphabetic ? "Android gọi là tay cầm: phím chữ vẫn tới game" : "tay cầm");
        return kinds.isEmpty() ? "phím khác" : String.join(", ", kinds);
    }

    /** A keyboard, mouse or gamepad of the player's, not the phone's own screen, buttons or fingerprint reader. */
    private static boolean plugged(InputDevice device) {
        if (device == null || device.isVirtual()) return false;
        boolean pad = ExternalController.isGameController(device);
        boolean wanted = pad || device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC
                || has(device.getSources(), InputDevice.SOURCE_MOUSE);
        return wanted && (pad || Build.VERSION.SDK_INT < 29 || device.isExternal());
    }

    private static boolean has(int sources, int source) {
        return (sources & source) == source;
    }

    private static void once(InputDevice device, boolean reached, String text) {
        String key = device.getDescriptor() + "|" + reached;
        synchronized (logged) {
            if (!logged.add(key)) return;
        }
        AgvnSessionLog.event(text);
    }
}
