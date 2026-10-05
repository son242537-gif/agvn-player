/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** "Máy bố, bố biết" on the heat warning and "Chơi luôn" on the battery saver question: quiet for a day. */
public class AgvnQuietDayTest {
    private static final long AT = 1_791_200_000_000L, HOUR = 60 * 60 * 1000L;

    @Test
    public void heatStaysQuietForADayAfterMayBoBoBiet() {
        // A Mali-G610 phone reported a severe thermal status at 36–38 °C: the dialog came at every start
        assertFalse("never chosen", AgvnHeatAck.quiet(0, AT));
        assertTrue(AgvnHeatAck.quiet(AT, AT));
        assertTrue("the next starts that evening", AgvnHeatAck.quiet(AT, AT + 3 * HOUR));
        assertTrue(AgvnHeatAck.quiet(AT, AT + 24 * HOUR - 1));
        assertFalse("told again a day later", AgvnHeatAck.quiet(AT, AT + 24 * HOUR));
        assertFalse("a clock set back", AgvnHeatAck.quiet(AT, AT - HOUR));
    }

    @Test
    public void batterySaverIsNotAskedForADayAfterChoiLuon() {
        assertFalse(AgvnPowerSave.playedAnyway(0, AT));
        assertTrue(AgvnPowerSave.playedAnyway(AT, AT + 23 * HOUR));
        assertFalse(AgvnPowerSave.playedAnyway(AT, AT + 24 * HOUR));
        assertFalse(AgvnPowerSave.playedAnyway(AT, AT - 1));
    }
}
