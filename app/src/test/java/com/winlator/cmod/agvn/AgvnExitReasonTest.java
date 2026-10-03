/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The session summary says in words why Android ended the app. */
public class AgvnExitReasonTest {
    @Test
    public void killedAppsAreExplained() {
        assertTrue(AgvnExitReason.describe(2).contains("SIGKILL"));
        assertTrue(AgvnExitReason.describe(2).contains("HyperOS"));
        assertEquals("app đang mở", AgvnExitReason.importance(100));
        assertEquals("chạy nền có thông báo giữ app", AgvnExitReason.importance(125));
        assertEquals("app vẫn hiện trên màn hình", AgvnExitReason.importance(200));
        assertEquals("chạy nền, không được giữ", AgvnExitReason.importance(300));
    }
}
