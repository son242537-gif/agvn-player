/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.Locale;

/**
 * The exit code agvn-winhandler.exe 3 reports once the game and every process it started have ended (it then ends,
 * and Wine with it, so the game's end is seen and "Tự sửa lỗi" can ask). A crash code is the doctor's evidence too: a
 * .NET game that crashed in GDI+ ended with 0xC0000005 and no line in Wine's log, so "Tự sửa lỗi" read it as one that
 * closed by itself (07/11/2026). Pure Java (JVM-testable).
 */
public final class AgvnGameEnd {
    /** STATUS_CONTROL_C_EXIT: the game was told to end (Ctrl+C, a console closed), not a crash. */
    static final int CONTROL_C = 0xC000013A;
    private static volatile int lastCode;
    private static volatile boolean ended;

    private AgvnGameEnd() {}

    /** The game ended with {@code code} (WinHandler): in su-kien.txt, and kept for the doctor. */
    public static void ended(int code) {
        lastCode = code;
        ended = true;
        AgvnSessionLog.event("Game và mọi tiến trình của game đã tắt, mã thoát " + code(code));
    }

    /** A new game starts (AgvnSessionTrack.start). */
    static void reset() {
        ended = false;
        lastCode = 0;
    }

    /** True once agvn-winhandler.exe told the game's exit code this session. */
    static boolean known() {
        return ended;
    }

    /** The crash code the game ended with in this session, or 0 when it did not end with one. */
    static int crashCode() {
        return ended && crash(lastCode) ? lastCode : 0;
    }

    /**
     * True for a Windows error code (0xC…: an exception the game did not handle) other than {@link #CONTROL_C}. Not
     * 0xFFFFFFFF, a program's own exit(-1): mscoree's "Could not load Mono" ends so.
     */
    static boolean crash(int code) {
        return code >>> 28 == 0xC && code != CONTROL_C;
    }

    /** "0", "1", or "0xC0000005 (lỗi truy cập bộ nhớ)" for a Windows crash code; a code of the high bit in hex. */
    public static String code(int code) {
        if (code >= 0) return String.valueOf(code);
        String hex = String.format(Locale.ROOT, "0x%08X", code);
        switch (code) {
            case 0xC0000005: return hex + " (lỗi truy cập bộ nhớ)";
            case 0xC0000409: return hex + " (chương trình tự dừng vì lỗi nặng)";
            case 0xC000001D: return hex + " (lệnh CPU không chạy được)";
            case 0xC00000FD: return hex + " (tràn ngăn xếp)";
            default: return crash(code) ? hex + " (game văng)" : hex;
        }
    }
}
