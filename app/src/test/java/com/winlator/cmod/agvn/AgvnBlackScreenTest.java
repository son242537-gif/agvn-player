/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;

/** A game that stays black: when the bar asks, and the larger screen it offers. */
public class AgvnBlackScreenTest {
    private static final AgvnBlackScreenRules.Look BLACK = AgvnBlackScreenRules.Look.BLACK,
            PICTURE = AgvnBlackScreenRules.Look.PICTURE, UNKNOWN = AgvnBlackScreenRules.Look.UNKNOWN,
            NO_WINDOW = AgvnBlackScreenRules.Look.NO_WINDOW;
    private static final AgvnBlackScreenRules.Step WAIT = AgvnBlackScreenRules.Step.WAIT,
            OFFER = AgvnBlackScreenRules.Step.OFFER, STOP = AgvnBlackScreenRules.Step.STOP;

    /** The step after looking every 2 s, the same look each time, from 2 s to {@code untilMs}. */
    private static AgvnBlackScreenRules.Step run(AgvnBlackScreenRules rules, AgvnBlackScreenRules.Look look, long fromMs,
                                                 long untilMs, boolean refused) {
        AgvnBlackScreenRules.Step step = WAIT;
        for (long t = fromMs; t <= untilMs && step == WAIT; t += AgvnBlackScreenRules.POLL_MS) step = rules.next(look, t, refused);
        return step;
    }

    @Test
    public void blackFromTheStartIsOfferedAfter20Seconds() {
        AgvnBlackScreenRules rules = new AgvnBlackScreenRules(0);
        assertEquals(20_000, rules.waitMs());
        assertEquals(WAIT, run(rules, BLACK, 2000, 18_000, false));
        assertEquals(OFFER, rules.next(BLACK, 20_000, false));
    }

    @Test
    public void aSlowStarterGetsItsTimeFirst() {
        AgvnBlackScreenRules rules = new AgvnBlackScreenRules(60_000); // its last start took a minute
        assertEquals(70_000, rules.waitMs());
        assertEquals(WAIT, run(rules, BLACK, 2000, 68_000, false));
        assertEquals(OFFER, rules.next(BLACK, 70_000, false));
    }

    @Test
    public void aGameThatShowedAPictureIsInAScene() {
        AgvnBlackScreenRules rules = new AgvnBlackScreenRules(0);
        assertEquals(WAIT, rules.next(PICTURE, 10_000, false)); // its logo
        assertEquals(STOP, run(rules, BLACK, 12_000, 200_000, false)); // a fade to black: not asked
        AgvnBlackScreenRules refused = new AgvnBlackScreenRules(0);
        refused.next(PICTURE, 10_000, false);
        assertEquals(OFFER, run(refused, BLACK, 12_000, 30_000, true)); // then its full screen was refused
    }

    @Test
    public void blackMustLastThreeLooks() {
        AgvnBlackScreenRules rules = new AgvnBlackScreenRules(0);
        // a look it cannot read (every third one here) starts the count again
        for (long t = 2000; t <= 24_000; t += 2000) assertEquals(WAIT, rules.next(t % 6000 == 0 ? UNKNOWN : BLACK, t, false));
        assertEquals(WAIT, rules.next(BLACK, 26_000, false));
        assertEquals(WAIT, rules.next(BLACK, 28_000, false));
        assertEquals(OFFER, rules.next(BLACK, 30_000, false));
    }

    @Test
    public void noWindowCountsOnlyWithARefusedResolution() {
        assertEquals(STOP, run(new AgvnBlackScreenRules(0), NO_WINDOW, 2000, 200_000, false)); // still loading
        assertEquals(OFFER, run(new AgvnBlackScreenRules(0), NO_WINDOW, 2000, 30_000, true));
        assertEquals(STOP, run(new AgvnBlackScreenRules(0), UNKNOWN, 2000, 200_000, true)); // a frame it cannot read
    }

    @Test
    public void darkRows() {
        int width = 1280;
        ByteBuffer row = ByteBuffer.allocate(width * 4);
        for (int i = 0; i < width; i++) row.put(i * 4 + 3, (byte) 0xff); // alpha does not count
        assertTrue(AgvnBlackScreenRules.dark(row, width));
        row.put(640 * 4 + 1, (byte) (AgvnBlackScreenRules.DARK)); // very dark grey is still dark
        assertTrue(AgvnBlackScreenRules.dark(row, width));
        row.put(640 * 4 + 2, (byte) 0x80); // a lit pixel where it looks
        assertFalse(AgvnBlackScreenRules.dark(row, width));
    }

    @Test
    public void wineRefusingTheGamesResolution() {
        assertTrue(AgvnBlackScreenRules.refused(Arrays.asList("0024:fixme:ver:GetCurrentPackageId",
                "0124:err:system:NtUserChangeDisplaySettings Changing L\"\\\\\\\\.\\\\DISPLAY1\" display settings returned -2.")));
        assertFalse(AgvnBlackScreenRules.refused(Collections.singletonList("0124:err:module:import_dll Library d3dx9_43.dll")));
    }

    @Test
    public void theLargerScreenOffered() {
        int[] full = {0, 0, 854, 480};
        assertEquals("1280x720", AgvnBlackScreenRules.bigger("854x480", "1280x720", full)); // a KiriKiri game's size
        assertEquals("800x600", AgvnBlackScreenRules.bigger("960x544", "800x600", new int[]{0, 0, 960, 544})); // taller
        assertEquals("1024x768", AgvnBlackScreenRules.bigger("854x480", null, new int[]{0, 0, 1024, 768})); // cut off
        assertEquals("1280x720", AgvnBlackScreenRules.bigger("854x480", "-", full)); // the next screen
        // a maximized window's frame past the edges is no reason for a screen its size: the next one
        assertEquals("1280x720", AgvnBlackScreenRules.bigger("960x544", null, new int[]{-6, -6, 972, 556}));
        assertEquals("1600x900", AgvnBlackScreenRules.bigger("1280x720", "1280x720", new int[]{0, 0, 1280, 720}));
        assertEquals("1600x900", AgvnBlackScreenRules.bigger("1280x800", null, null));
        assertEquals("1920x1080", AgvnBlackScreenRules.bigger("1600x900", null, null));
        assertNull(AgvnBlackScreenRules.bigger("1920x1080", null, new int[]{0, 0, 1920, 1080})); // nothing larger
        assertNull(AgvnBlackScreenRules.bigger("bad", null, null));
    }
}
