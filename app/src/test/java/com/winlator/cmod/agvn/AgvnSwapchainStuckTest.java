/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A Unity 2022.3 game (D3D11, Adreno 830, 0.1.74, 03/11/2026) froze black at 0% CPU right after DXVK made its swap
 * chain again in FIFO: the end of its wine-cuoi.txt.
 */
public class AgvnSwapchainStuckTest {
    static final List<String> REPORT = Arrays.asList(
            "info:  Presenter: Actual swap chain properties:",
            "info:    Present mode: VK_PRESENT_MODE_IMMEDIATE_KHR (dynamic: yes)",
            "info:    Buffer size:  1280x720",
            "info:    Image count:  4",
            "info:    Exclusive FS: 1",
            "warn:  DXGI: MakeWindowAssociation: Ignoring flags",
            "warn:  DxgiSwapChain::GetFrameStatistics: Frame statistics may be inaccurate",
            "wine: setpriority 6 for pid -1 failed: 3",
            "info:  DXVK: Using 8 compiler threads",
            "info:  Presenter: Actual swap chain properties:",
            "info:    Present mode: VK_PRESENT_MODE_FIFO_KHR (dynamic: yes)",
            "info:    Buffer size:  1280x720",
            "info:    Image count:  4",
            "info:    Exclusive FS: 1");

    @Test
    public void madeAgainAndNothingAfter() {
        assertTrue(AgvnSwapchainStuck.lastWords(REPORT));
        List<String> wineAfter = new ArrayList<>(REPORT);
        wineAfter.add("err:module:import_dll Library foo.dll not found");
        wineAfter.add("[AGVN] ×3 dòng lặp lại");
        assertTrue("Wine's own lines and the app's notes are not DXVK's", AgvnSwapchainStuck.lastWords(wineAfter));
    }

    @Test
    public void notWhenDxvkWentOnOrMadeItOnce() {
        List<String> wentOn = new ArrayList<>(REPORT);
        wentOn.add("info:  D3D11CoreCreateDevice: Probing D3D_FEATURE_LEVEL_11_1");
        assertFalse(AgvnSwapchainStuck.lastWords(wentOn));
        List<String> wentOnWarn = new ArrayList<>(REPORT);
        wentOnWarn.add("warn:  DXGI: MakeWindowAssociation: Ignoring flags");
        assertFalse(AgvnSwapchainStuck.lastWords(wentOnWarn));
        assertFalse("its first swap chain only: a game that ran on, as most do",
                AgvnSwapchainStuck.lastWords(REPORT.subList(9, REPORT.size())));
        assertFalse("no DXVK at all", AgvnSwapchainStuck.lastWords(Arrays.asList("fixme:ntdll:foo", "err:x11:bar")));
    }
}
