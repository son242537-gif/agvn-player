/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

/**
 * A game's error box, as Wine 9 traces it (trace+msgbox, dlls/user32/msgbox.c MSGBOX_OnInit): the box's text through
 * debugstr_w, which escapes it and cuts it after about 290 characters. The texts are the players' screenshots of 04/10.
 */
public class AgvnErrorBoxTest {
    /** Wine's C runtime turning a Vulkan driver crash in winevulkan's thunk into an assertion (ucrtbase _wassert). */
    static final String VULKAN_ASSERT = "Assertion failed!\n\nProgram: D:\\game\\blabla\\ROOM 404 EROTIC MASSAGE\\ROOM 404 "
            + "EROTIC MASSAGE\\Massage\\Binaries\\Win64\\Massage-Win64-Shipping.exe\nFile: ../dlls/winevulkan/loader_thunks.c\n"
            + "Line: 3275\n\nExpression: \"!status && \"vkCreateShaderModule\"\"\n\n"
            + "Press OK to exit the program, or Cancel to start the Wine debugger.\n";
    /** Godot 4.4 to 4.6 when ANGLE (Direct3D 11) and native OpenGL both failed (display_server_windows.cpp create_func). */
    static final String GODOT_GL = "Your video card drivers seem not to support the required OpenGL 3.3 or Direct3D 11 version.\n\n"
            + "If possible, consider updating your video card drivers.\n\n"
            + "If you have recently updated your video card drivers, try rebooting.";
    /** Unreal Engine 4 and 5 when Direct3D 11 gives less than 11.0 (D3D11RHI's RequiredDX11Feature_11_SM5), 09/10. */
    static final String UNREAL_D3D11 = "A D3D11-compatible GPU (Feature Level 11.0, Shader Model 5.0) is required to run the engine.";

    @BeforeClass
    public static void load() throws IOException {
        if (AgvnDoctorTest.catalog == null) AgvnDoctorTest.load();
    }

    /** {@code text} as Wine prints a message box: "0128:trace:msgbox:MSGBOX_OnInit L\"...\"", escaped and cut like debugstr_w. */
    static String traced(String text) {
        StringBuilder out = new StringBuilder("L\"");
        int i = 0;
        for (; i < text.length() && out.length() <= 290; i++) {
            char c = text.charAt(i);
            if (c == '\n') out.append("\\n");
            else if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < ' ' || c >= 127) out.append(String.format("\\%04x", (int) c));
            else out.append(c);
        }
        out.append('"');
        if (i < text.length()) out.append("...");
        return "0128:trace:msgbox:MSGBOX_OnInit " + out;
    }

    private static AgvnEvidence box(String engine, String text) {
        AgvnEvidence ev = new AgvnEvidence();
        ev.engine = engine;
        ev.started = ev.endedByGame = true; // it ran, then the box came and OK closed it
        ev.seconds = 95;
        ev.lines.add(traced(text));
        return ev;
    }

    @Test
    public void aVulkanDriverCrashIsNamed() {
        AgvnEvidence ev = box("UNREAL", VULKAN_ASSERT);
        assertTrue(ev.lines.get(0), ev.lines.get(0).contains("Expression: \\\"!status && \\\"vkCreateShaderModule\\\"\\\""));
        AgvnProblemCatalog.Finding f = AgvnDoctorTest.catalog.find(ev);
        assertEquals("vulkan-crash", f.id());
        assertEquals("vkCreateShaderModule", f.params.get("1"));
        assertEquals(Arrays.asList("wrapper-constants", "wrapper-clip", "dxvk-other", "driver-other", "wine-old", "wined3d",
                "send-logs"), f.fixes());
        ev.engine = "GODOT";
        assertEquals("godot-vulkan-crash", AgvnDoctorTest.catalog.find(ev).id());
        ev.endedByGame = false; // the player closed the game behind the box: the box still said it
        ev.playerQuit = true;
        assertEquals("godot-vulkan-crash", AgvnDoctorTest.catalog.find(ev).id());
    }

    @Test
    public void aLongPathCutsTheExpressionNotTheFile() {
        String deep = VULKAN_ASSERT.replace("blabla", "Game Việt hoá\\Những game hay nhất\\bản mới");
        String line = traced(deep);
        assertTrue(line, line.endsWith("\"...") && !line.contains("vkCreateShaderModule") && line.contains("loader_thunks.c"));
        assertEquals("vulkan-crash", AgvnDoctorTest.catalog.find(box("UNKNOWN", deep)).id());
    }

    @Test
    public void aLongerPathCutsTheCallsName() {
        String deeper = VULKAN_ASSERT.replace("blabla", "blabla\\Game hay nhất\\Unreal Engine 4");
        AgvnProblemCatalog.Finding f = AgvnDoctorTest.catalog.find(box("UNREAL", deeper));
        assertEquals("vulkan-crash", f.id());
        assertEquals("vkCreateShaderModu", f.params.get("1"));
        assertTrue("the Mali shader steps are still offered", AgvnWrapperPasses.creatingShader(f.params.get("1")));
    }

    @Test
    public void godotSaysWhichRenderersFailed() {
        AgvnEvidence ev = box("GODOT", GODOT_GL);
        ev.started = false; // it never drew
        ev.seconds = 9;
        assertEquals("godot-gl-angle", AgvnDoctorTest.catalog.find(ev).id());
        assertTrue("an unknown game that says this is Godot's", AgvnGodotGame.saysGodot(ev.lines));
        ev.lines.set(0, traced(GODOT_GL.replace(" or Direct3D 11", ""))); // Godot 4.0 to 4.3: OpenGL only
        assertEquals("godot-opengl", AgvnDoctorTest.catalog.find(ev).id());
        ev.lines.set(0, "ERROR: Could not initialize native OpenGL."); // Godot 4.6's own log
        assertEquals("godot-opengl", AgvnDoctorTest.catalog.find(ev).id());
        AgvnEvidence vulkan = box("GODOT", "Your video card drivers seem not to support the required Vulkan or Direct3D 12 "
                + "version.\n\nIf possible, consider updating your video card drivers or using the OpenGL 3 driver.");
        vulkan.started = false;
        assertEquals("godot-vulkan", AgvnDoctorTest.catalog.find(vulkan).id());
        assertFalse(AgvnGodotGame.saysGodot(Collections.singletonList(traced(VULKAN_ASSERT))));
    }

    @Test
    public void unrealSaysDirectX11IsTooLow() {
        AgvnEvidence ev = box("UNREAL", UNREAL_D3D11);
        ev.started = false; // the box comes before the game's window
        ev.seconds = 30;
        ev.lines.add(0, "err:   D3D11CoreCreateDevice: Minimum required feature level D3D_FEATURE_LEVEL_11_0 not supported");
        AgvnProblemCatalog.Finding f = AgvnDoctorTest.catalog.find(ev);
        assertEquals("d3d11-level", f.id());
        assertEquals("Game cần DirectX 11 mức 11.0", f.title());
        assertEquals("from WineD3D back to DXVK first; another DXVK needs the same features",
                Arrays.asList("dxvk-back", "driver-other", "send-logs"), f.fixes());
        ev.endedByGame = false; // swiped away behind the box: the box still said it
        assertEquals("d3d11-level", AgvnDoctorTest.catalog.find(ev).id());
        ev.lines.set(1, traced("DX11 feature level 10.0 is required to run the engine.")); // Unreal 4.0 to 4.2x
        assertEquals("Game cần DirectX 11 mức 10.0", AgvnDoctorTest.catalog.find(ev).title());
    }

    @Test
    public void unrealOnWineD3dWithoutZinkIsAnOpenGlProblem() {
        // Support Pregnancy School on a Galaxy M34 (Mali-G68, Vulkan 1.1), 09/10 13:06: "Tự sửa lỗi" tried WineD3D for
        // a black screen, Zink did not start, and Unreal showed its box (the maintainer's screenshot)
        AgvnEvidence ev = box("UNREAL", UNREAL_D3D11);
        ev.lines.addAll(0, Arrays.asList("WARNING: Some incorrect rendering might occur because the selected Vulkan device "
                + "(Wrapper(Mali-G68)) doesn't support base Zink requirements: feats.features.logicOp "
                + "feats.features.fillModeNonSolid feats.features.shaderClipDistance ", "glx: failed to create drisw screen",
                "failed to load driver: zink"));
        ev.started = false;
        assertTrue("kept whatever the end is named: no more WineD3D with this driver", AgvnOpenGlCheck.zinkFailed(ev.lines));
        assertEquals(AgvnOpenGlCheck.PROBLEM, AgvnDoctorTest.catalog.find(ev).id());
        ev.endedByGame = false; // the player quit with the box up, after the game's window had shown
        ev.playerQuit = ev.bigWindowSeen = true;
        assertEquals("d3d11-level", AgvnDoctorTest.catalog.find(ev).id());
        assertFalse(AgvnOpenGlCheck.zinkFailed(Collections.singletonList("Zink: logicOp missing")));
    }

    @Test
    public void anOrdinaryBoxSaysNothing() {
        AgvnEvidence ev = box("UNITY", "Do you want to save your game before quitting?");
        ev.endedByGame = false;
        ev.playerQuit = true;
        assertNull(AgvnDoctorTest.catalog.find(ev));
    }
}
