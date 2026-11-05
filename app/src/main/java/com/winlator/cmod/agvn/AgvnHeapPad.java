/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

/**
 * An SRPG Studio game starts through agvn-winhandler.exe /heappad (app/src/main/cpp/agvn/heap-pad.c): its zeroed heap
 * blocks get two more zero bytes, as Windows' own compatibility shim HeapPadAllocation pads them.
 * <p>game.exe reads a UTF-16 script into size + 1 zeroed bytes and hands it to JScript, whose end needs two. Under
 * Wine's heap the byte after the block keeps an old block's text, and the game stopped at its start with "スクリプトエラー
 * … Line N: Syntax error" on the line after a script's last, its English original too (POCO, 05/11/2026). The same
 * reading in Wine 9.0 broke 5 of 40 such scripts, none with two zero bytes. No file of the game changes, nor any other
 * game's start. Pure Java.
 */
public final class AgvnHeapPad {
    static final String FLAG = "/heappad";
    private static volatile String note;

    private AgvnHeapPad() {}

    /** {@code starter} with {@link #FLAG} for a game of {@code engine} that needs it, when the starter is AGVN's. */
    public static String starter(String starter, String engine) {
        if (!AgvnRawMouse.EXE.equals(starter) || !GameExeResolver.Engine.SRPG_STUDIO.name().equals(engine)) {
            return starter;
        }
        note = "Game SRPG Studio: mở bằng " + AgvnRawMouse.EXE + " " + FLAG + ", vùng nhớ xin đầy số 0 của game có thêm"
                + " 2 byte 0 (script UTF-16 có đủ ký tự kết thúc; dòng \"heap pad on\" trong nhật ký Wine)";
        return starter + " " + FLAG;
    }

    /** The line for the session's events, once; null when the game starts as before. */
    static String takeNote() {
        String n = note;
        note = null;
        return n;
    }
}
