/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AgvnBcnTest {
    @Test
    public void turnipNeverEmulates() {
        assertEquals("none", AgvnBcn.effective("auto", "turnip26.2.0"));
        assertEquals("none", AgvnBcn.effective("full", "Turnip-25.3"));
        assertEquals("none", AgvnBcn.effective("partial", "adrenotools-turnip26.2.0"));
    }

    @Test
    public void otherDriversKeepThePlayersChoice() {
        assertEquals("auto", AgvnBcn.effective("auto", "System"));
        assertEquals("full", AgvnBcn.effective("full", "v863"));
        assertEquals("none", AgvnBcn.effective("none", "System"));
        assertEquals("auto", AgvnBcn.effective("auto", null));
    }
}
