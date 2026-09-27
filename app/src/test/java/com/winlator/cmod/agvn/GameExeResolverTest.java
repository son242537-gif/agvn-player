package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

public class GameExeResolverTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static void touch(File f) throws IOException {
        f.getParentFile().mkdirs();
        assertTrue(f.createNewFile());
    }

    @Test
    public void unrealPicksShipping() throws Exception {
        File dir = tmp.newFolder("ue");
        touch(new File(dir, "Game.exe"));
        touch(new File(dir, "Game/Binaries/Win64/Game-Win64-Shipping.exe"));
        touch(new File(dir, "Engine/Binaries/ThirdParty/x.dll"));
        assertEquals(GameExeResolver.Engine.UNREAL, GameExeResolver.detectEngine(dir));
        assertEquals("Game/Binaries/Win64/Game-Win64-Shipping.exe", GameExeResolver.resolveExe(dir, GameExeResolver.Engine.UNREAL));
    }

    @Test
    public void unityPicksExeMatchingDataFolder() throws Exception {
        File dir = tmp.newFolder("unity");
        touch(new File(dir, "UnityCrashHandler64.exe"));
        touch(new File(dir, "Launcher.exe"));
        touch(new File(dir, "Cool Game.exe"));
        touch(new File(dir, "UnityPlayer.dll"));
        touch(new File(dir, "Cool Game_Data/globalgamemanagers"));
        assertEquals(GameExeResolver.Engine.UNITY, GameExeResolver.detectEngine(dir));
        assertEquals("Cool Game.exe", GameExeResolver.resolveExe(dir, GameExeResolver.Engine.UNITY));
    }

    @Test
    public void genericSkipsInstallersAndLaunchers() throws Exception {
        File dir = tmp.newFolder("generic");
        touch(new File(dir, "unins000.exe"));
        touch(new File(dir, "GameLauncher.exe"));
        touch(new File(dir, "vc_redist.x64.exe"));
        touch(new File(dir, "game.exe"));
        touch(new File(dir, "data.win"));
        assertEquals(GameExeResolver.Engine.GAMEMAKER, GameExeResolver.detectEngine(dir));
        assertEquals("game.exe", GameExeResolver.resolveExe(dir, GameExeResolver.Engine.GAMEMAKER));
    }

    @Test
    public void emptyFolderHasNoExe() throws Exception {
        File dir = tmp.newFolder("empty");
        assertEquals(GameExeResolver.Engine.UNKNOWN, GameExeResolver.detectEngine(dir));
        assertNull(GameExeResolver.resolveExe(dir, GameExeResolver.Engine.UNKNOWN));
    }
}
