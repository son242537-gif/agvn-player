/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.Locale;

/**
 * How a 3D game's camera turns, as the player set it for that game ("Camera 3D" in the game's sidebar,
 * {@link AgvnCameraDialog}), kept in its shortcut ({@link #EXTRA}): how fast a drag, the camera stick and the
 * gyroscope turn it, how much it turns up and down beside sideways, and whether tilting the phone turns it (off
 * unless the player turns it on). Pure Java (JVM-testable).
 */
public final class AgvnCameraSettings {
    /** Shortcut extra: "speed=1.32;vertical=0.8;gyro=1;gyroSpeed=1" (a part left out keeps its default). */
    public static final String EXTRA = "agvnCamera";
    /** A speed slider's steps: 2^((step - MIDDLE) / 5), from 0.25 to 4, 1 at the middle. */
    public static final int STEPS = 20, MIDDLE = 10;
    static final float MIN_SPEED = 0.25f, MAX_SPEED = 4f;
    static final float MIN_VERTICAL = 0.5f, MAX_VERTICAL = 1.5f, VERTICAL_STEP = 0.05f;

    public float speed = 1f, vertical = 1f, gyroSpeed = 1f;
    public boolean gyro;

    /** The settings {@code extra} holds; the defaults for anything missing or unreadable. */
    public static AgvnCameraSettings parse(String extra) {
        AgvnCameraSettings s = new AgvnCameraSettings();
        if (extra == null) return s;
        for (String part : extra.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String key = part.substring(0, eq).trim(), value = part.substring(eq + 1).trim();
            switch (key) {
                case "speed": s.speed = clamp(number(value, 1f), MIN_SPEED, MAX_SPEED); break;
                case "vertical": s.vertical = clamp(number(value, 1f), MIN_VERTICAL, MAX_VERTICAL); break;
                case "gyroSpeed": s.gyroSpeed = clamp(number(value, 1f), MIN_SPEED, MAX_SPEED); break;
                case "gyro": s.gyro = value.equals("1"); break;
                default: break;
            }
        }
        return s;
    }

    /** What {@link #parse} reads back; "" for the defaults, which the shortcut then does not keep. */
    public String format() {
        StringBuilder sb = new StringBuilder();
        if (speed != 1f) part(sb, "speed", decimal(speed));
        if (vertical != 1f) part(sb, "vertical", decimal(vertical));
        if (gyro) part(sb, "gyro", "1");
        if (gyroSpeed != 1f) part(sb, "gyroSpeed", decimal(gyroSpeed));
        return sb.toString();
    }

    /** For the session's events: what the player changed, or null for the defaults. */
    String describe() {
        if (format().isEmpty()) return null;
        return String.format(Locale.ROOT, "Camera 3D do người chơi chỉnh: độ nhạy ×%.2f, lên xuống ×%.2f, con quay %s",
                speed, vertical, gyro ? String.format(Locale.ROOT, "bật ×%.2f", gyroSpeed) : "tắt");
    }

    /** The speed at a slider's {@code step} (0..{@link #STEPS}). */
    public static float speedAt(int step) {
        int s = Math.max(0, Math.min(STEPS, step));
        return s == MIDDLE ? 1f : (float) Math.pow(2, (s - MIDDLE) / 5.0);
    }

    /** The slider step nearest to {@code speed}. */
    public static int stepOf(float speed) {
        double step = MIDDLE + 5 * Math.log(clamp(speed, MIN_SPEED, MAX_SPEED)) / Math.log(2);
        return (int) Math.max(0, Math.min(STEPS, Math.round(step)));
    }

    /** The up-and-down share at a slider's {@code step} (0..{@link #STEPS}): 0.5 to 1.5, 1 at the middle. */
    public static float verticalAt(int step) {
        int s = Math.max(0, Math.min(STEPS, step));
        return s == MIDDLE ? 1f : Math.round((MIN_VERTICAL + s * VERTICAL_STEP) * 100) / 100f;
    }

    public static int verticalStepOf(float vertical) {
        float share = clamp(vertical, MIN_VERTICAL, MAX_VERTICAL) - MIN_VERTICAL;
        return Math.max(0, Math.min(STEPS, Math.round(share / VERTICAL_STEP)));
    }

    private static void part(StringBuilder sb, String key, String value) {
        if (sb.length() > 0) sb.append(';');
        sb.append(key).append('=').append(value);
    }

    private static String decimal(float value) {
        String text = String.format(Locale.ROOT, "%.3f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static float number(String value, float fallback) {
        try {
            float f = Float.parseFloat(value);
            return Float.isNaN(f) || Float.isInfinite(f) ? fallback : f;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
