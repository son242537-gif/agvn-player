/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class AgvnLayoutsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static File touch(File f) throws IOException {
        f.getParentFile().mkdirs();
        assertTrue(f.createNewFile());
        return f;
    }

    @Test
    public void engineMapsToKind() {
        for (GameExeResolver.Engine e : Arrays.asList(GameExeResolver.Engine.RENPY, GameExeResolver.Engine.KIRIKIRI,
                GameExeResolver.Engine.TYRANO, GameExeResolver.Engine.SIGLUS, GameExeResolver.Engine.NSCRIPTER))
            assertEquals(e.name(), AgvnLayouts.VN, AgvnLayouts.kindFor(e));
        for (GameExeResolver.Engine e : Arrays.asList(GameExeResolver.Engine.RPGMAKER, GameExeResolver.Engine.RPGMAKER_MV,
                GameExeResolver.Engine.WOLFRPG))
            assertEquals(e.name(), AgvnLayouts.RPG, AgvnLayouts.kindFor(e));
        assertEquals(AgvnLayouts.TWO_D, AgvnLayouts.kindFor(GameExeResolver.Engine.GAMEMAKER));
        assertEquals(AgvnLayouts.TWO_D, AgvnLayouts.kindFor(GameExeResolver.Engine.GODOT));
        for (GameExeResolver.Engine e : Arrays.asList(GameExeResolver.Engine.UNREAL, GameExeResolver.Engine.UNITY,
                GameExeResolver.Engine.UNKNOWN))
            assertEquals(e.name(), AgvnLayouts.PC, AgvnLayouts.kindFor(e));
        assertEquals(AgvnLayouts.PC, AgvnLayouts.kindFor((GameExeResolver.Engine) null));
    }

    @Test
    public void profileOverridesEngine() throws Exception {
        AgvnProfile p = AgvnProfile.parse("{\"schemaVersion\":1,\"name\":\"g\",\"controls\":\"action\"}");
        assertEquals(AgvnLayouts.ACTION, AgvnLayouts.kindFor(p, GameExeResolver.Engine.RENPY));
        assertEquals(AgvnLayouts.VN, AgvnLayouts.kindFor(AgvnProfile.defaultFor("g"), GameExeResolver.Engine.RENPY));
        assertEquals(AgvnLayouts.RPG, AgvnLayouts.kindFor(null, GameExeResolver.Engine.WOLFRPG));
    }

    @Test
    public void reservedIds() {
        assertEquals(Arrays.asList("pc", "vn", "rpg", "2d", "action", "mouse"), AgvnLayouts.KINDS);
        for (int i = 0; i < AgvnLayouts.KINDS.size(); i++) {
            String kind = AgvnLayouts.KINDS.get(i);
            assertEquals(9000 + i, AgvnLayouts.idFor(kind));
            assertEquals(kind, AgvnLayouts.kindForId(9000 + i));
            assertTrue(AgvnLayouts.isAgvnId(String.valueOf(9000 + i)));
        }
        assertEquals(9000, AgvnLayouts.idFor("gamepad"));
        assertNull(AgvnLayouts.kindForId(9006));
        assertNull(AgvnLayouts.kindForId(4));
        assertTrue(AgvnLayouts.isAgvnId("900"));
        assertFalse(AgvnLayouts.isAgvnId("901"));
        assertFalse(AgvnLayouts.isAgvnId("0"));
        assertFalse(AgvnLayouts.isAgvnId("x"));
        assertTrue(AgvnLayouts.isKind("2d"));
        assertFalse(AgvnLayouts.isKind("VN"));
        assertFalse(AgvnLayouts.isKind(null));
    }

    @Test
    public void assignOnlyWhatAgvnOwns() {
        assertTrue(AgvnLayouts.shouldAssign(null, null));          // never chosen
        assertTrue(AgvnLayouts.shouldAssign("", null));
        assertTrue(AgvnLayouts.shouldAssign("900", null));         // legacy "AGVN Bàn phím"
        assertTrue(AgvnLayouts.shouldAssign("9001", "1"));         // picked by AGVN earlier
        assertFalse(AgvnLayouts.shouldAssign("0", null));          // player turned controls off
        assertFalse(AgvnLayouts.shouldAssign("0", "1"));
        assertFalse(AgvnLayouts.shouldAssign("9001", null));       // player picked the VN layout
        assertFalse(AgvnLayouts.shouldAssign("4", null));          // player's own profile
        assertFalse(AgvnLayouts.shouldAssign("4", "1"));           // stale flag never overrides a non-AGVN profile
    }

    @Test
    public void pickFallsBackToPc() {
        assertEquals("vn", AgvnControls.pick("vn", new HashSet<>(Arrays.asList("pc", "vn"))));
        assertEquals("pc", AgvnControls.pick("vn", new HashSet<>(Collections.singletonList("pc"))));
        assertNull(AgvnControls.pick("vn", new HashSet<>(Collections.singletonList("rpg"))));
    }

    @Test
    public void launchKindPrefersStoredKindThenFiles() throws Exception {
        File vn = tmp.newFolder("vn");
        File exe = touch(new File(vn, "game.exe"));
        touch(new File(vn, "data.xp3"));
        assertEquals("rpg", AgvnLayouts.launchKind("rpg", "RENPY", vn.getPath(), exe.getPath()));
        assertEquals("vn", AgvnLayouts.launchKind(null, "UNKNOWN", vn.getPath(), exe.getPath()));
        assertEquals("vn", AgvnLayouts.launchKind("bogus", null, null, exe.getPath()));
        // files win over an engine stored by an older build (Siglus used to be stored as GODOT)
        assertEquals("vn", AgvnLayouts.launchKind(null, "GODOT", vn.getPath(), null));
        File empty = tmp.newFolder("empty", "a", "b", "c", "d", "e");
        assertEquals("2d", AgvnLayouts.launchKind(null, "GAMEMAKER", null, new File(empty, "x.exe").getPath()));
        assertEquals("pc", AgvnLayouts.launchKind(null, "NOT_AN_ENGINE", null, null));
        assertEquals("pc", AgvnLayouts.launchKind(null, null, null, null));
    }

    @Test
    public void unrealShippingExeFoundThreeLevelsUp() throws Exception {
        File root = tmp.newFolder("AV");
        touch(new File(root, "AVDirectorLife.exe"));
        File shipping = touch(new File(root, "AVDirectorLife/Binaries/Win64/AVDirectorLife-Win64-Shipping.exe"));
        assertEquals(GameExeResolver.Engine.UNREAL, AgvnLayouts.detectAround(shipping.getParentFile()));
        // Ren'Py keeps its python builds in lib/<platform>
        File renpyLib = tmp.newFolder("rp", "lib", "py3-windows-x86_64");
        assertTrue(new File(tmp.getRoot(), "rp/renpy").mkdir());
        assertEquals(GameExeResolver.Engine.RENPY, AgvnLayouts.detectAround(renpyLib));
        File deep = tmp.newFolder("rm", "bin", "x64", "win64", "lib");
        touch(new File(tmp.getRoot(), "rm/Game.ini"));
        touch(new File(tmp.getRoot(), "rm/Game.rgss3a"));
        assertEquals(GameExeResolver.Engine.UNKNOWN, AgvnLayouts.detectAround(deep));             // 4 levels: too far
        assertEquals(GameExeResolver.Engine.RPGMAKER, AgvnLayouts.detectAround(deep.getParentFile()));
    }

    @Test
    public void sharedFoldersAboveTheGameFolderAreNotSearched() throws Exception {
        File download = tmp.newFolder("Download");
        touch(new File(download, "patch.xp3")); // a translation patch downloaded next to the games
        touch(new File(download, "mod.pck"));
        File game = tmp.newFolder("Download", "MyGame");
        File exe = touch(new File(game, "Game.exe"));
        assertEquals(GameExeResolver.Engine.UNKNOWN, AgvnLayouts.detectAround(game));
        assertEquals("pc", AgvnLayouts.launchKind(null, null, null, exe.getPath()));
        assertEquals("pc", AgvnLayouts.launchKind(null, null, game.getPath(), exe.getPath()));
        // bin -> Tool is climbed, Tool (a plain folder) is not
        assertEquals(GameExeResolver.Engine.UNKNOWN, AgvnLayouts.detectAround(tmp.newFolder("Download", "Tool", "bin")));
        // Unreal Project/Binaries/Win64 reaches the root only when the root has Engine/
        File win64 = tmp.newFolder("Download", "UE", "Proj", "Binaries", "Win64");
        touch(new File(win64, "Proj.exe"));
        assertEquals(GameExeResolver.Engine.UNKNOWN, AgvnLayouts.detectAround(win64));
        assertTrue(new File(tmp.getRoot(), "Download/UE/Engine/Binaries").mkdirs());
        assertEquals(GameExeResolver.Engine.UNREAL, AgvnLayouts.detectAround(win64));
        assertEquals(GameExeResolver.Engine.UNKNOWN, AgvnLayouts.detectAround(new File(tmp.getRoot(), "Download/UE/Proj")));
    }
}
