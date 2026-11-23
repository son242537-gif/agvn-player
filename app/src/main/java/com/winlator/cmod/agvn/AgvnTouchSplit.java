/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

/**
 * Shares the fingers of a "Chạy nhẹ" game between its on-screen keys and the game. The keys ({@link AgvnLightKeys})
 * cover the whole screen above the game, and Android gives a new finger to the view that already holds one without
 * asking it: with ▲ held, a finger on the game to turn the view went to the keys, which let it be, and the game never
 * saw it (Dâm Từ Phủ, Godot 4.7.2, POCO F8 Pro, 23/11/2026: no walking while looking round, in every 3D game). Each
 * finger now goes, as it lands, to the keys when it lands on a key, else to the game and the views above the keys
 * (the bar, the menu), and stays there until it lifts; each side gets its fingers only ({@link AgvnTouchGroups}).
 * While the keys are hidden or being edited, touches go on as they came. On the UI thread.
 */
final class AgvnTouchSplit {
    /** How the game's screen handles a touch: the activity's own dispatchTouchEvent. */
    interface Rest {
        boolean dispatch(MotionEvent event);
    }

    private final AgvnLightKeys keys;
    private final AgvnLightLayout layout;
    private final AgvnTouchGroups groups = new AgvnTouchGroups();
    private final long[] downTime = new long[2];
    private final int[] at = new int[2];
    /** This gesture (from its first finger down) shares its fingers. */
    private boolean sharing;

    AgvnTouchSplit(AgvnLightKeys keys, AgvnLightLayout layout) {
        this.keys = keys;
        this.layout = layout;
    }

    /** A touch of the game's screen; {@code share}: the keys show and are not being edited. */
    boolean dispatch(MotionEvent ev, Rest rest, boolean share) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            groups.clear();
            sharing = share;
        }
        if (!sharing || !AgvnTouchGroups.isTouch(action)) return rest.dispatch(ev);
        int index = ev.getActionIndex(), id = ev.getPointerId(index);
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int side = onKey(ev.getX(index), ev.getY(index)) ? AgvnTouchGroups.KEYS : AgvnTouchGroups.GAME;
            if (groups.count(side) == 0) downTime[side] = ev.getEventTime();
            groups.down(id, side);
        }
        int[] ids = new int[ev.getPointerCount()];
        for (int i = 0; i < ids.length; i++) ids[i] = ev.getPointerId(i);
        boolean handled = false;
        AgvnTouchGroups.Part forKeys = groups.part(AgvnTouchGroups.KEYS, action, id, ids);
        AgvnTouchGroups.Part forGame = groups.part(AgvnTouchGroups.GAME, action, id, ids);
        if (forKeys != null) {
            long start = downTime[AgvnTouchGroups.KEYS];
            MotionEvent e = whole(ev, forKeys, start) ? MotionEvent.obtain(ev) : of(ev, forKeys, start);
            keys.getLocationInWindow(at);
            e.offsetLocation(-at[0], -at[1]);
            handled = keys.dispatchTouchEvent(e);
            e.recycle();
        }
        if (forGame != null) {
            boolean whole = whole(ev, forGame, downTime[AgvnTouchGroups.GAME]);
            MotionEvent e = whole ? ev : of(ev, forGame, downTime[AgvnTouchGroups.GAME]);
            handled |= rest.dispatch(e);
            if (!whole) e.recycle();
        }
        if (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP) groups.up(id);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) groups.clear();
        return handled || forKeys != null || forGame != null;
    }

    /** True when {@code part} is the event itself: all its fingers, its action, its start. */
    private static boolean whole(MotionEvent ev, AgvnTouchGroups.Part part, long downTime) {
        return part.indices.length == ev.getPointerCount() && part.action == ev.getActionMasked()
                && downTime == ev.getDownTime();
    }

    /** The event with {@code part}'s fingers only (the readings between two events are left out). */
    private static MotionEvent of(MotionEvent ev, AgvnTouchGroups.Part part, long downTime) {
        int n = part.indices.length;
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[n];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[n];
        for (int i = 0; i < n; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            ev.getPointerProperties(part.indices[i], properties[i]);
            coords[i] = new MotionEvent.PointerCoords();
            ev.getPointerCoords(part.indices[i], coords[i]);
        }
        int action = part.action;
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
            action |= part.actionIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        }
        return MotionEvent.obtain(downTime, ev.getEventTime(), action, n, properties, coords, ev.getMetaState(),
                ev.getButtonState(), ev.getXPrecision(), ev.getYPrecision(), ev.getDeviceId(), ev.getEdgeFlags(),
                ev.getSource(), ev.getFlags());
    }

    /** A finger landing at (x, y) of the window is on a key, with no bar or menu above it there. */
    private boolean onKey(float x, float y) {
        keys.getLocationInWindow(at);
        if (AgvnLightGeometry.hit(layout.elements, x - at[0], y - at[1], keys.getWidth(), keys.getHeight()) < 0) {
            return false;
        }
        ViewParent parent = keys.getParent();
        if (!(parent instanceof ViewGroup)) return true;
        ViewGroup views = (ViewGroup) parent;
        for (int i = views.indexOfChild(keys) + 1; i < views.getChildCount(); i++) {
            if (takes(views.getChildAt(i), x, y)) return false;
        }
        return true;
    }

    /** Whether {@code v}, shown and at (x, y) of the window, or one of its views would take a touch there. */
    private boolean takes(View v, float x, float y) {
        if (v.getVisibility() != View.VISIBLE) return false;
        v.getLocationInWindow(at);
        if (x < at[0] || y < at[1] || x >= at[0] + v.getWidth() || y >= at[1] + v.getHeight()) return false;
        if (v.isClickable() || v.isLongClickable()) return true;
        if (!(v instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) v;
        for (int i = 0; i < group.getChildCount(); i++) if (takes(group.getChildAt(i), x, y)) return true;
        return false;
    }
}
