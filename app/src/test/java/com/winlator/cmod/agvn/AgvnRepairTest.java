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
import java.util.Collections;
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
        assertEquals(12, reported);
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
        // a Godot game: Godot for Android first where it reads the game, then its renderers (Train45, 07/10/2026)
        assertEquals(Arrays.asList("godot-light", "godot-light-renderer", "godot-renderer", "godot-angle"),
                catalog.byId("report-animation").fixes.subList(0, 4));
        assertEquals("godot-light", catalog.byId("report-slow").fixes.get(0));
        // a game with mods that freezes: without them second (Rina, BepInEx, 07/10/2026)
        assertEquals(Arrays.asList("emulator-stable", "mods-off"), catalog.byId("report-freeze").fixes.subList(0, 2));
        // taps a game takes no notice of: as a real mouse first (Open At Nine, Unity Input System, 08/10/2026)
        assertEquals("raw-mouse", catalog.byId("report-touch").fixes.get(0));
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
    public void aFixIsAskedAboutOnceTheGameRanWithIt() {
        Properties state = new Properties();
        assertTrue("a trial begun before 0.1.27 has no time: asked as before", AgvnRepair.ran(state, ""));
        String oldRun = String.valueOf(System.currentTimeMillis() - 1);
        AgvnRepair.trying(state, new AgvnFixes.Fix("reset", "Về cấu hình gốc", "1"));
        // Isekai NTR Inn, 07/10/2026: "Vẫn còn lỗi" 11 s after the fix was put on, the game still on its old run
        assertFalse(AgvnRepair.ran(state, oldRun));
        assertFalse("never started", AgvnRepair.ran(state, ""));
        assertTrue(AgvnRepair.ran(state, String.valueOf(System.currentTimeMillis() + 1000)));
        AgvnRepair.clear(state);
        assertTrue(state.isEmpty());
    }

    @Test
    public void aFixTheGameDidNotStartWithIsNotCounted() {
        // Support Pregnancy School, Galaxy M34, 09/10/2026: "Dùng WineD3D" on trial, the game started with DXVK
        String dx = "dxvk+vkd3d", dxConfig = "version=1.10.3,vkd3dLevel=12_1";
        String driver = "vulkanVersion=1.3;version=;syncFrame=0";
        Map<String, String> before = new LinkedHashMap<>();
        before.put("dxwrapperConfig", "version=1.10.3,vkd3dLevel=9_1");
        assertFalse(AgvnRepairCheck.on("wined3d", "wined3d", before, dx, dxConfig, driver));
        Map<String, String> wined3d = new LinkedHashMap<>(before);
        wined3d.put("dxwrapper", "wined3d");
        assertTrue(AgvnRepairCheck.on("wined3d", "wined3d", wined3d, dx, dxConfig, driver));
        assertTrue(AgvnRepairCheck.on("dxvk-back", "dxvk+vkd3d", before, dx, dxConfig, driver));
        assertFalse(AgvnRepairCheck.on("dxvk-back", "dxvk+vkd3d", wined3d, dx, dxConfig, driver));
        // another DXVK: the version it set, still with DXVK
        assertFalse(AgvnRepairCheck.on("dxvk-other", "2.3.1", before, dx, dxConfig, driver));
        Map<String, String> newer = new LinkedHashMap<>();
        newer.put("dxwrapperConfig", "version=2.3.1,vkd3dLevel=9_1");
        assertTrue(AgvnRepairCheck.on("dxvk-other", "2.3.1", newer, dx, dxConfig, driver));
        assertTrue("a trial begun before 0.1.34 has no target",
                AgvnRepairCheck.on("dxvk-other", "", before, dx, dxConfig, driver));
        newer.put("dxwrapper", "wined3d");
        assertFalse(AgvnRepairCheck.on("dxvk-other", "2.3.1", newer, dx, dxConfig, driver));
        // a driver: no version of the game's own is the phone's
        assertTrue(AgvnRepairCheck.on("driver-other", AgvnFixes.SYSTEM, before, dx, dxConfig, driver));
        Map<String, String> turnip = new LinkedHashMap<>();
        turnip.put("graphicsDriverConfig", "vulkanVersion=1.3;version=turnip26.2.0;syncFrame=1");
        assertFalse(AgvnRepairCheck.on("driver-other", AgvnFixes.SYSTEM, turnip, dx, dxConfig, driver));
        assertTrue(AgvnRepairCheck.on("driver-other", "turnip26.2.0", turnip, dx, dxConfig, driver));
        assertTrue("the others count as on", AgvnRepairCheck.on("reset", "", before, dx, dxConfig, driver));

        // let go: no fix on trial, that one not counted as tried; the report goes on from the same settings before
        Properties state = new Properties();
        AgvnRepair.begin(state, "report-black", before);
        AgvnRepair.trying(state, new AgvnFixes.Fix("dxvk-other", "Đổi DXVK sang bản 2.3.1", "2.3.1"));
        AgvnRepair.trying(state, new AgvnFixes.Fix("wined3d", "Dùng WineD3D thay DXVK", "wined3d"));
        assertEquals("wined3d", state.getProperty(AgvnRepair.TO));
        assertEquals("Dùng WineD3D thay DXVK", AgvnRepairCheck.letGo(state));
        assertEquals("", AgvnRepair.trying(state));
        assertEquals(Collections.singleton("dxvk-other"), AgvnRepair.tried(state));
        assertNull(state.getProperty(AgvnRepair.TO));
        assertEquals("report-black", AgvnRepair.symptom(state));
        assertEquals(before, AgvnRepair.before(state));
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
