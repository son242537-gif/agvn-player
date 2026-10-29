/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.winlator.cmod.core.EnvVars;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AgvnAvProTest {
    private static final String NO_MANAGER = AgvnMediaFoundation.NO_DXGI_MANAGER;
    private static final Set<String> BOTH = new HashSet<>(Arrays.asList(AgvnAvPro.DLL, AgvnAvPro.WINRT_DLL));

    @Test
    public void findsAvProsPluginsBesideTheExe() throws Exception {
        File game = Files.createTempDirectory("avpro").toFile();
        File exe = new File(game, "main.exe");
        assertTrue(exe.createNewFile());
        assertTrue("a Unity game without AVPro", AgvnAvPro.dlls(exe).isEmpty());
        File plugins = new File(game, "main_Data/Plugins/x86_64");
        assertTrue(plugins.mkdirs());
        assertTrue(new File(plugins, "AVProVideo.dll").createNewFile());
        assertEquals(Collections.singleton(AgvnAvPro.DLL), AgvnAvPro.dlls(exe));
        assertTrue(new File(plugins, "AVProVideoWinRT.dll").createNewFile());
        assertTrue(new File(plugins, "AVProVideoDS.dll").createNewFile());
        assertEquals(BOTH, AgvnAvPro.dlls(exe));
        assertTrue("no folder", AgvnAvPro.dlls(new File("main.exe")).isEmpty());

        File old = Files.createTempDirectory("avpro-old").toFile();
        assertTrue(new File(old, "Game_Data/Plugins").mkdirs());
        assertTrue(new File(old, "Game_Data/Plugins/avprovideo.dll").createNewFile());
        assertEquals(Collections.singleton(AgvnAvPro.DLL), AgvnAvPro.dlls(new File(old, "Game.exe")));
    }

    @Test
    public void anAvProGameKeepsItsDxgiManagerAndLosesItsWinRtPlayer() {
        // NZGY (30/10/2026): no DXGI manager for hardware decoding, and the WinRT player crashed in kernelbase.dll
        EnvVars env = new EnvVars("WINEESYNC=1 WINEDLLOVERRIDES=winhttp=n,b");
        AgvnAvPro.apply(env, BOTH);
        assertFalse(env.has(NO_MANAGER));
        assertEquals("winhttp=n,b;avprovideowinrt=d", env.get("WINEDLLOVERRIDES"));

        EnvVars mine = new EnvVars(NO_MANAGER + "=1 WINEDLLOVERRIDES=AVProVideoWinRT=n");
        AgvnAvPro.apply(mine, BOTH);
        assertEquals("the player's own picks hold", "1", mine.get(NO_MANAGER));
        assertEquals("AVProVideoWinRT=n", mine.get("WINEDLLOVERRIDES"));

        EnvVars mfOnly = new EnvVars();
        AgvnAvPro.apply(mfOnly, Collections.singleton(AgvnAvPro.DLL));
        assertFalse(mfOnly.has(NO_MANAGER));
        assertFalse(mfOnly.has("WINEDLLOVERRIDES"));

        EnvVars other = new EnvVars();
        AgvnAvPro.apply(other, Collections.emptySet());
        assertEquals("every other game as before", "1", other.get(NO_MANAGER));
        assertFalse(other.has("WINEDLLOVERRIDES"));
    }
}
