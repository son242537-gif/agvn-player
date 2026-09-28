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

    /** A game folder with the given files; a path ending in '/' is created as an empty directory. */
    private File game(String name, String... paths) throws IOException {
        File dir = tmp.newFolder(name);
        for (String p : paths) {
            File f = new File(dir, p);
            if (p.endsWith("/")) assertTrue(f.mkdirs());
            else touch(f);
        }
        return dir;
    }

    private static void assertEngine(GameExeResolver.Engine expected, File dir) {
        assertEquals(dir.getName(), expected, GameExeResolver.detectEngine(dir));
    }

    @Test
    public void siglusIsNotGodot() throws Exception {
        File siglus = game("siglus", "SiglusEngine.exe", "Scene.pck", "Gameexe.dat", "g00/");
        assertEngine(GameExeResolver.Engine.SIGLUS, siglus);
        assertEngine(GameExeResolver.Engine.SIGLUS, game("siglus2", "Game.exe", "Gameexe.dat"));
        assertEngine(GameExeResolver.Engine.GODOT, game("godot", "Game.exe", "Game.pck"));
        assertEquals("SiglusEngine.exe", GameExeResolver.resolveExe(siglus, GameExeResolver.Engine.SIGLUS));
    }

    @Test
    public void rpgMakerFamilies() throws Exception {
        File mv = game("mv", "Game.exe", "notification_helper.exe", "nw.dll", "www/js/rpg_core.js");
        assertEngine(GameExeResolver.Engine.RPGMAKER_MV, mv);
        assertEquals("Game.exe", GameExeResolver.resolveExe(mv, GameExeResolver.Engine.RPGMAKER_MV));
        assertEngine(GameExeResolver.Engine.RPGMAKER_MV, game("mz", "Game.exe", "js/rmmz_core.js"));
        assertEngine(GameExeResolver.Engine.RPGMAKER, game("vxace", "Game.exe", "Game.ini", "Game.rgss3a", "System/RGSS301.dll"));
        assertEngine(GameExeResolver.Engine.RPGMAKER, game("xp", "Game.exe", "Game.ini", "RGSS104E.dll"));
        assertEngine(GameExeResolver.Engine.RPGMAKER, game("vx", "Game.exe", "Game.ini", "Data/Map001.rvdata"));
        assertEngine(GameExeResolver.Engine.RPGMAKER, game("rm2k3", "RPG_RT.exe", "RPG_RT.ldb", "RPG_RT.ini"));
        assertEngine(GameExeResolver.Engine.UNKNOWN, game("iniOnly", "Game.exe", "Game.ini"));
    }

    @Test
    public void wolfRpgSkipsConfigExe() throws Exception {
        File wolf = game("wolf", "Config.exe", "Game.exe", "Data.wolf", "GuruguruSMF4.dll");
        assertEngine(GameExeResolver.Engine.WOLFRPG, wolf);
        assertEquals("Game.exe", GameExeResolver.resolveExe(wolf, GameExeResolver.Engine.WOLFRPG));
        assertEngine(GameExeResolver.Engine.WOLFRPG, game("wolf2", "Game.exe", "Data/BasicData.wolf"));
        assertEngine(GameExeResolver.Engine.WOLFRPG, game("wolf3", "Game.exe", "GuruguruSMF4.dll", "Data/BasicData/"));
    }

    @Test
    public void visualNovelEngines() throws Exception {
        assertEngine(GameExeResolver.Engine.KIRIKIRI, game("krkr", "game.exe", "data.xp3", "plugin/wuvorbis.dll"));
        assertEngine(GameExeResolver.Engine.NSCRIPTER, game("ons", "nscr.exe", "arc.nsa", "0.txt"));
        assertEngine(GameExeResolver.Engine.NSCRIPTER, game("ons2", "nscr.exe", "nscript.dat"));
        assertEngine(GameExeResolver.Engine.TYRANO, game("tyrano", "Game.exe", "index.html", "tyrano/libs.js"));
        // folder names differ in case on Windows copies: data/system/Config.tjs is matched case-insensitively
        assertEngine(GameExeResolver.Engine.TYRANO, game("tyrano2", "Game.exe", "Data/System/Config.tjs"));
        assertEngine(GameExeResolver.Engine.RENPY, game("renpy", "Game.exe", "renpy/", "lib/"));
        assertEngine(GameExeResolver.Engine.RENPY, game("renpy2", "Game.exe", "game/archive.rpa"));
        assertEngine(GameExeResolver.Engine.RENPY, game("renpy3", "Game.exe", "game/script.rpyc"));
        assertEngine(GameExeResolver.Engine.UNKNOWN, game("plain", "Game.exe", "game/readme.txt"));
    }

    @Test
    public void knownEnginesNeedNoTopLevelDll() throws Exception {
        assertTrue(AgvnGameScanner.isGameDir(game("krkrScan", "game.exe", "data.xp3", "plugin/krmovie.dll")));
        assertTrue(AgvnGameScanner.isGameDir(game("aceScan", "Game.exe", "Game.ini", "Game.rgss3a", "System/RGSS301.dll")));
    }

    @Test
    public void emptyFolderHasNoExe() throws Exception {
        File dir = tmp.newFolder("empty");
        assertEquals(GameExeResolver.Engine.UNKNOWN, GameExeResolver.detectEngine(dir));
        assertNull(GameExeResolver.resolveExe(dir, GameExeResolver.Engine.UNKNOWN));
    }
}
