/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The on-screen keys of a "Chạy nhẹ" game ({@link AgvnLightLayout}), placed and sized as the Windows controls of the
 * same game type ({@link AgvnLightGeometry}). Several fingers work at once (walk while dashing); a touch that starts
 * outside every key goes to the game. While editing ({@link AgvnLightEditor}), a tap selects a key and a drag moves it.
 */
@SuppressLint("ViewConstructor")
final class AgvnLightKeys extends View {
    /** Gets each binding (KEY_..., MOUSE_...) pressed and released once, however many fingers hold it. */
    interface Sink {
        void press(String binding, boolean down);
    }

    private static final String[] NOTHING = new String[0];

    private final AgvnLightLayout layout;
    private final Sink sink;
    private final AgvnLightPainter painter = new AgvnLightPainter();
    /** Pointer id -> {element index, bindings it presses}. */
    private final SparseArray<Object[]> held = new SparseArray<>();
    private final Map<String, Integer> count = new HashMap<>();
    private boolean editing, changed;
    private int selected = -1, dragId = -1;
    private float grabDx, grabDy;

    AgvnLightKeys(Context context, AgvnLightLayout layout, Sink sink) {
        super(context);
        this.layout = layout;
        this.sink = sink;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        painter.draw(canvas, layout, getWidth(), getHeight(), this::pressedBy, editing ? selected : -1);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (editing) return editTouch(ev);
        int action = ev.getActionMasked(), index = ev.getActionIndex(), id = ev.getPointerId(index);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                float x = ev.getX(index), y = ev.getY(index);
                int i = AgvnLightGeometry.hit(layout.elements, x, y, getWidth(), getHeight());
                if (i < 0) return action != MotionEvent.ACTION_DOWN; // a first touch elsewhere is the game's
                hold(id, i, bindingsAt(i, x, y));
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int p = 0; p < ev.getPointerCount(); p++) {
                    Object[] h = held.get(ev.getPointerId(p));
                    int i = h != null ? (int) h[0] : -1;
                    if (i >= 0 && layout.elements.get(i).pad) hold(ev.getPointerId(p), i, bindingsAt(i, ev.getX(p), ev.getY(p)));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                hold(id, -1, NOTHING);
                held.remove(id);
                break;
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                break;
            default:
                break;
        }
        return true;
    }

    /** What a touch at (x, y) on key {@code i} presses: the pad's direction (none at its centre), or the button's keys. */
    private String[] bindingsAt(int i, float x, float y) {
        AgvnLightLayout.Element e = layout.elements.get(i);
        if (e.pad) {
            int part = AgvnLightGeometry.padPart(e, x, y, getWidth(), getHeight());
            return part >= 0 && part < e.bindings.length && !e.bindings[part].equals("NONE") ? new String[]{e.bindings[part]} : NOTHING;
        }
        List<String> out = new ArrayList<>();
        for (String b : e.bindings) if (!b.equals("NONE")) out.add(b);
        return out.toArray(NOTHING);
    }

    /** Pointer {@code id} now holds {@code bindings} of key {@code element} (-1: nothing): releases the old, presses the new. */
    private void hold(int id, int element, String[] bindings) {
        Object[] old = held.get(id);
        String[] before = old != null ? (String[]) old[1] : NOTHING;
        for (String b : before) {
            int n = count.getOrDefault(b, 0) - 1;
            count.put(b, Math.max(0, n));
            if (n == 0) sink.press(b, false);
        }
        if (element >= 0) held.put(id, new Object[]{element, bindings});
        for (String b : bindings) {
            int n = count.getOrDefault(b, 0) + 1;
            count.put(b, n);
            if (n == 1) sink.press(b, true);
        }
        invalidate();
    }

    /** Lets go of every key, as when the game goes to the background with a finger still down. */
    void releaseAll() {
        for (int i = held.size() - 1; i >= 0; i--) hold(held.keyAt(i), -1, NOTHING);
        held.clear();
    }

    private String[] pressedBy(int element) {
        for (int i = 0; i < held.size(); i++) {
            Object[] h = held.valueAt(i);
            if ((int) h[0] == element && ((String[]) h[1]).length > 0) return (String[]) h[1];
        }
        return NOTHING;
    }

    /** Edit mode: keys only change (the game gets nothing); returns to play with {@code false}. */
    void setEditing(boolean on) {
        releaseAll();
        editing = on;
        changed = false;
        selected = -1;
        dragId = -1;
        invalidate();
    }

    /** True when an edit changed a key ({@link AgvnLightEditor}) or moved one. */
    boolean changed() {
        return changed;
    }

    /** The editor changed the keys: redraws. */
    void markChanged() {
        changed = true;
        invalidate();
    }

    /** The key being edited, or -1. */
    int selected() {
        return selected;
    }

    /** Selects key {@code i} (-1: none), e.g. one just added, so the tools act on it. */
    void select(int i) {
        selected = i >= 0 && i < layout.elements.size() ? i : -1;
        invalidate();
    }

    private boolean editTouch(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            selected = AgvnLightGeometry.hit(layout.elements, ev.getX(), ev.getY(), getWidth(), getHeight());
            dragId = selected >= 0 ? ev.getPointerId(0) : -1;
            if (selected >= 0) {
                AgvnLightLayout.Element e = layout.elements.get(selected);
                grabDx = ev.getX() - e.x * getWidth();
                grabDy = ev.getY() - e.y * getHeight();
            }
            invalidate();
        } else if (action == MotionEvent.ACTION_MOVE && dragId >= 0) {
            int p = ev.findPointerIndex(dragId);
            if (p < 0) return true;
            float[] at = AgvnLightGeometry.moved(ev.getX(p), ev.getY(p), grabDx, grabDy, getWidth(), getHeight());
            AgvnLightLayout.Element e = layout.elements.get(selected);
            e.x = at[0];
            e.y = at[1];
            changed = true;
            invalidate();
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            dragId = -1;
        }
        return true;
    }
}
