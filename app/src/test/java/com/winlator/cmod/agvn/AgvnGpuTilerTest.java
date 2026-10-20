/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

/** A Mali GPU out of tiler memory again and again: counted, asked about while playing, never a good run. */
public class AgvnGpuTilerTest {
    @Test
    public void everyTimeIsCountedThoughTheTailFoldsThem() {
        // Thanh Âm Mùa Hạ (Unreal, Mali-G925, 07/10/2026): a pair of lines every 0.6 s, 30 to 80 in a minute
        AgvnWineTail tail = AgvnWineTail.get();
        tail.reset();
        tail.call("info:  DXVK: Using 5 async compiler threads");
        for (int i = 0; i < 30; i++) {
            tail.call("[" + (124227 + i) + ".847445] (mali-event-hand) BASE: basep_event_dump_error <unknown>");
            tail.call("==>[WARN] Received a GROUP_ERROR_TILER_HEAP_OOM error on group(0)");
        }
        assertEquals(30, tail.tilerOoms());
        tail.reset();
        assertEquals(0, tail.tilerOoms());
    }

    @Test
    public void suchARunDidNotGoWell() {
        AgvnEvidence ev = new AgvnEvidence();
        ev.started = ev.playerQuit = true;
        ev.seconds = 75; // quit after a minute of 1-5 s stalls and a black screen
        ev.tilerOoms = 30;
        assertFalse(ev.good());
        ev.tilerOoms = 3; // once in a while, as a heavy scene loads
        assertTrue(ev.good());
    }

    @Test
    public void theBarOffersALowerStep() throws IOException {
        AgvnProblemCatalog catalog;
        try (Reader in = Files.newBufferedReader(new File("src/main/assets/" + AgvnProblemCatalog.ASSET).toPath(),
                StandardCharsets.UTF_8)) {
            catalog = AgvnProblemCatalog.parse(in);
        }
        AgvnProblemCatalog.Problem p = catalog.byId(AgvnSlowWatch.TILER_PROBLEM);
        assertNotNull(p);
        assertEquals(Collections.singletonList("live"), p.when); // asked while playing, never at the end
        assertEquals(Arrays.asList("quality-down", "send-logs"), p.fixes);
        assertNotNull(catalog.finding(AgvnSlowWatch.TILER_PROBLEM, Collections.emptyMap()));
    }
}
