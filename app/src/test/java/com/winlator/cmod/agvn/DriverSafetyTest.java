package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DriverSafetyTest {
    private static final String DENYLIST =
            "[{\"gpu\": \"Adreno \\\\(TM\\\\) 8\\\\d\\\\d\", \"hide\": [\"System\", \"v863\"]}]";

    @Test
    public void parseVulkanPatch() {
        assertEquals(289, DriverSafety.parseVulkanPatch("1.3.289"));
        assertEquals(0, DriverSafety.parseVulkanPatch("Unknown"));
        assertEquals(0, DriverSafety.parseVulkanPatch(null));
        assertEquals(0, DriverSafety.parseVulkanPatch("1.3"));
        assertEquals(128, DriverSafety.parseVulkanPatch("1.4.128-dev"));
    }

    @Test
    public void driverIsWrittenOffOnlyAfterTwoFailures() {
        assertEquals(0, DriverSafety.previousFailures(null));
        assertEquals("bad1", DriverSafety.failedState(DriverSafety.previousFailures(null)));
        assertEquals(1, DriverSafety.previousFailures("bad1"));
        assertEquals("bad", DriverSafety.failedState(DriverSafety.previousFailures("bad1")));
        assertEquals(1, DriverSafety.previousFailures("probing:1234:1"));
        assertEquals(0, DriverSafety.previousFailures("probing:1234"));
        assertEquals(0, DriverSafety.previousFailures("probing:1234:x"));
    }

    @Test
    public void denylistHidesSystemAndV863OnAdreno8xx() {
        DriverDenylist list = DriverDenylist.parse(DENYLIST);
        assertTrue(list.isDenylisted("Adreno (TM) 830", "System"));
        assertTrue(list.isDenylisted("Adreno (TM) 830", "system"));
        assertTrue(list.isDenylisted("Adreno (TM) 830", "v863"));
        assertFalse(list.isDenylisted("Adreno (TM) 830", "turnip26.2.0"));
    }

    @Test
    public void denylistKeepsOlderAdreno() {
        DriverDenylist list = DriverDenylist.parse(DENYLIST);
        assertFalse(list.isDenylisted("Adreno (TM) 740", "System"));
        assertFalse(list.isDenylisted("Mali-G78", "System"));
    }

    @Test
    public void brokenDenylistIsEmpty() {
        assertFalse(DriverDenylist.parse("not json").isDenylisted("Adreno (TM) 830", "System"));
        assertFalse(DriverDenylist.parse("[{\"gpu\": \"(\", \"hide\": [\"System\"]}]").isDenylisted("Adreno (TM) 830", "System"));
    }
}
