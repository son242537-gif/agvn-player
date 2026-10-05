/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.winlator.cmod.core.EnvVars;

import org.junit.Test;

public class AgvnPresentSyncTest {
    @Test
    public void aPhoneThatCannotCopeShowsFramesOneAtATime() {
        // Support Pregnancy School (Unreal) on a Mali-G615 with 7.2 GB, the phone's own driver: black on the defaults,
        // started with "Đồng bộ khung hình" and "Tắt Present Wait" on
        assertTrue(AgvnPresentSync.wanted(true, "System"));
        assertTrue(AgvnPresentSync.wanted(true, "v863"));
        assertTrue("driver not known", AgvnPresentSync.wanted(true, null));
        assertFalse("Turnip: the POCO runs on the defaults", AgvnPresentSync.wanted(true, "turnip26.2.0"));
        assertFalse("a phone that can cope", AgvnPresentSync.wanted(false, "System"));

        EnvVars env = new EnvVars("WRAPPER_DISABLE_PRESENT_WAIT=0 MESA_VK_WSI_PRESENT_MODE=mailbox");
        AgvnPresentSync.applyTo(env);
        assertEquals("forcesync", env.get("MESA_VK_WSI_DEBUG"));
        assertEquals("1", env.get("WRAPPER_DISABLE_PRESENT_WAIT"));
        assertEquals("the present mode stays", "mailbox", env.get("MESA_VK_WSI_PRESENT_MODE"));
    }

    @Test
    public void forcesyncJoinsWhatMesaIsAlreadyTold() {
        assertEquals("forcesync", AgvnPresentSync.withSync(null));
        assertEquals("forcesync", AgvnPresentSync.withSync(" "));
        assertEquals("DRI3 off", "sw,forcesync", AgvnPresentSync.withSync("sw"));
        assertEquals("forcesync", AgvnPresentSync.withSync("forcesync"));
        assertEquals("sw,forcesync", AgvnPresentSync.withSync("sw,forcesync"));
    }
}
