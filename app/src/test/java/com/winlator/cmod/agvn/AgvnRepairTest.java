/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** "Tự sửa lỗi" for what the player reports: the symptoms, their fixes, and going back when a fix did not help. */
public class AgvnRepairTest {
    private static AgvnProblemCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        try (Reader in = Files.newBufferedReader(new File("src/main/assets/" + AgvnProblemCatalog.ASSET).toPath(),
                StandardCharsets.UTF_8)) {
            catalog = AgvnProblemCatalog.parse(in);
        }
    }

    @Test
    public void everySymptomHasFixesAndIsOnlyReported() {
        int reported = 0;
        for (AgvnProblemCatalog.Problem p : catalog.all()) {
            if (p.when == null || !p.when.contains("reported")) continue;
            reported++;
            assertTrue(p.id, p.id.startsWith("report-"));
            assertEquals(p.id + ": reported, nothing else", 1, p.when.size());
            assertTrue(p.id + " ends with Gửi nhật ký", p.fixes.get(p.fixes.size() - 1).equals("send-logs"));
            assertFalse(p.id + ": a move to another Wine cannot be put back", p.fixes.contains("wine-old"));
        }
        assertEquals(10, reported);
        // a game that crashed, never started, or ran well: never one of these, whatever it printed
        AgvnEvidence ev = new AgvnEvidence();
        ev.crashed = ev.endedByGame = true;
        ev.lines.add("D3D11: flicker black spots");
        assertFalse(catalog.find(ev).id().startsWith("report-"));
        AgvnEvidence good = new AgvnEvidence();
        good.started = good.playerQuit = true;
        good.seconds = 300;
        assertNull(catalog.find(good));
        List<String> flicker = catalog.byId("report-flicker").fixes;
        assertEquals("cheapest first: async off, then frame sync", Arrays.asList("async-off", "present-sync"),
                flicker.subList(0, 2));
        assertEquals("unity-quality-own", catalog.byId("unity-crash").fixes.get(0));
    }

    @Test
    public void aFixThatDidNotHelpGoesBack() {
        Properties state = new Properties();
        state.setProperty(AgvnGoodConfig.HAS_GOOD, "1"); // the doctor's own state stays
        Map<String, String> before = new LinkedHashMap<>();
        before.put("dxwrapperConfig", "version=2.3.1,async=1");
        before.put("envVars", "TU_DEBUG=noconform");
        AgvnRepair.begin(state, "report-flicker", before);
        AgvnRepair.trying(state, new AgvnFixes.Fix("async-off", "Tắt DXVK async", "0"));
        assertEquals("report-flicker", AgvnRepair.symptom(state));
        assertEquals("async-off", AgvnRepair.trying(state));
        assertEquals("Tắt DXVK async", AgvnRepair.label(state));
        assertEquals(before, AgvnRepair.before(state));

        // the same symptom again goes on: what was before stays the settings to go back to
        Map<String, String> changed = new LinkedHashMap<>(before);
        changed.put("dxwrapperConfig", "version=2.3.1,async=0");
        AgvnRepair.begin(state, "report-flicker", changed);
        assertEquals(before, AgvnRepair.before(state));
        AgvnRepair.trying(state, new AgvnFixes.Fix("present-sync", "Bật Đồng bộ khung hình", "1"));
        assertEquals(Arrays.asList("async-off", "present-sync"),
                Arrays.asList(AgvnRepair.tried(state).toArray(new String[0])));

        // another symptom starts afresh, from the settings as they are then
        AgvnRepair.begin(state, "report-artifacts", changed);
        assertEquals(changed, AgvnRepair.before(state));
        assertTrue(AgvnRepair.tried(state).isEmpty());
        assertEquals("", AgvnRepair.trying(state));

        AgvnRepair.clear(state);
        assertEquals("", AgvnRepair.symptom(state));
        assertTrue(AgvnRepair.before(state).isEmpty());
        assertEquals("1", state.getProperty(AgvnGoodConfig.HAS_GOOD));
        assertTrue("Đồ họa's other settings and Unity's own quality go back too",
                AgvnRepair.keys().containsAll(AgvnRepair.MORE_KEYS)
                        && AgvnRepair.keys().contains(AgvnUnityQuality.EXTRA_OWN));
    }

    @Test
    public void turnipFlagsAndOtherSettings() {
        assertEquals("TU_DEBUG=noconform,nolrz", AgvnRepairFixes.withTuFlag("", "noconform", "nolrz"));
        assertEquals("TU_DEBUG=gmem,noubwc", AgvnRepairFixes.withTuFlag("TU_DEBUG=gmem", "noconform", "noubwc"));
        assertEquals("once", "TU_DEBUG=nolrz", AgvnRepairFixes.withTuFlag("TU_DEBUG=nolrz", "", "nolrz"));
        assertEquals("sysmem replaces gmem, other flags stay", "TU_DEBUG=noconform,sysmem",
                AgvnFixEdits.withRenderMode("TU_DEBUG=noconform,gmem", "", "sysmem"));
        assertEquals(AgvnRepairFixes.ALSA, AgvnRepairFixes.otherAudio("pulse-audio-gn"));
        assertEquals(AgvnRepairFixes.ALSA, AgvnRepairFixes.otherAudio("pulseaudio"));
        assertEquals(AgvnRepairFixes.PULSE, AgvnRepairFixes.otherAudio("alsa"));
        for (String id : AgvnRepairFixes.IDS) {
            boolean used = false;
            for (AgvnProblemCatalog.Problem p : catalog.all()) used |= p.fixes.contains(id);
            assertTrue(id + " is a fix of some problem", used);
        }
    }
}
