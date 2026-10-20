/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void unpackedTexturesGetAQuarterOfTheUnrealPool() {
        // Legend Cleaner (Unreal) on a Mali-G610 with 7.2 GB, System driver: 4.1 GB while loading, then ended for memory
        assertTrue(AgvnBcn.unpacked("auto", "System"));
        assertTrue(AgvnBcn.unpacked("", "System")); // unset: the default, auto
        assertTrue(AgvnBcn.unpacked("full", "v863"));
        assertFalse(AgvnBcn.unpacked("auto", "turnip26.2.0"));
        assertFalse(AgvnBcn.unpacked("none", "System"));
        assertEquals(128, AgvnBcn.texturePool(512, true)); // Thấp
        assertEquals(96, AgvnBcn.texturePool(384, true)); // Siêu nhẹ
        assertEquals(AgvnBcn.MIN_POOL_MB, AgvnBcn.texturePool(200, true));
        assertEquals("never larger", 48, AgvnBcn.texturePool(48, true));
        assertEquals(512, AgvnBcn.texturePool(512, false)); // Turnip reads BCn as it is
        assertEquals("the game's own pool", 0, AgvnBcn.texturePool(0, true));
    }
}
