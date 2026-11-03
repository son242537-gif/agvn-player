/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * A Ren'Py game whose folder had no lib/ showed nothing for 2.5 min, the player quit, and tom-tat.txt read "Game kết
 * thúc bình thường (thoát từ menu hoặc game tự đóng)", with Wine's exit code 137 unexplained (03/11/2026).
 */
public class AgvnSessionEndTest {
    @Test
    public void theSummarySaysWhoEndedTheGameAndWhetherItShowedAnything() {
        assertEquals("Người chơi thoát sau 153 giây; game chưa hiện hình (chưa vẽ khung nào): không phải lần chạy tốt,"
                + " xem su-kien.txt", AgvnSessionTrack.endText(true, false, -1, 153));
        assertEquals("Người chơi thoát sau 153 giây; game không vẽ khung nào trong 60 giây cuối",
                AgvnSessionTrack.endText(true, true, 60, 153));
        assertEquals("Người chơi thoát game sau 900 giây", AgvnSessionTrack.endText(true, true, 2, 900));
        assertTrue(AgvnSessionTrack.endText(false, false, -1, 4).startsWith("Game tự đóng trước khi hiện hình"));
        assertEquals("Game tự đóng sau 900 giây (người chơi thoát từ menu của game, hoặc game tự tắt)",
                AgvnSessionTrack.endText(false, true, 1, 900));
    }

    @Test
    public void wineKilledByTheAppSaysSo() {
        assertEquals("Wine kết thúc, mã thoát 137 (app tắt game vì người chơi thoát)",
                AgvnSessionTrack.wineEndText(137, true));
        assertEquals("Wine kết thúc, mã thoát 137 (bị tắt từ bên ngoài: Android hoặc app)",
                AgvnSessionTrack.wineEndText(137, false));
        assertEquals("Wine kết thúc, mã thoát 0", AgvnSessionTrack.wineEndText(0, false));
    }
}
