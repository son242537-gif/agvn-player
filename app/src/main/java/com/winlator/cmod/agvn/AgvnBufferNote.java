/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

/**
 * su-kien.txt's line when Android gives the X server no graphics buffer for a game's picture, cursor or window
 * ({@code Drawable.NoBuffer}): the game now gets X's BadAlloc for that request, where the whole app closed before. The
 * X server kept every buffer a game had freed (upstream Winlator), so The Blood of Yidhra (Unity, Xiaomi 23117RK66C),
 * which made cursors without end, closed the app after 5 to 7 minutes as it made the next one (20/10/2026). At most a
 * line a minute: a phone out of buffers refuses many requests in a row.
 */
public final class AgvnBufferNote {
    private static final String TAG = "AGVN";
    private static final long EVERY_MS = 60_000;
    private static long last; // guarded by the class

    private AgvnBufferNote() {}

    /** X request {@code opcode} found no buffer; {@code why} says its size and how many buffers were held. */
    public static void noBuffer(int opcode, String why) {
        Log.w(TAG, "X request " + (opcode & 0xff) + " got no buffer: " + why);
        synchronized (AgvnBufferNote.class) {
            long now = System.currentTimeMillis();
            if (last != 0 && now - last < EVERY_MS) return;
            last = now;
        }
        AgvnSessionLog.event("Android không cấp bộ đệm đồ hoạ cho yêu cầu X " + (opcode & 0xff) + " (" + why
                + "): game nhận lỗi BadAlloc thay vì app bị đóng");
    }
}
