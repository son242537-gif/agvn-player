/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class AgvnRunModeTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File rpgMakerMv() throws Exception {
        File dir = tmp.newFolder("MV Game");
        File www = new File(dir, "www");
        assertEquals(true, www.mkdirs());
        assertEquals(true, new File(www, "index.html").createNewFile());
        assertEquals(true, new File(dir, "Game.exe").createNewFile());
        return dir;
    }

    @Test
    public void aPageGameHasItsWayBackToLight() throws Exception {
        File dir = rpgMakerMv();
        AgvnRunMode.Light light = AgvnRunMode.lightFor(null, GameExeResolver.Engine.RPGMAKER_MV, dir, new File(dir, "Game.exe"));
        assertNotNull(light);
        assertEquals(AgvnHtmlGame.RUNNER_HTML, light.runner);
        assertEquals(new File(dir, "www/index.html").getAbsolutePath(), light.index);
    }

    @Test
    public void aWindowsOnlyGameHasNone() throws Exception {
        File dir = tmp.newFolder("Unity Game");
        assertNull(AgvnRunMode.lightFor(null, GameExeResolver.Engine.UNITY, dir, new File(dir, "Game.exe")));
        assertNull(AgvnRunMode.lightFor(null, GameExeResolver.Engine.UNITY, new File(dir, "gone"), null));
        assertNull(AgvnRunMode.lightFor(null, GameExeResolver.Engine.UNITY, null, null));
    }

    @Test
    public void aProfileThatSaysWindowsDecides() throws Exception {
        File dir = rpgMakerMv();
        AgvnProfile profile = AgvnProfile.defaultFor("MV Game");
        profile.runner = AgvnHtmlGame.RUNNER_WINE;
        assertNull(AgvnRunMode.lightFor(profile, GameExeResolver.Engine.RPGMAKER_MV, dir, new File(dir, "Game.exe")));
    }

    @Test
    public void anUnknownEngineNameIsNoEngine() {
        assertEquals(GameExeResolver.Engine.UNKNOWN, AgvnRunMode.engine(""));
        assertEquals(GameExeResolver.Engine.RENPY, AgvnRunMode.engine("RENPY"));
    }
}
