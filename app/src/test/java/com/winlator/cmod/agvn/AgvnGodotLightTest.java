/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/** "Chạy nhẹ" for Godot 4: which games Godot 4.7 for Android runs, read from their packs as Godot reads them. */
public class AgvnGodotLightTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** A GDScript kept as binary tokens of {@code version}: "GDSC", the version, then the tokens. */
    private static byte[] tokens(int version) {
        return ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(AgvnGodotPack.SCRIPT_MAGIC).putInt(version).array();
    }

    /**
     * A pack as Godot exports it: format 2 (Godot 4.0-4.4: directory after the header, paths with res://, file base
     * relative to the pack) or format 3 (4.5+: directory at the end, paths without res://). {@code flags[i]} per file.
     */
    private static byte[] pack(int format, int minor, String[] paths, byte[][] files, int[] flags) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        long[] offsets = new long[paths.length];
        for (int i = 0; i < paths.length; i++) {
            offsets[i] = data.size();
            data.write(files[i], 0, files[i].length);
        }
        ByteArrayOutputStream dir = new ByteArrayOutputStream();
        put(dir, le(4).putInt(paths.length));
        for (int i = 0; i < paths.length; i++) {
            byte[] name = ((format == 2 ? "res://" : "") + paths[i]).getBytes(StandardCharsets.UTF_8);
            int padded = (name.length + 3) / 4 * 4;
            put(dir, le(4).putInt(padded));
            dir.write(Arrays.copyOf(name, padded), 0, padded);
            put(dir, le(36).putLong(offsets[i]).putLong(files[i].length).put(new byte[16]).putInt(flags[i]));
        }
        int header = format == 2 ? 96 : 104;
        long fileBase = format == 2 ? header + dir.size() : header;
        ByteBuffer h = le(header).putInt(AgvnGodotFiles.MAGIC).putInt(format).putInt(4).putInt(minor).putInt(1)
                .putInt(format == 2 ? 2 : 0).putLong(fileBase); // format 2: PACK_REL_FILEBASE
        if (format == 3) h.putLong(header + data.size());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        put(out, h);
        if (format == 2) out.write(dir.toByteArray(), 0, dir.size());
        out.write(data.toByteArray(), 0, data.size());
        if (format == 3) out.write(dir.toByteArray(), 0, dir.size());
        return out.toByteArray();
    }

    private static ByteBuffer le(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static void put(ByteArrayOutputStream out, ByteBuffer b) {
        out.write(b.array(), 0, b.capacity());
    }

    private static byte[] game(int format, int minor, String script, byte[] code) {
        return pack(format, minor, new String[]{"project.binary", script}, new byte[][]{"ECFG".getBytes(StandardCharsets.US_ASCII), code},
                new int[]{0, 0});
    }

    private File write(String name, byte[] bytes) throws IOException {
        File f = new File(tmp.getRoot(), name);
        if (!f.getParentFile().isDirectory()) assertTrue(f.getParentFile().mkdirs());
        Files.write(f.toPath(), bytes);
        return f;
    }

    @Test
    public void partyMeRunsOnGodot47() throws IOException {
        // Godot 4.6, scripts as 4.5+ binary tokens, as Party Me's pack: its directory, version and scripts are read
        File pck = write("PartyMe.pck", game(3, 6, "main.gdc", tokens(101)));
        AgvnGodotPack p = AgvnGodotPack.open(pck);
        assertEquals(3, p.format);
        assertEquals(6, p.minor);
        assertEquals(Arrays.asList("project.binary", "main.gdc"), Arrays.asList(p.entries.get(0).path, p.entries.get(1).path));
        assertEquals(101, p.scriptTokens());
        assertEquals(AgvnGodotLight.Fit.OK, AgvnGodotLight.fit(p));
        assertTrue(AgvnGodotLight.useGodot(null, GameExeResolver.Engine.GODOT, write("PartyMe.exe", "MZ".getBytes(StandardCharsets.US_ASCII))));
        AgvnProfile wine = new AgvnProfile();
        wine.runner = AgvnHtmlGame.RUNNER_WINE;
        assertFalse(AgvnGodotLight.useGodot(wine, GameExeResolver.Engine.GODOT, new File(tmp.getRoot(), "PartyMe.exe")));
        assertFalse(AgvnGodotLight.useGodot(null, GameExeResolver.Engine.UNITY, new File(tmp.getRoot(), "PartyMe.exe")));
    }

    @Test
    public void whatGodot47CannotRun() throws IOException {
        // 4.3 and 4.4 kept binary tokens 100, which 4.5+ refuse; as text (.gd) they run
        assertEquals(AgvnGodotLight.Fit.OLD_SCRIPTS, AgvnGodotLight.fit(AgvnGodotPack.open(write("a.pck", game(2, 4, "main.gdc", tokens(100))))));
        assertEquals(AgvnGodotLight.Fit.OK, AgvnGodotLight.fit(AgvnGodotPack.open(write("b.pck", game(2, 2, "main.gd", "extends Node".getBytes())))));
        assertEquals(AgvnGodotLight.Fit.NEWER, AgvnGodotLight.fit(AgvnGodotPack.open(write("c.pck", game(3, 8, "main.gdc", tokens(101))))));
        File ext = write("d.pck", pack(3, 6, new String[]{".godot/extension_list.cfg", "main.gdc"},
                new byte[][]{"res://addons/godotsteam/godotsteam.gdextension".getBytes(), tokens(101)}, new int[]{0, 0}));
        assertEquals(AgvnGodotLight.Fit.EXTENSIONS, AgvnGodotLight.fit(AgvnGodotPack.open(ext)));
        File enc = write("e.pck", pack(3, 6, new String[]{"main.gdc"}, new byte[][]{tokens(101)}, new int[]{AgvnGodotPack.FILE_ENCRYPTED}));
        assertEquals(AgvnGodotLight.Fit.ENCRYPTED, AgvnGodotLight.fit(AgvnGodotPack.open(enc)));
        File cs = write("f.pck", pack(3, 6, new String[]{"Main.cs"}, new byte[][]{"class".getBytes()}, new int[]{0}));
        assertEquals(AgvnGodotLight.Fit.CSHARP, AgvnGodotLight.fit(AgvnGodotPack.open(cs)));
        File godot3 = write("g.pck", Arrays.copyOf(le(24).putInt(AgvnGodotFiles.MAGIC).putInt(1).putInt(3).putInt(5).array(), 120));
        assertEquals(AgvnGodotLight.Fit.NOT_GODOT_4, AgvnGodotLight.fit(AgvnGodotPack.open(godot3)));
        assertEquals(AgvnGodotLight.Fit.NO_PACK, AgvnGodotLight.fit(AgvnGodotPack.open(write("h.pck", "Siglus' own pack".getBytes()))));
    }

    @Test
    public void aDotNetGameKeepsItsDataBesideTheExe() throws IOException {
        write("DotNet/Game.pck", game(3, 6, "main.gdc", tokens(101)));
        write("DotNet/data_Game_windows_x86_64/GodotSharp.dll", "MZ".getBytes());
        assertEquals(AgvnGodotLight.Fit.CSHARP, AgvnGodotLight.fit(AgvnGodotPack.open(new File(tmp.getRoot(), "DotNet/Game.pck"))));
    }

    @Test
    public void theMainPackIsTheOneGodotForWindowsOpens() throws IOException {
        byte[] pck = game(3, 6, "main.gdc", tokens(101));
        // "Embed PCK": the engine, the pack, its size and "GDPC"; the pack starts after the engine
        byte[] engine = "MZ... Godot for Windows ...".getBytes(StandardCharsets.US_ASCII);
        ByteBuffer exe = le(engine.length + pck.length + 12).put(engine).put(pck).putLong(pck.length).putInt(AgvnGodotFiles.MAGIC);
        File one = write("One/Game.exe", exe.array());
        AgvnGodotPack embedded = AgvnGodotLight.mainPack(one);
        assertEquals(one, embedded.file);
        assertEquals(engine.length, embedded.start);
        assertEquals(101, embedded.scriptTokens());
        // beside the exe: <name>.pck, else <name>.exe.pck, else the only .pck (an exe renamed since)
        File exe2 = write("Two/Renamed.exe", "MZ".getBytes());
        write("Two/Original.pck", pck);
        assertEquals("Original.pck", AgvnGodotLight.mainPack(exe2).file.getName());
        write("Two/Renamed.exe.pck", pck);
        assertEquals("Renamed.exe.pck", AgvnGodotLight.mainPack(exe2).file.getName());
        write("Two/renamed.pck", pck); // any letter case, as on Windows
        // a file system that ignores case (Windows, Android's shared storage) gives the name as asked: Renamed.pck
        assertTrue("renamed.pck".equalsIgnoreCase(AgvnGodotLight.mainPack(exe2).file.getName()));
        File exe3 = write("Three/Game.exe", "MZ".getBytes());
        write("Three/a.pck", pck);
        write("Three/b.pck", pck);
        assertNull("two packs, neither named after the exe", AgvnGodotLight.mainPack(exe3));
        assertNull(AgvnGodotLight.mainPack(new File(tmp.getRoot(), "Missing/Game.exe")));
    }

    @Test
    public void godotsCommandLine() {
        File pck = new File("/storage/emulated/0/Games/PartyMe/PartyMe.pck"), log = new File("/sdcard/AGVN-Player/godot/PartyMe/godot.log");
        assertEquals(Arrays.asList("--main-pack", pck.getPath(), "--log-file", log.getPath(), "--fullscreen"),
                AgvnGodotLight.commandLine(pck, log, 0, null));
        assertEquals(Arrays.asList("--main-pack", pck.getPath(), "--log-file", log.getPath(), "--max-fps", "30",
                "--rendering-method", "gl_compatibility", "--rendering-driver", "opengl3", "--fullscreen"),
                AgvnGodotLight.commandLine(pck, log, 30, "gl"));
        assertEquals(new File("/sdcard/AGVN-Player/godot/PartyMe"),
                AgvnGodotLight.publicDir(new File("/sdcard/AGVN-Player"), new File("/x/PartyMe")));
    }

    @Test
    public void theRunnerIsLight() {
        assertTrue(AgvnHtmlGame.isValidRunner(AgvnHtmlGame.RUNNER_GODOT));
        assertTrue(AgvnHtmlGame.isLight(AgvnHtmlGame.RUNNER_GODOT));
        assertEquals("Godot", AgvnLightDoctor.runnerName(AgvnHtmlGame.RUNNER_GODOT));
    }

    @Test
    public void godotSaysWhyItStopped() {
        assertEquals("Cannot open resource pack '/storage/emulated/0/Games/X/X.pck'.", AgvnGodotFailure.reason(Arrays.asList(
                "Godot Engine v4.7.2.stable.custom_build - https://godotengine.org",
                "ERROR: Cannot open resource pack '/storage/emulated/0/Games/X/X.pck'.",
                "   at: _setup (core/config/project_settings.cpp:690)", "ERROR: a later error")));
        assertEquals("Parse Error: Identifier \"Steam\" not declared in the current scope.", AgvnGodotFailure.reason(Arrays.asList(
                "SCRIPT ERROR: Parse Error: Identifier \"Steam\" not declared in the current scope.")));
        assertEquals("", AgvnGodotFailure.reason(Arrays.asList("Godot Engine v4.7.2", "ERROR:   ")));
        assertEquals("", AgvnGodotFailure.reason(new File(tmp.getRoot(), "none.log").getPath()));
    }
}
