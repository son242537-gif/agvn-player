/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AgvnLookControlTest {
    @Test
    public void aTapOnTheCameraZoneIsShortAndBarelyMoves() {
        assertTrue(AgvnLookControl.isTap(120, 10f, 27f));
        assertFalse("held: no click", AgvnLookControl.isTap(AgvnLookControl.TAP_MS, 0f, 27f));
        assertFalse("dragged: the camera turned, no click", AgvnLookControl.isTap(80, 40f, 27f));
    }
}
