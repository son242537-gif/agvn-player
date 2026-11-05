/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

/** agvn-profile.json's env and dllOverrides, edited after the game was added (SRPG Studio, 06/11/2026). */
public class AgvnProfileEnvTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void whatTheProfileGivesIsItsEnvAndItsOverrides() throws Exception {
        AgvnProfile p = AgvnProfile.parse("{\"schemaVersion\":1,\"name\":\"G\",\"env\":{\"WINEDEBUG\":\"+msgbox\","
                + "\"DXVK_FRAME_RATE\":\"30\"},\"dllOverrides\":{\"jscript.dll\":\"n,b\"}}");
        assertEquals("the FPS cap stays the in-game one's", "WINEDLLOVERRIDES=jscript=n,b WINEDEBUG=+msgbox",
                AgvnProfileEnv.given(p));
        assertEquals("", AgvnProfileEnv.given(AgvnProfile.parse("{\"schemaVersion\":1,\"name\":\"G\"}")));
    }

    @Test
    public void anEditReplacesWhatTheProfileGaveAndKeepsTheRest() {
        // at import: WINEDEBUG=-all from the profile; a Doctor fix then added DXVK_ASYNC, the game folder a mod DLL
        String env = "WINEDLLOVERRIDES=winmm=n,b WINEDEBUG=-all DXVK_ASYNC=1";
        String now = "WINEDLLOVERRIDES=jscript=n,b WINEDEBUG=+msgbox,+loaddll";
        assertEquals("WINEDLLOVERRIDES=winmm=n,b;jscript=n,b WINEDEBUG=+msgbox,+loaddll DXVK_ASYNC=1",
                AgvnProfileEnv.apply(env, "WINEDEBUG=-all", now));
        assertEquals("a variable the profile no longer has goes", "DXVK_ASYNC=1",
                AgvnProfileEnv.apply("WINEDEBUG=-all DXVK_ASYNC=1", "WINEDEBUG=-all", ""));
        assertEquals("unless the player changed it since", "WINEDEBUG=+seh DXVK_ASYNC=1",
                AgvnProfileEnv.apply("WINEDEBUG=+seh DXVK_ASYNC=1", "WINEDEBUG=-all", ""));
        assertEquals("a game added before 0.1.81 has no record: the profile's are put", "A=1 WINEDEBUG=+msgbox",
                AgvnProfileEnv.apply("A=1", "", "WINEDEBUG=+msgbox"));
    }

    @Test
    public void overridesTheProfileDroppedGoAndOthersStay() {
        assertEquals("winmm=n,b;d3d10=b", AgvnProfileEnv.overrides("jscript=n,b;winmm=n,b", "jscript=n,b", "d3d10=b"));
        assertEquals("a mode the player changed stays", "jscript=b",
                AgvnProfileEnv.overrides("jscript=b", "jscript=n,b", ""));
        assertEquals("names listed together are one each", "d3d10=n;d3d10core=b;dxgi=n",
                AgvnProfileEnv.overrides("d3d10,d3d10core=n;dxgi=n", "", "D3D10CORE=b"));
        assertEquals("", AgvnProfileEnv.apply("WINEDLLOVERRIDES=jscript=n,b", "WINEDLLOVERRIDES=jscript=n,b", ""));
    }

    @Test
    public void theProfileEditedLastIsRead() throws Exception {
        File game = tmp.newFile("in-game.json"), copy = tmp.newFile("copy.json");
        assertSame(game, AgvnProfileEnv.edited(game, null));
        assertSame(copy, AgvnProfileEnv.edited(new File(tmp.getRoot(), "none.json"), copy));
        assertNull(AgvnProfileEnv.edited(null, null));
        Files.setLastModifiedTime(copy.toPath(), java.nio.file.attribute.FileTime.fromMillis(1_000_000L));
        Files.setLastModifiedTime(game.toPath(), java.nio.file.attribute.FileTime.fromMillis(2_000_000L));
        assertSame("the game folder's, edited after the game was added", game, AgvnProfileEnv.edited(game, copy));
        Files.setLastModifiedTime(copy.toPath(), java.nio.file.attribute.FileTime.fromMillis(3_000_000L));
        assertSame(copy, AgvnProfileEnv.edited(game, copy));
    }
}
