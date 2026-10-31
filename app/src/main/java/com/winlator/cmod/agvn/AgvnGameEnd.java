/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.Locale;

/**
 * The exit code agvn-winhandler.exe 3 reports once the game and every process it started have ended (it then ends,
 * and Wine with it, so the game's end is seen and "Tự sửa lỗi" can ask). Pure Java (JVM-testable).
 */
public final class AgvnGameEnd {
    private AgvnGameEnd() {}

    /** "0", "1", or "0xC0000005 (lỗi truy cập bộ nhớ)" for a Windows crash code. */
    public static String code(int code) {
        if ((code & 0xC0000000) != 0xC0000000) return String.valueOf(code);
        String hex = String.format(Locale.ROOT, "0x%08X", code);
        switch (code) {
            case 0xC0000005: return hex + " (lỗi truy cập bộ nhớ)";
            case 0xC0000409: return hex + " (chương trình tự dừng vì lỗi nặng)";
            case 0xC000001D: return hex + " (lệnh CPU không chạy được)";
            case 0xC00000FD: return hex + " (tràn ngăn xếp)";
            default: return hex + " (game văng)";
        }
    }
}
