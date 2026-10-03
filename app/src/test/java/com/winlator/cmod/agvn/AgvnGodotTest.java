/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

/** Godot games: their version from the pack, their own log, and Zink failing to draw one (a Mali phone's log). */
public class AgvnGodotTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @BeforeClass
    public static void load() throws IOException {
        if (AgvnDoctorTest.catalog == null) AgvnDoctorTest.load();
    }

    /** A pack's start: "GDPC", the pack format, the engine's major, minor and patch, then the rest of the header. */
    private static byte[] pack(int format, int major, int minor, int patch) {
        ByteBuffer b = ByteBuffer.allocate(96).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(AgvnGodotFiles.MAGIC).putInt(format).putInt(major).putInt(minor).putInt(patch);
        return b.array();
    }

    @Test
    public void theVersionComesFromThePack() throws IOException {
        File game = tmp.newFolder("PARTY ME GAMEHUB");
        File exe = new File(game, "PartyMe.exe");
        Files.write(exe.toPath(), "MZ, not a pack".getBytes(StandardCharsets.US_ASCII));
        assertNull(AgvnGodotFiles.major(exe, game));
        Files.write(new File(game, "PartyMe.pck").toPath(), pack(2, 4, 3, 0));
        assertEquals("4", AgvnGodotFiles.major(exe, game));
        assertArrayEquals(new int[]{4, 3, 0}, AgvnGodotFiles.version(new File(game, "PartyMe.pck")));
        File old = tmp.newFolder("old");
        Files.write(new File(old, "data.pck").toPath(), pack(1, 3, 5, 2)); // not named after the exe: still read
        assertEquals("3", AgvnGodotFiles.major(new File(old, "Game.exe"), old));
        Files.write(new File(old, "Scene.pck").toPath(), "Siglus' own pack".getBytes(StandardCharsets.US_ASCII));
        assertNull(AgvnGodotFiles.version(new File(old, "Scene.pck")));
    }

    @Test
    public void aPackEmbeddedInTheExe() throws IOException {
        // Godot's "Embed PCK": the exe, the pack, the pack's size (64-bit) and "GDPC"
        byte[] exeCode = "MZ... the engine ...".getBytes(StandardCharsets.US_ASCII), pck = pack(3, 4, 4, 1);
        ByteBuffer b = ByteBuffer.allocate(exeCode.length + pck.length + 12).order(ByteOrder.LITTLE_ENDIAN);
        b.put(exeCode).put(pck).putLong(pck.length).putInt(AgvnGodotFiles.MAGIC);
        File exe = new File(tmp.getRoot(), "Embedded.exe");
        Files.write(exe.toPath(), b.array());
        assertArrayEquals(new int[]{4, 4, 1}, AgvnGodotFiles.version(exe));
        assertEquals("4", AgvnGodotFiles.major(exe, tmp.getRoot()));
        b.putLong(b.capacity() - 12, Long.MAX_VALUE); // a size bigger than the file
        Files.write(exe.toPath(), b.array());
        assertNull(AgvnGodotFiles.version(exe));
    }

    @Test
    public void theDoctorPrefersGodotsOwnWords() throws IOException {
        File game = tmp.newFolder("game");
        File exe = new File(game, "Game.exe");
        Files.write(new File(game, "Game.pck").toPath(), pack(2, 4, 2, 1));
        AgvnEvidence ev = new AgvnEvidence();
        ev.engine = "GODOT";
        assertEquals("4", AgvnDoctor.godotVersion(ev, exe, game));
        ev.lines.add("Godot Engine v3.5.2.stable.official.170ba337a - https://godotengine.org"); // from its godot.log
        assertEquals("3", AgvnDoctor.godotVersion(ev, exe, game));
        ev.engine = "UNITY";
        assertNull(AgvnDoctor.godotVersion(ev, exe, game));
    }

    @Test
    public void godotLogsOfThisSession() throws IOException {
        File roaming = tmp.newFolder("Roaming");
        File own = new File(roaming, "PartyMe/logs/godot.log"); // a custom user dir
        File other = new File(roaming, "Godot/app_userdata/Other Game/logs/godot.log");
        for (File f : Arrays.asList(own, other)) {
            assertTrue(f.getParentFile().mkdirs());
            Files.write(f.toPath(), "Godot Engine v4.3.stable.official.77dcf97d8".getBytes(StandardCharsets.US_ASCII));
        }
        long start = 1_790_000_000_000L;
        assertTrue(other.setLastModified(start - 3_600_000) && own.setLastModified(start + 1_000));
        assertEquals(Collections.singletonList(own), AgvnGodotFiles.logs(roaming, start));
        assertEquals(Arrays.asList(own, other), AgvnGodotFiles.logs(roaming, 0));
        assertTrue(AgvnGodotFiles.logs(new File(roaming, "missing"), 0).isEmpty());
    }

    @Test
    public void zinkFailingToDrawEndsTheGame() {
        // Party Me on a Mali-G615 MC2 (Android 16): it drew for a while, then Zink failed and the game was gone
        AgvnEvidence ev = new AgvnEvidence();
        ev.started = ev.endedByGame = true;
        ev.seconds = 47;
        ev.lines.addAll(Arrays.asList("WARNING: Some incorrect rendering might occur because the selected Vulkan device "
                        + "(Wrapper(Mali-G615 MC2)) doesn't support base Zink requirements: feats.features.logicOp "
                        + "feats.features.fillModeNonSolid feats.features.shaderClipDistance ",
                "MESA: warning: WARNING: Incorrect rendering will happen because the Vulkan device doesn't support the "
                        + "'VK_EXT_depth_clip_enable' feature",
                "MESA: error: ZINK: vkCreateGraphicsPipelines failed (VK_ERROR_INITIALIZATION_FAILED)"));
        ev.engine = "GODOT";
        AgvnProblemCatalog.Finding f = AgvnDoctorTest.catalog.find(ev);
        assertEquals("godot-gl-crash", f.id());
        assertEquals("[godot-renderer, godot-undo, driver-other, send-logs]", f.fixes().toString());
        ev.engine = "UNKNOWN";
        assertEquals("opengl-crash", AgvnDoctorTest.catalog.find(ev).id());
        ev.endedByGame = false; // the player left while it still drew: nothing to ask
        ev.playerQuit = true;
        assertNull(AgvnDoctorTest.catalog.find(ev));
    }
}
