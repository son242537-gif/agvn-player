/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** A movie Wine cannot decode: its lines in Wine's output, and the Wine log and tail without their flood. */
public class AgvnMovieRulesTest {
    /** The lines of a KiriKiri game's WMV3 opening movie, from its Wine log (0.1.20, "Bật debug Wine" on). */
    static final String CAPS = "0:00:12.204522958 13093 0xb400007a2c5e3c00 WARN                    WINE unixlib.c:127:"
            + "find_element_factories: Failed to find any element factory matching type 64x, caps video/x-wmv, "
            + "width=(int)1280, height=(int)720, pixel-aspect-ratio=(fraction)1/1, framerate=(fraction)30/1, "
            + "wmvversion=(int)3, format=(string)WMV3 / video/x-wmv, parsed=(boolean)true, wmvversion=(int)3.";
    static final String REFUSED = "0:00:12.420917458 13093 0xb400007a2c5e3c00 WARN            videodecoder "
            + "gstvideodecoder.c:928:gst_video_decoder_setcaps:<avdec_wmv3-1> Subclass refused caps";
    static final String PUSH = "0:00:12.421000000 13093 0xb400007a2c5e3c00 WARN                    WINE "
            + "wg_transform.c:1169:get_transform_output: Failed to push transform input, error -4";

    @Test
    public void theMoviesFormat() {
        assertEquals("WMV3 1280x720", AgvnMovieRules.movie(CAPS));
        assertNull(AgvnMovieRules.movie("WARN WINE wg_format.c: caps video/x-raw, format=(string)NV12, width=(int)1280"));
        assertNull(AgvnMovieRules.movie(REFUSED));
        assertEquals("H264", AgvnMovieRules.movie("Failed to find any element factory matching caps video/x-h264, "));
        assertTrue(AgvnMovieRules.failed(REFUSED));
        assertTrue(AgvnMovieRules.failed(PUSH));
        assertFalse(AgvnMovieRules.failed(CAPS));
    }

    @Test
    public void aMovieThatDoesNotPlay() {
        AgvnMovieRules rules = new AgvnMovieRules();
        assertFalse(rules.add(CAPS, 0));
        assertEquals("WMV3 1280x720", rules.movie());
        long t = 200;
        int found = 0;
        for (int i = 0; i < 3000; i++, t += 1) if (rules.add(i % 2 == 0 ? REFUSED : PUSH, t)) found++; // 3 s, 1000 a second
        assertEquals("once", 1, found);
        assertTrue(rules.stillFailing(t));
        assertFalse(rules.stillFailing(t + AgvnMovieRules.QUIET_MS + 1));
        // another movie that does not play, after the first one stopped
        t += AgvnMovieRules.REARM_MS + 1;
        for (int i = 0; i < 2000; i++, t += 1) if (rules.add(REFUSED, t)) found++;
        assertEquals(2, found);
    }

    @Test
    public void aFewRefusedFramesAreNotAMovieThatDoesNotPlay() {
        AgvnMovieRules rules = new AgvnMovieRules();
        // a burst shorter than a second: a decoder trying caps before it takes others
        for (int i = 0; i < 500; i++) assertFalse(rules.add(REFUSED, i));
        // a few now and then
        AgvnMovieRules slow = new AgvnMovieRules();
        for (int i = 0; i < 400; i++) assertFalse(slow.add(PUSH, i * 1000L));
        assertFalse(new AgvnMovieRules().stillFailing(0));
    }

    @Test
    public void theWineLogWithoutItsFlood() {
        assertEquals("WARN                    WINE wg_transform.c:1169:get_transform_output: Failed to push transform input, "
                + "error -4", AgvnLogRepeats.key(PUSH));
        assertEquals("warn:quartz:MediaEvent_FreeEventParams graph 0x…, code 0x…, stub!",
                AgvnLogRepeats.key("00e8:warn:quartz:MediaEvent_FreeEventParams graph 0x7a2c5e3c, code 0x1, stub!"));
        AgvnLogRepeats log = new AgvnLogRepeats();
        assertEquals(Arrays.asList(CAPS), log.add(CAPS, 0));
        assertEquals(Arrays.asList(REFUSED), log.add(REFUSED, 1));
        assertEquals(Arrays.asList(PUSH), log.add(PUSH, 2));
        for (int i = 0; i < 1000; i++) assertTrue(log.add(i % 2 == 0 ? REFUSED : PUSH, 3 + i).isEmpty());
        assertEquals(Arrays.asList("0024:err:module:import_dll Library X.dll not found"),
                log.add("0024:err:module:import_dll Library X.dll not found", 1004));
        List<String> second = log.tick(1005);
        assertEquals(2, second.size());
        assertTrue(second.get(0), second.get(0).startsWith("[AGVN] ×500 nữa: WARN            videodecoder"));
        assertTrue(second.get(1), second.get(1).startsWith("[AGVN] ×500 nữa: WARN                    WINE wg_transform"));
        assertTrue(log.tick(2005).isEmpty());
        // not seen for FORGET_MS: forgotten, then written in full again
        assertTrue(log.tick(1004 + AgvnLogRepeats.FORGET_MS + 1).isEmpty());
        assertEquals(Arrays.asList(REFUSED), log.add(REFUSED, 1004 + AgvnLogRepeats.FORGET_MS + 2));
    }

    @Test
    public void theWineLogStopsAtItsCap() {
        AgvnLogRepeats log = new AgvnLogRepeats(100);
        assertEquals(1, log.add("line one, forty characters long........", 0).size());
        assertEquals(1, log.add("line two, forty characters long........", 1).size());
        List<String> full = log.add("line three, forty characters long......", 2);
        assertEquals(1, full.size());
        assertTrue(full.get(0), full.get(0).startsWith("[AGVN] Log Wine đã quá"));
        assertTrue(log.add("line four", 3).isEmpty());
    }

    @Test
    public void theWineTailKeepsOtherLinesThroughAFlood() {
        AgvnWineTail tail = AgvnWineTail.get();
        tail.reset();
        tail.call("0024:err:module:import_dll Library X.dll not found");
        for (int i = 0; i < 5000; i++) tail.call(i % 2 == 0 ? REFUSED : PUSH);
        tail.call("wine: Unhandled page fault on read access to 0x00000000");
        List<String> lines = tail.lines();
        assertEquals(Arrays.asList("0024:err:module:import_dll Library X.dll not found", REFUSED, PUSH,
                "[AGVN] ×4998 dòng lặp lại", "wine: Unhandled page fault on read access to 0x00000000"), lines);
        tail.reset();
    }
}
