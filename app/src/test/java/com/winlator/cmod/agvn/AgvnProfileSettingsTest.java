/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class AgvnProfileSettingsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void onlyGameSettingsAreAllowed() throws Exception {
        AgvnProfile p = AgvnProfile.defaultFor("Game");
        p.settings = new LinkedHashMap<>();
        p.settings.put("dxwrapperConfig", "version=1.10.3,framerate=0,async=1");
        AgvnProfileSettings.validate(p);
        p.settings.put("graphicsDriver", "turnip"); // the phone's, never a profile's
        try {
            AgvnProfileSettings.validate(p);
            fail("a driver is not a profile's to set");
        } catch (AgvnProfileException expected) {}
        p.settings.remove("graphicsDriver");
        p.settings.put("audioDriver", "alsa; rm -rf");
        try {
            AgvnProfileSettings.validate(p);
            fail("unsafe value");
        } catch (AgvnProfileException expected) {}
    }

    @Test
    public void aGameIsWrittenOutAsItRunsNow() throws Exception {
        File dir = tmp.newFolder("My Game");
        File exe = new File(new File(dir, "bin"), "Game.exe");
        assertTrue(exe.getParentFile().mkdirs() && exe.createNewFile());
        Map<String, String> extras = new LinkedHashMap<>();
        extras.put(AgvnHtmlGame.EXTRA_RUNNER, AgvnHtmlGame.RUNNER_WINE);
        extras.put("lc_all", "ja_JP.UTF-8");
        extras.put("envVars", "WINE_OLD_MEDIA_SOURCE=1 WINEDLLOVERRIDES=winhttp=n,b");
        extras.put("execArgs", "-windowed  -nolog");
        extras.put("dxwrapper", "dxvk");
        extras.put("dxwrapperConfig", "version=1.10.3,async=1");
        extras.put("graphicsDriver", "turnip");
        AgvnProfile p = AgvnProfileSettings.export(AgvnProfile.defaultFor("My Game"), "My Game: Full", dir, exe, extras);
        assertEquals("My Game - Full", p.name);
        assertEquals("bin/Game.exe", p.exe);
        assertEquals(AgvnHtmlGame.RUNNER_WINE, p.runner);
        assertEquals("ja_JP.UTF-8", p.locale);
        assertEquals("1", p.env.get("WINE_OLD_MEDIA_SOURCE"));
        assertFalse(p.env.containsKey("WINEDLLOVERRIDES"));
        assertEquals(java.util.Arrays.asList("-windowed", "-nolog"), p.args);
        assertEquals("version=1.10.3,async=1", p.settings.get("dxwrapperConfig"));
        assertFalse(p.settings.containsKey("graphicsDriver"));
        // what was written is a profile the app takes back
        AgvnProfile back = AgvnProfile.parse(p.toPrettyJson());
        assertEquals("bin/Game.exe", AgvnProfileValidator.validate(back, dir));
        assertEquals("dxvk", back.settings.get("dxwrapper"));
    }

    @Test
    public void anExeOutsideTheFolderKeepsTheProfilesOwn() throws Exception {
        File dir = tmp.newFolder("G");
        assertNull(AgvnProfileSettings.relative(dir, new File(tmp.getRoot(), "Other.exe")));
        assertNull(AgvnProfileSettings.relative(dir, null));
    }
}
