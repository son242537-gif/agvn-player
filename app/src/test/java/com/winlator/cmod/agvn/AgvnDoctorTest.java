/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
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
            "app-settings", "send-logs", "run-windows", "rgss-frameskip", "wrapper-constants", "wrapper-clip", "wine-mono"));
    private static final Set<String> CONDITIONS = new HashSet<>(Arrays.asList("failed", "no-start", "crash", "ended",
            "ended-early", "godot-switched", "changed", "small-screen", "killed-low-memory", "killed-background", "low-ram",
            "ram-saved", "live", "light", "script-error", "page-crash", "frozen"));
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
        for (String ranOut : AgvnMemorySaver.OUT_OF_RAM) assertNotNull(ranOut, catalog.byId(ranOut));
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
    public void aGameThatFrozeBeforeItClosedDidNotRunWell() {
        // Party Me (Godot on ANGLE) on a Mali-G615: no frame for 22 s while the player tapped on, then it closed itself
        // 88 s in. 0.1.17 kept that run as good, which forgot the renderers tried, so the app went round them again.
        long start = 5_000_000;
        AgvnSessionTrack.start(start);
        assertEquals("not ended yet", -1, AgvnSessionTrack.frozenSecondsAtEnd());
        for (int i = 0; i < AgvnSessionTrack.STARTED_UPDATES; i++) AgvnSessionTrack.onFrame(start + 30_000 + i * 33);
        AgvnSessionTrack.onFrame(start + 60_000);
        for (int i = 0; i < 3; i++) AgvnSessionTrack.onPress();
        AgvnSessionTrack.onFrame(start + 66_000); // it answered
        assertEquals(0, AgvnSessionTrack.pressesWithoutFrame());
        for (int i = 0; i < 18; i++) AgvnSessionTrack.onPress();
        AgvnSessionTrack.noteEnd(start + 88_000);
        AgvnSessionTrack.noteEnd(start + 90_000); // the player's exit after Wine's: the first end counts
        assertEquals(22, AgvnSessionTrack.frozenSecondsAtEnd());
        assertEquals(18, AgvnSessionTrack.pressesWithoutFrame());

        AgvnEvidence ev = new AgvnEvidence();
        ev.started = ev.endedByGame = true;
        ev.seconds = 88;
        ev.frozenS = AgvnSessionTrack.frozenSecondsAtEnd();
        ev.frozenPresses = AgvnSessionTrack.pressesWithoutFrame();
        assertTrue(ev.frozeAtEnd());
        assertFalse(ev.good());
        assertNull("nothing new to ask", found(ev));
        ev.frozenPresses = 2; // a static screen the player hardly touched
        assertTrue(ev.good());
        ev.frozenPresses = 18;
        ev.frozenS = 3; // its last frame just before a menu's "Thoát"
        assertTrue(ev.good());
        AgvnSessionTrack.start(start + 100_000);
        AgvnSessionTrack.onFrame(start + 101_000); // one frame through Present, then EGL or DisplayX
        for (int i = 0; i < 30; i++) AgvnSessionTrack.onPress();
        AgvnSessionTrack.noteEnd(start + 400_000);
        assertEquals("too few frames to judge", -1, AgvnSessionTrack.frozenSecondsAtEnd());
    }

    @Test
    public void aGameWhoseFileIsGone() {
        // Monster Black Market on a Mali-G615: Wine's box from winhandler.exe, then the game closed 7 s in;
        // 0.1.17 said "Game không chạy với cấu hình mới"
        AgvnEvidence ev = noStart("00cc:trace:msgbox:MSGBOX_OnInit L\"File not found.\\r\\n\"");
        assertEquals("file-not-found", found(ev));
        ev.changed = true;
        assertEquals("file-not-found", found(ev));
        assertEquals("only Wine's box", "no-start", found(noStart("File not found: save1.dat")));
    }

    @Test
    public void aFileWineCannotRun() {
        // Yarisutemesubuta Cheat02 on an Adreno 610: Wine's box at the first start, which 0.1.19 read as "no-start";
        // then its exe, a .NET program, with no Wine Mono yet: the first fix installs it
        assertEquals("bad-exe", found(noStart("00d0:trace:msgbox:MSGBOX_OnInit L\"Bad EXE format for \"")));
        assertEquals("dotnet", found(noStart("00e4:err:mscoree:CLRRuntimeInfo_GetRuntimeHost Wine Mono is not installed")));
        assertEquals("wine-mono", catalog.byId("dotnet").fixes.get(0));
        // Wine Mono there, but Wine could not load it or mscoree.dll: installing it again would not help
        assertEquals("dotnet-load", found(noStart("0110:err:mscoree:load_mono Could not load Mono into this process")));
        assertEquals("dotnet-load", found(noStart("0024:err:module:fixup_imports_ilonly mscoree.dll not found, "
                + "IL-only binary L\"Launcher.exe\" cannot be loaded")));
        assertFalse(catalog.byId("dotnet-load").fixes.contains("wine-mono"));
    }

    @Test
    public void androidEndingTheApp() {
        AgvnEvidence ev = new AgvnEvidence();
        ev.killedReason = AgvnEvidence.REASON_LOW_MEMORY;
        assertEquals("killed-low-memory", found(ev));
        ev.ramSaved = true; // it already started with the most RAM savings: Lg Light's last run
        assertEquals("killed-low-memory-saved", found(ev));
        assertFalse(catalog.find(ev).fixes().contains("quality-down"));
        ev.ramSaved = false;
        ev.killedReason = AgvnEvidence.REASON_SIGNALED;
        ev.killedImportance = 125; // in the background, kept by AGVN's notification
        assertEquals("killed-background", found(ev));
        ev.killedImportance = 100; // on screen: not a background clean-up
        assertNull(found(ev));
        ev.killedReason = 10; // the player swiped AGVN away
        assertNull(found(ev));
    }

    @Test
    public void aGameThatClosedAsRamRanOut() {
        // With The Devilish Her (Unity) on a Mali-G610 with 7.2 GB: Wine ended (code 0) at the same scene in every run,
        // without a crash or an out-of-memory line, 7-17 s after the RAM bar (641 and 525 MB free)
        long start = 1_000_000;
        AgvnSessionTrack.start(start);
        AgvnSessionTrack.memory(start + 160_000, 900, 737); // above the level of a 7.2 GB phone: not kept
        assertEquals(-1, AgvnSessionTrack.lowRamFreeMb(start + 165_000, AgvnDoctor.LOW_RAM_RECENT_MS));
        AgvnSessionTrack.memory(start + 165_000, 641, 737);
        assertEquals(641, AgvnSessionTrack.lowRamFreeMb(start + 172_000, AgvnDoctor.LOW_RAM_RECENT_MS));
        assertEquals("long before the end", -1, AgvnSessionTrack.lowRamFreeMb(start + 200_000, AgvnDoctor.LOW_RAM_RECENT_MS));
        AgvnSessionTrack.start(start + 300_000);
        assertEquals("a new game", -1, AgvnSessionTrack.lowRamFreeMb(start + 301_000, AgvnDoctor.LOW_RAM_RECENT_MS));

        AgvnEvidence ev = new AgvnEvidence();
        ev.started = ev.endedByGame = true;
        ev.seconds = 169;
        ev.lowRamFreeMb = 641;
        ev.params.put("free", "641");
        AgvnProblemCatalog.Finding f = catalog.find(ev);
        assertEquals("low-ram-end", f.id());
        assertTrue(f.cause(), f.cause().contains("chỉ còn trống 641 MB RAM"));
        assertEquals(Arrays.asList("restore-good", "quality-down", "send-logs"), f.fixes());
        ev.endedByGame = false;
        ev.playerQuit = true;
        assertNull("the player quit with AGVN's own button", found(ev));
        ev.playerQuit = false;
        ev.endedByGame = true;
        ev.lowRamFreeMb = -1;
        assertNull("RAM to spare: the game closed itself, from its menu", found(ev));
        ev.lowRamFreeMb = 641;
        ev.crashed = true;
        assertEquals("a crash names its own cause", "crash", found(ev));
    }

    @Test
    public void aGameThatRanOutOfRamWhileLoadingRanOutOfRam() {
        // Lg Light (Lifeguard Holic, Unity 6) on a Mali-G925 with 11.1 GB: it never showed its first scene, reached
        // 5.6-7.1 GB and closed 10-19 s after free RAM fell to 242 and 367 MB. 0.1.17 read two of those runs as
        // "no-start" and "low-resolution", whose fixes (another DXVK, a higher Đồ họa step) cannot help.
        AgvnEvidence ev = noStart();
        ev.seconds = 87;
        ev.lowRamFreeMb = 242;
        ev.params.put("free", "242");
        ev.params.put("screen", "854×480");
        assertEquals("low-ram-end", found(ev));
        ev.smallScreen = true; // 640x360, the screen it ran out of RAM on, was not refused
        ev.params.put("screen", "640×360");
        assertEquals("low-ram-end", found(ev));
        assertFalse(AgvnDoctor.refusedScreen("low-ram-end", false, Collections.emptyList(), "640x360", null));
        ev.engine = "GODOT";
        assertEquals("not \"godot-no-start\"", "low-ram-end", found(ev));
        ev.engine = "";
        ev.lines.add("err:   DxvkAdapter: Failed to create device");
        assertEquals("what a line names comes first", "directx", found(ev));
        ev.lines.clear();
        ev.crashed = true;
        assertEquals("a crash Wine printed names its own cause", "crash", found(ev));
        ev.crashed = false;

        ev.ramSaved = true; // every one of those runs had Siêu nhẹ's savings: 1600x900 to 640x360 changed nothing
        AgvnProblemCatalog.Finding f = catalog.find(ev);
        assertEquals("low-ram-saved", f.id());
        assertTrue(f.cause(), f.cause().contains("chỉ còn trống 242 MB RAM"));
        assertTrue(f.cause(), f.cause().contains("đã chạy với mức tiết kiệm RAM cao nhất"));
        assertEquals(Arrays.asList("restore-good", "send-logs"), f.fixes());
        assertTrue(AgvnMemorySaver.ranOutOfRam(f.id()));
        ev.lowRamFreeMb = -1;
        assertEquals("RAM to spare", "low-resolution", found(ev));
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
