/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AgvnPresentSyncTest {
    @Test
    public void aBlackDirectXGameIsOfferedWhatGotSupportPregnancySchoolToShow() {
        // Support Pregnancy School (Unreal) on a Mali-G615 stayed black on the defaults and started with both on;
        // the black-screen bar now offers them to such a game, on any phone, instead of every game getting them
        String config = "version=System;presentMode=mailbox;syncFrame=0;disablePresentWait=0;bcnEmulation=auto";
        assertFalse(AgvnPresentSync.on(config));
        String both = AgvnPresentSync.withBoth(config);
        assertEquals("version=System;presentMode=mailbox;syncFrame=1;disablePresentWait=1;bcnEmulation=auto", both);
        assertTrue(AgvnPresentSync.on(both));
        assertTrue("added when absent", AgvnPresentSync.on(AgvnPresentSync.withBoth("version=System")));
        assertFalse("one of the two", AgvnPresentSync.on("syncFrame=1;disablePresentWait=0"));
        assertFalse(AgvnPresentSync.on(null));
    }
}
