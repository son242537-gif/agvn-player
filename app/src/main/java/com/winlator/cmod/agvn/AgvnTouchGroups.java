/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.view.MotionEvent;

import java.util.Arrays;

/**
 * Which fingers are on the on-screen keys and which on the game ({@link AgvnTouchSplit}), and what each side is
 * told of a touch: its own fingers only, a DOWN for the first of them and an UP for the last, as if the other side's
 * fingers were not there. Pure Java (JVM-testable): MotionEvent's constants only.
 */
final class AgvnTouchGroups {
    static final int GAME = 0, KEYS = 1;

    /** What one side is told of an event: its fingers (their indices in the event), the action, and its finger. */
    static final class Part {
        final int[] indices;
        /** ACTION_DOWN, ACTION_POINTER_DOWN, ACTION_MOVE, ACTION_POINTER_UP, ACTION_UP or ACTION_CANCEL. */
        final int action;
        /** The index in {@link #indices} of the finger that went down or up; 0 for a move or a cancel. */
        final int actionIndex;

        Part(int[] indices, int action, int actionIndex) {
            this.indices = indices;
            this.action = action;
            this.actionIndex = actionIndex;
        }
    }

    /** Pointer id bits of each side (Android's pointer ids are below 32). */
    private final int[] bits = new int[2];

    /** True for the actions a touch is made of; others (ACTION_OUTSIDE...) go on as they came. */
    static boolean isTouch(int action) {
        switch (action) {
            case MotionEvent.ACTION_DOWN: case MotionEvent.ACTION_POINTER_DOWN: case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_POINTER_UP: case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return false;
        }
    }

    /** Finger {@code id} went down on {@code side}, which it keeps until it lifts. */
    void down(int id, int side) {
        if (id < 0 || id >= 32) return;
        bits[side] |= 1 << id;
        bits[1 - side] &= ~(1 << id);
    }

    /** Finger {@code id} lifted. */
    void up(int id) {
        if (id < 0 || id >= 32) return;
        bits[GAME] &= ~(1 << id);
        bits[KEYS] &= ~(1 << id);
    }

    void clear() {
        bits[GAME] = bits[KEYS] = 0;
    }

    /** How many fingers {@code side} has down. */
    int count(int side) {
        return Integer.bitCount(bits[side]);
    }

    /**
     * What {@code side} is told of an event {@code action} (masked) about finger {@code actionId}, whose fingers are
     * {@code ids} in the event's order: after {@link #down} for a finger going down, before {@link #up} for one
     * lifting. Null: nothing (none of its fingers is in the event, or another side's finger went down or up).
     */
    Part part(int side, int action, int actionId, int[] ids) {
        int mine = bits[side], n = 0, at = -1;
        int[] indices = new int[ids.length];
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] < 0 || ids[i] >= 32 || (mine & (1 << ids[i])) == 0) continue;
            if (ids[i] == actionId) at = n;
            indices[n++] = i;
        }
        if (n == 0) return null;
        indices = Arrays.copyOf(indices, n);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (at < 0) return null;
                return new Part(indices, n == 1 ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_POINTER_DOWN, at);
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (at < 0) return null;
                return new Part(indices, n == 1 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_POINTER_UP, at);
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_CANCEL:
                return new Part(indices, action, 0);
            default:
                return null;
        }
    }
}
