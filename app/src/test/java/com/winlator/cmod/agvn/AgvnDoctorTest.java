/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** "Tự sửa lỗi": game-problems.json read with the lines Wine, DXVK, Godot and Unity really print. */
public class AgvnDoctorTest {
    private static final Set<String> FIXES = new HashSet<>(Arrays.asList("restore-good", "reset", "quality-down",
            "quality-up", "dxvk-other", "dxvk-arm64ec", "driver-other", "wined3d", "godot-renderer", "godot-angle", "godot-undo",
            "render-gmem", "render-auto", "emulator-stable", "emulator-fast", "wincomponent", "power-save-settings",
            "app-settings", "send-logs", "run-windows", "rgss-frameskip"));
    private static final Set<String> CONDITIONS = new HashSet<>(Arrays.asList("failed", "no-start", "crash", "ended",
            "ended-early", "godot-switched", "changed", "small-screen", "killed-low-memory", "killed-background", "live",
            "light", "script-error", "page-crash", "frozen"));
    static AgvnProblemCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        try (Reader in = Files.newBufferedReader(new File("src/main/assets/" + AgvnProblemCatalog.ASSET).toPath(), StandardCharsets.UTF_8)) {
            catalog = AgvnProblemCatalog.parse(in);
        }
    }

    /** A game that closed by itself before drawing its window, having printed {@code lines}. */
    private static AgvnEvidence noStart(String... lines) {
        AgvnEvidence ev = new AgvnEvidence();
        ev.endedByGame = true;
        ev.seconds = 8;
        ev.lines.addAll(Arrays.asList(lines));
        return ev;
    }

    private static String found(AgvnEvidence ev) {
        AgvnProblemCatalog.Finding f = catalog.find(ev);
        return f != null ? f.id() : null;
    }

    @Test
    public void everyProblemIsComplete() {
        Set<String> ids = new HashSet<>();
        for (AgvnProblemCatalog.Problem p : catalog.all()) {
            assertTrue("duplicate " + p.id, ids.add(p.id));
            assertFalse(p.id, p.title == null || p.title.isEmpty() || p.cause == null || p.cause.isEmpty());
            assertFalse(p.id + " has no fix", p.fixes == null || p.fixes.isEmpty());
            for (String fix : p.fixes) assertTrue(p.id + ": unknown fix " + fix, FIXES.contains(fix));
            if (p.when != null) for (String c : p.when) assertTrue(p.id + ": unknown condition " + c, CONDITIONS.contains(c));
        }
        for (String live : Arrays.asList("slow-gpu", "slow-cpu", "slow", "slow-unknown", "power-save"))
            assertNotNull(live, catalog.byId(live));
    }

    @Test
    public void missingDllNamesTheFile() {
        AgvnEvidence ev = noStart("0024:err:module:import_dll Library MSVCP140.dll (which is needed by L\"C:\\\\Games\\\\Cleaner\\\\Game.exe\") not found",
                "0024:err:module:loader_init Importing dlls for L\"C:\\\\Games\\\\Cleaner\\\\Game.exe\" failed, status c0000135");
        AgvnProblemCatalog.Finding f = catalog.find(ev);
        assertEquals("missing-dll", f.id());
        assertEquals("Game thiếu file MSVCP140.dll", f.title());
        assertTrue(f.cause(), f.cause().contains("MSVCP140.dll"));
        assertEquals("steam", found(noStart("0024:err:module:import_dll Library steam_api64.dll (which is needed by L\"C:\\\\g\\\\a.exe\") not found")));
        assertEquals("bad-dll", found(noStart("0024:err:module:import_dll Loading library fmod.dll (which is needed by "
                + "L\"C:\\\\g\\\\a.exe\") failed (error c000007b).")));
        assertEquals("dotnet", found(noStart("0110:err:mscoree:CLRRuntimeInfo_GetRuntimeHost Wine Mono is not installed")));
    }

    @Test
    public void directXErrorsOfDxvk() {
        assertEquals("dxvk-no-adapter", found(noStart("warn:  DXVK: No adapters found. Please check your device filter settings "
                + "and Vulkan setup. A Vulkan 1.3 capable driver is required.")));
        assertEquals("directx", found(noStart("err:   DxvkAdapter: Failed to create device",
                "err:   D3D11InternalCreateDevice: Failed to create D3D11 device")));
        assertEquals("directx", found(noStart("InitializeEngineGraphics failed"))); // Unity's Player.log
        AgvnEvidence ran = noStart("err:   D3D11InternalCreateDevice: Minimum required feature level D3D_FEATURE_LEVEL_11_1 not supported");
        ran.started = true; // it tried 11_1, then ran on 11_0: nothing to fix
        ran.endedByGame = false;
        ran.playerQuit = true;
        assertNull(found(ran));
        AgvnEvidence lost = new AgvnEvidence();
        lost.started = lost.endedByGame = true;
        lost.lines.add("err:   DxvkSubmissionQueue: Command submission failed: VK_ERROR_DEVICE_LOST");
        assertEquals("gpu-lost", found(lost));
        lost.lines.set(0, "Could not allocate memory: System out of memory!");
        assertEquals("memory", found(lost));
    }

    @Test
    public void godotGetsItsOwnAdvice() {
        AgvnEvidence ev = noStart("Godot Engine v4.3.stable.official.77dcf97d8 - https://godotengine.org");
        ev.engine = "GODOT";
        assertEquals("godot-no-start", found(ev));
        assertArrayEquals(new int[]{4, 3}, AgvnGodotGame.fromLines(ev.lines));
        ev.lines.add("WARNING: Your video card drivers seem not to support the required OpenGL 3.3 version, switching to ANGLE.");
        assertEquals("godot-opengl", found(ev));
        assertArrayEquals(new int[]{3, 5}, AgvnGodotGame.fromLines(Collections.singletonList("Godot Engine v3.5.2.stable.official.170ba337a")));
        assertEquals(AgvnFixEdits.GODOT3_ARGS, AgvnFixEdits.godotArgs("3"));
        assertEquals(AgvnFixEdits.GODOT4_ARGS, AgvnFixEdits.godotArgs(null));
    }

    @Test
    public void aGameThatRefusesASmallScreen() {
        // "The current resolution is too low to run this game.": an error box, nothing in the log
        AgvnEvidence ev = noStart();
        ev.smallScreen = true;
        ev.params.put("screen", "640×360");
        AgvnProblemCatalog.Finding f = catalog.find(ev);
        assertEquals("low-resolution", f.id());
        assertEquals("Game không chạy ở độ phân giải 640×360", f.title());
        assertEquals(Arrays.asList("quality-up", "restore-good"), f.fixes());
        ev.smallScreen = false;
        ev.changed = true;
        assertEquals("no-start-after-change", found(ev));
        ev.changed = false;
        assertEquals("no-start", found(ev));
    }

    @Test
    public void whoEndedTheGameMatters() {
        AgvnEvidence quit = new AgvnEvidence();
        quit.playerQuit = true;
        quit.seconds = 5;
        assertNull("left at once: nothing to say", found(quit));
        quit.seconds = 15;
        assertEquals("only an error box for 15 s", "no-start", found(quit));
        quit.bigWindowSeen = true; // a Ren'Py presplash: still loading
        assertNull(found(quit));
        AgvnEvidence played = new AgvnEvidence();
        played.started = played.playerQuit = true;
        played.seconds = 600;
        assertNull(found(played));
        assertTrue(played.good());
        AgvnEvidence crash = new AgvnEvidence();
        crash.crashed = crash.started = true;
        crash.params.put("crash", "page fault đọc 0x0 trong game.exe+0x1234");
        AgvnProblemCatalog.Finding f = catalog.find(crash);
        assertEquals("crash", f.id());
        assertTrue(f.cause(), f.cause().contains("page fault đọc 0x0"));
        assertFalse(crash.good());
        crash.changed = true;
        assertEquals("crash-after-change", found(crash));
    }

    @Test
    public void androidEndingTheApp() {
        AgvnEvidence ev = new AgvnEvidence();
        ev.killedReason = AgvnEvidence.REASON_LOW_MEMORY;
        assertEquals("killed-low-memory", found(ev));
        ev.killedReason = AgvnEvidence.REASON_SIGNALED;
        ev.killedImportance = 125; // in the background, kept by AGVN's notification
        assertEquals("killed-background", found(ev));
        ev.killedImportance = 100; // on screen: not a background clean-up
        assertNull(found(ev));
        ev.killedReason = 10; // the player swiped AGVN away
        assertNull(found(ev));
    }

    @Test
    public void aScreenTheGameRefusedIsRemembered() {
        List<String> screenOnly = Arrays.asList("screenSize", AgvnQuality.EXTRA_QUALITY);
        assertTrue(AgvnDoctor.refusedScreen("low-resolution", false, Collections.emptyList(), "640x360", null));
        assertTrue(AgvnDoctor.refusedScreen("no-start-after-change", true, screenOnly, "640x360", "854x480"));
        assertFalse("larger now", AgvnDoctor.refusedScreen("no-start-after-change", true, screenOnly, "1280x720", "854x480"));
        assertFalse("DXVK changed too", AgvnDoctor.refusedScreen("no-start-after-change", true,
                Arrays.asList("screenSize", "dxwrapperConfig"), "640x360", "854x480"));
        assertFalse(AgvnDoctor.refusedScreen("crash", true, screenOnly, "640x360", "854x480"));
        assertEquals(360, AgvnDoctor.screenLines("640x360"));
        assertEquals(Integer.MAX_VALUE, AgvnDoctor.screenLines("auto"));
    }

    @Test
    public void textsFillTheirValues() {
        assertEquals("a 1 ? b", AgvnProblemCatalog.fill("a {1} {missing} b", Collections.singletonMap("1", "1")));
        assertEquals("no braces", AgvnProblemCatalog.fill("no braces", Collections.emptyMap()));
        assertEquals("open {", AgvnProblemCatalog.fill("open {", Collections.emptyMap()));
    }
}
