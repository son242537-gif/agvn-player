package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SessionGuardTest {
    private static SessionGuard.Sample cool(long ram) {
        return new SessionGuard.Sample(35f, 0, 0.5f, ram);
    }

    private static SessionGuard.Sample hot() {
        return new SessionGuard.Sample(46f, 1, 0.8f, 3000);
    }

    @Test
    public void thermalNeedsThreeConsecutiveSamples() {
        SessionGuard g = new SessionGuard();
        assertEquals(SessionGuard.Decision.OK, g.feed(hot(), 0));
        assertEquals(SessionGuard.Decision.OK, g.feed(hot(), 2000));
        assertEquals(SessionGuard.Decision.WARN_THERMAL, g.feed(hot(), 4000));
    }

    @Test
    public void coolSampleResetsCounter() {
        SessionGuard g = new SessionGuard();
        g.feed(hot(), 0);
        g.feed(hot(), 2000);
        assertEquals(SessionGuard.Decision.OK, g.feed(cool(3000), 4000));
        assertEquals(SessionGuard.Decision.OK, g.feed(hot(), 6000));
    }

    @Test
    public void cooldownPreventsNagging() {
        SessionGuard g = new SessionGuard();
        long t = 0;
        for (int i = 0; i < 3; i++) g.feed(hot(), t += 2000);
        for (int i = 0; i < 10; i++) assertEquals(SessionGuard.Decision.OK, g.feed(hot(), t += 2000));
        // still hot after the cooldown -> warn again right away
        assertEquals(SessionGuard.Decision.WARN_THERMAL, g.feed(hot(), t + SessionGuard.COOLDOWN_MS));
    }

    @Test
    public void osThrottleSignalsCountAsHot() {
        assertEquals(true, SessionGuard.isHot(new SessionGuard.Sample(Float.NaN, 3, Float.NaN, 3000)));
        assertEquals(true, SessionGuard.isHot(new SessionGuard.Sample(Float.NaN, -1, 1.05f, 3000)));
        assertEquals(false, SessionGuard.isHot(new SessionGuard.Sample(Float.NaN, -1, Float.NaN, 3000)));
    }

    @Test
    public void lowRamAfterTwoSamplesAndThermalWins() {
        SessionGuard g = new SessionGuard();
        assertEquals(SessionGuard.Decision.OK, g.feed(cool(250), 0));
        assertEquals(SessionGuard.Decision.WARN_LOW_RAM, g.feed(cool(250), 2000));

        SessionGuard both = new SessionGuard();
        SessionGuard.Sample hotAndLow = new SessionGuard.Sample(47f, 0, 0.5f, 200);
        both.feed(hotAndLow, 0);
        both.feed(hotAndLow, 2000);
        assertEquals(SessionGuard.Decision.WARN_THERMAL, both.feed(hotAndLow, 4000));
    }

    @Test
    public void requiredRam() {
        assertEquals(1536, RamGuard.getRequiredRamMb(512));
        assertEquals(1536, RamGuard.getRequiredRamMb(0));
        assertEquals(2048, RamGuard.getRequiredRamMb(1024));
    }
}
