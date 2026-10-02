/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.winlator.cmod.agvn.sdl.SDLActivity;

/**
 * On-screen keys for an RPG Maker XP/VX/VX Ace game, placed and named as AGVN's RPG layout for Wine (controls-rpg.icp):
 * a direction pad on the left, OK / Hủy / Chạy / Menu on the right. They send RPG Maker's default keys in mkxp-z:
 * arrows, Enter (C: confirm; not Z, which is A in XP), X and Esc (B: cancel, menu) and Shift (A: dash). Several fingers
 * at once work (walk while dashing). A touch that starts elsewhere goes to the game.
 */
@SuppressLint("ViewConstructor")
final class AgvnRgssKeys extends View {
    /** Right, down, left, up: the order of {@link AgvnRgssPad#direction}. */
    private static final int[] DIRECTIONS = {KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP};
    private static final String[] LABELS = {"OK", "Hủy", "Chạy", "Menu"};
    private static final int[] BUTTON_KEYS = {KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_ESCAPE};
    /** Button centres as fractions of the screen, from controls-rpg.icp. */
    private static final float[][] PLACES = {{0.865f, 0.86f}, {0.925f, 0.73f}, {0.805f, 0.73f}, {0.865f, 0.60f}};

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    /** Pointer id -> the key it holds (0: none). */
    private final SparseIntArray held = new SparseIntArray();
    /** Key -> how many fingers hold it; the game gets one press and one release. */
    private final SparseIntArray count = new SparseIntArray();
    private final float[][] buttons = new float[LABELS.length][3]; // x, y, radius
    private AgvnRgssPad pad;

    AgvnRgssKeys(Context context) {
        super(context);
        line.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float unit = Math.min(w, h);
        pad = new AgvnRgssPad(w * 0.13f, h * 0.74f, unit * 0.17f);
        float b = unit * 0.075f;
        for (int i = 0; i < buttons.length; i++) set(buttons[i], w * PLACES[i][0], h * PLACES[i][1], i == 0 ? b * 1.15f : b);
        line.setStrokeWidth(unit * 0.006f);
        text.setTextSize(b * 0.55f);
    }

    private static void set(float[] button, float x, float y, float r) {
        button[0] = x;
        button[1] = y;
        button[2] = r;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (pad == null) return;
        int dir = -1;
        for (int i = 0; i < 4; i++) if (count.get(DIRECTIONS[i]) > 0) dir = i;
        circle(canvas, pad.x, pad.y, pad.radius, false);
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 2 * i;
            float cx = pad.x + (float) Math.cos(a) * pad.radius * 0.62f, cy = pad.y + (float) Math.sin(a) * pad.radius * 0.62f;
            float s = pad.radius * 0.2f;
            arrow.reset();
            arrow.moveTo(cx + (float) Math.cos(a) * s, cy + (float) Math.sin(a) * s);
            arrow.lineTo(cx + (float) Math.cos(a + 2.2) * s, cy + (float) Math.sin(a + 2.2) * s);
            arrow.lineTo(cx + (float) Math.cos(a - 2.2) * s, cy + (float) Math.sin(a - 2.2) * s);
            arrow.close();
            fill.setColor(Color.argb(i == dir ? 230 : 140, 255, 255, 255));
            canvas.drawPath(arrow, fill);
        }
        for (int i = 0; i < buttons.length; i++) {
            float[] b = buttons[i];
            circle(canvas, b[0], b[1], b[2], count.get(BUTTON_KEYS[i]) > 0);
            text.setColor(Color.argb(220, 255, 255, 255));
            canvas.drawText(LABELS[i], b[0], b[1] - (text.descent() + text.ascent()) / 2, text);
        }
    }

    private void circle(Canvas canvas, float x, float y, float r, boolean pressed) {
        fill.setColor(Color.argb(pressed ? 120 : 55, 20, 20, 24));
        canvas.drawCircle(x, y, r, fill);
        line.setColor(Color.argb(pressed ? 230 : 120, 255, 255, 255));
        canvas.drawCircle(x, y, r, line);
    }

    /** The key under (x, y): a pad direction, a button, 0 in the pad's centre, or -1 outside every key. */
    private int keyAt(float x, float y) {
        if (pad.contains(x, y)) {
            int d = pad.direction(x, y);
            return d >= 0 ? DIRECTIONS[d] : 0;
        }
        for (int i = 0; i < buttons.length; i++) {
            float[] b = buttons[i];
            if (Math.hypot(x - b[0], y - b[1]) <= b[2] * 1.15f) return BUTTON_KEYS[i];
        }
        return -1;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (pad == null) return false;
        int action = e.getActionMasked(), index = e.getActionIndex(), id = e.getPointerId(index);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int key = keyAt(e.getX(index), e.getY(index));
                if (key < 0) return action != MotionEvent.ACTION_DOWN; // a first touch elsewhere is the game's
                held.put(id, 0); // tracked from now on, even from the pad's dead zone
                hold(id, key);
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int pid = e.getPointerId(i), key = held.get(pid, -1);
                    boolean onPad = key == 0 || indexOf(DIRECTIONS, key) >= 0;
                    if (onPad) hold(pid, Math.max(0, padKey(e.getX(i), e.getY(i))));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                hold(id, 0);
                held.delete(id);
                break;
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                break;
            default:
                break;
        }
        return true;
    }

    /** Lets go of every key, as when the game goes to the background with a finger still down. */
    void releaseAll() {
        for (int i = held.size() - 1; i >= 0; i--) hold(held.keyAt(i), 0);
        held.clear();
    }

    /** A finger that started on the pad keeps steering while it slides, even past the pad's edge. */
    private int padKey(float x, float y) {
        int d = pad.direction(x, y);
        return d >= 0 ? DIRECTIONS[d] : 0;
    }

    /** Pointer {@code id} now holds {@code key} (0: nothing): release what it held, press the new key. */
    private void hold(int id, int key) {
        int old = held.get(id, 0);
        if (old == key) return;
        if (old != 0) {
            int n = count.get(old) - 1;
            count.put(old, Math.max(0, n));
            if (n <= 0) SDLActivity.onNativeKeyUp(old);
        }
        held.put(id, key);
        if (key != 0) {
            int n = count.get(key) + 1;
            count.put(key, n);
            if (n == 1) SDLActivity.onNativeKeyDown(key);
        }
        invalidate();
    }

    private static int indexOf(int[] keys, int key) {
        for (int i = 0; i < keys.length; i++) if (keys[i] == key) return i;
        return -1;
    }
}
