/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
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
        for (long t = fromMs; t <= untilMs && step == WAIT; t += AgvnBlackScreenRules.POLL_MS)
            step = rules.next(look, t, refused, false, false);
        return step;
    }

    @Test
    public void blackFromTheStartIsOfferedAfter20Seconds() {
        AgvnBlackScreenRules rules = new AgvnBlackScreenRules(0);
        assertEquals(20_000, rules.waitMs());
        assertEquals(WAIT, run(rules, BLACK, 2000, 18_000, false));
        assertEquals(OFFER, rules.next(BLACK, 20_000, false, false, false));
    }

    @Test
    public void aSlowStarterGetsItsTimeFirst() {
        AgvnBlackScreenRules rules = new AgvnBlackScreenRules(60_000); // its last start took a minute
        assertEquals(70_000, rules.waitMs());
        assertEquals(WAIT, run(rules, BLACK, 2000, 68_000, false));
        assertEquals(OFFER, rules.next(BLACK, 70_000, false, false, false));
    }

    @Test
    public void aGameThatShowedAPictureIsInAScene() {
        AgvnBlackScreenRules rules = new AgvnBlackScreenRules(0);
        assertEquals(WAIT, rules.next(PICTURE, 10_000, false, false, false)); // its logo
        assertEquals(STOP, run(rules, BLACK, 12_000, 200_000, false)); // a fade to black: not asked
        AgvnBlackScreenRules refused = new AgvnBlackScreenRules(0);
        refused.next(PICTURE, 10_000, false, false, false);
        assertEquals(OFFER, run(refused, BLACK, 12_000, 30_000, true)); // then its full screen was refused
    }

    @Test
    public void aGameBehindItsMessageBoxIsNotAsked() {
        AgvnBlackScreenRules rules = new AgvnBlackScreenRules(0);
        // "Assertion failed!" over a game that never drew: "Tự sửa lỗi" reads the box when the game ends
        AgvnBlackScreenRules.Step step = WAIT;
        for (long t = 2000; t <= 200_000 && step == WAIT; t += 2000) step = rules.next(BLACK, t, false, true, false);
        assertEquals(STOP, step);
        AgvnBlackScreenRules refused = new AgvnBlackScreenRules(0); // a box about full screen, then a refused mode
        for (long t = 2000; t < 20_000; t += 2000) assertEquals(WAIT, refused.next(BLACK, t, true, true, false));
        assertEquals(OFFER, refused.next(BLACK, 20_000, true, true, false));
        assertTrue(AgvnBlackScreenRules.boxShown(Arrays.asList("wine: setpriority 6 for pid -1 failed: 3",
                AgvnErrorBoxTest.traced(AgvnErrorBoxTest.VULKAN_ASSERT))));
        assertFalse(AgvnBlackScreenRules.boxShown(Collections.singletonList("0024:err:module:import_dll X.dll")));
    }

    @Test
    public void blackMustLastThreeLooks() {
        AgvnBlackScreenRules rules = new AgvnBlackScreenRules(0);
        // a look it cannot read (every third one here) starts the count again
        for (long t = 2000; t <= 24_000; t += 2000)
            assertEquals(WAIT, rules.next(t % 6000 == 0 ? UNKNOWN : BLACK, t, false, false, false));
        assertEquals(WAIT, rules.next(BLACK, 26_000, false, false, false));
        assertEquals(WAIT, rules.next(BLACK, 28_000, false, false, false));
        assertEquals(OFFER, rules.next(BLACK, 30_000, false, false, false));
    }

    @Test
    public void aGameStillLoadingIsNotAsked() {
        // Lg Light (Unity, its resolution refused) stayed black while its memory rose 150-300 MB every 10 s, until it
        // ran out of RAM 50-90 s in: the bar offered it a larger screen at 22 s, which only takes more RAM
        AgvnBlackScreenRules rules = new AgvnBlackScreenRules(0);
        for (long t = 2000; t <= 90_000; t += 2000) assertEquals(WAIT, rules.next(BLACK, t, true, false, true));
        // black once the memory stops growing counts, and the watch runs from the end of the loading
        AgvnBlackScreenRules loaded = new AgvnBlackScreenRules(0);
        for (long t = 2000; t <= 70_000; t += 2000) loaded.next(BLACK, t, false, false, true);
        assertEquals(WAIT, loaded.next(BLACK, 72_000, false, false, false));
        assertEquals(WAIT, loaded.next(BLACK, 74_000, false, false, false));
        assertEquals(OFFER, loaded.next(BLACK, 76_000, false, false, false));
        AgvnBlackScreenRules scene = new AgvnBlackScreenRules(0);
        for (long t = 2000; t <= 70_000; t += 2000) scene.next(BLACK, t, false, false, true);
        assertEquals(WAIT, scene.next(PICTURE, 72_000, false, false, false));
        assertEquals(WAIT, run(scene, BLACK, 74_000, 128_000, false));
        assertEquals(STOP, scene.next(BLACK, 130_000, false, false, false));

        long within = AgvnBlackScreenRules.LOADING_SAMPLE_MS; // the memory watch samples every 5 s
        AgvnSessionTrack.start(1_000);
        assertEquals("no sample yet", 0, AgvnSessionTrack.grewMb(6_000, within));
        AgvnSessionTrack.used(6_000, 900);
        assertEquals("one sample", 0, AgvnSessionTrack.grewMb(7_000, within));
        AgvnSessionTrack.used(11_000, 1_050);
        assertEquals(150, AgvnSessionTrack.grewMb(12_000, within));
        assertEquals("an old sample", 0, AgvnSessionTrack.grewMb(19_000, within));
        AgvnSessionTrack.start(30_000);
        assertEquals("a new game", 0, AgvnSessionTrack.grewMb(30_500, within));
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
    public void aLargerScreenOnlyWhenWineRefusedTheGamesResolution() {
        // Legend Cleaner (Unreal) on a Mali-G610, black while it loads and then out of RAM: 711x400 in a 719x426 frame
        // on 854x480, 1066x600 on 1280x720, 1440x720 on 1600x900. Each larger screen only made a larger window.
        assertNull(AgvnBlackScreenRules.bigger("854x480", null, new int[]{67, 27, 719, 426}, false));
        assertNull(AgvnBlackScreenRules.bigger("1600x900", "-", new int[]{76, 73, 1448, 754}, false));
        // Support Pregnancy School (Unreal) on a Mali-G615 (0.1.17): black full screen at 1280x720 after 32 s; it
        // started with "Đồng bộ khung hình" and "Tắt Present Wait" on, not with a larger screen
        int[] hd = {0, 0, 1280, 720};
        assertNull(AgvnBlackScreenRules.bigger("1280x720", "-", hd, false));
        int[] maximized = {-6, -6, 972, 556};
        assertNull("a maximized window", AgvnBlackScreenRules.bigger("960x544", null, maximized, false));
        assertNull("no window seen", AgvnBlackScreenRules.bigger("1280x800", null, null, false));
        // Wine refused the game's mode: the screen is too small for it
        assertEquals("1280x720", AgvnBlackScreenRules.bigger("854x480", null, new int[]{67, 27, 719, 426}, true));
        assertEquals("1600x900", AgvnBlackScreenRules.bigger("1280x720", "-", hd, true));
    }

    @Test
    public void theLargerScreenOffered() {
        int[] full = {0, 0, 854, 480};
        // a KiriKiri game's size, also when it is taller
        assertEquals("1280x720", AgvnBlackScreenRules.bigger("854x480", "1280x720", full, false));
        assertEquals("800x600", AgvnBlackScreenRules.bigger("960x544", "800x600", new int[]{0, 0, 960, 544}, false));
        // a window the screen cuts off: its own size
        assertEquals("1024x768", AgvnBlackScreenRules.bigger("854x480", null, new int[]{0, 0, 1024, 768}, false));
        assertEquals("1280x720", AgvnBlackScreenRules.bigger("854x480", "-", full, true)); // refused: the next screen
        // a maximized window's frame past the edges is no reason for a screen its size: the next one
        assertEquals("1280x720", AgvnBlackScreenRules.bigger("960x544", null, new int[]{-6, -6, 972, 556}, true));
        int[] hd = {0, 0, 1280, 720};
        assertEquals("1600x900", AgvnBlackScreenRules.bigger("1280x720", "1280x720", hd, true));
        assertEquals("1600x900", AgvnBlackScreenRules.bigger("1280x800", null, null, true));
        assertEquals("1920x1080", AgvnBlackScreenRules.bigger("1600x900", null, null, true));
        int[] fullHd = {0, 0, 1920, 1080};
        assertNull("nothing larger", AgvnBlackScreenRules.bigger("1920x1080", null, fullHd, true));
        assertNull(AgvnBlackScreenRules.bigger("bad", null, null, true));
    }
}
