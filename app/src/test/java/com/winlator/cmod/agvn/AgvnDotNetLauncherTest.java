/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

/**
 * A GameMaker game whose folder also holds a small .NET launcher, "Choi Game.exe" (POCO, 24/10/2026): it is added with
 * the GameMaker exe, and a game added with the launcher is offered that exe; its 30 FPS start limit slowed it by half.
 */
public class AgvnDotNetLauncherTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** A PE32 file; {@code clr}: with a CLR header, as a C# compiler writes it. */
    private static byte[] pe(boolean clr) {
        int peAt = 0x80, dirs = 96, optSize = dirs + 16 * 8;
        ByteBuffer b = ByteBuffer.allocate(0x400).order(ByteOrder.LITTLE_ENDIAN);
        b.put(0, (byte) 'M').put(1, (byte) 'Z').putInt(0x3c, peAt);
        b.put(peAt, (byte) 'P').put(peAt + 1, (byte) 'E');
        b.putShort(peAt + 4, (short) 0x14c).putShort(peAt + 20, (short) optSize);
        b.putShort(peAt + 24, (short) 0x10b).putInt(peAt + 24 + dirs - 4, 16);
        if (clr) b.putInt(peAt + 24 + dirs + 14 * 8, 0x2008).putInt(peAt + 24 + dirs + 14 * 8 + 4, 0x48);
        return b.array();
    }

    private static File write(File dir, String name, byte[] data) throws Exception {
        File f = new File(dir, name);
        Files.write(f.toPath(), data);
        return f;
    }

    /** "Choi Game.exe" (.NET) sorts before "TenGame.exe" (GameMaker's runner), beside data.win. */
    private File gameMaker() throws Exception {
        File dir = tmp.newFolder("gm");
        write(dir, "Choi Game.exe", pe(true));
        write(dir, "TenGame.exe", pe(false));
        write(dir, "data.win", new byte[]{'F', 'O', 'R', 'M'});
        write(dir, "options.ini", new byte[0]);
        return dir;
    }

    @Test
    public void aGameMakerGameIsAddedWithItsOwnExe() throws Exception {
        File dir = gameMaker();
        assertTrue(AgvnWineMono.isDotNet(new File(dir, "Choi Game.exe")));
        assertEquals(GameExeResolver.Engine.GAMEMAKER, GameExeResolver.detectEngine(dir));
        assertEquals("TenGame.exe", GameExeResolver.resolveExe(dir, GameExeResolver.Engine.GAMEMAKER));
    }

    @Test
    public void aDotNetGameOfAnEngineAgvnDoesNotKnowStays() throws Exception {
        File dir = tmp.newFolder("xna"); // an XNA or FNA game: its exe is the .NET program
        write(dir, "Game.exe", pe(true));
        write(dir, "Other.exe", pe(false));
        assertEquals("Game.exe", GameExeResolver.resolveExe(dir, GameExeResolver.Engine.UNKNOWN));
        assertNull(AgvnExeMain.main(new File(dir, "Game.exe"), GameExeResolver.Engine.UNKNOWN.name()));
    }

    @Test
    public void aGameAddedWithTheLauncherIsOfferedTheGamesExe() throws Exception {
        File dir = gameMaker();
        File launcher = new File(dir, "Choi Game.exe");
        assertEquals(new File(dir, "TenGame.exe"), AgvnExeMain.main(launcher, GameExeResolver.Engine.GAMEMAKER.name()));
        assertEquals("an engine not kept yet is looked for", new File(dir, "TenGame.exe"), AgvnExeMain.main(launcher, ""));
        assertNull("the game's own exe", AgvnExeMain.main(new File(dir, "TenGame.exe"), GameExeResolver.Engine.GAMEMAKER.name()));
    }

    @Test
    public void aGameThatRunsAStepPerFrameIsNotSlowedByTheStartLimit() {
        assertEquals(60, AgvnFrameLock.fps(GameExeResolver.Engine.GAMEMAKER.name(), 30));
        assertEquals(60, AgvnFrameLock.fps(GameExeResolver.Engine.WOLFRPG.name(), 24));
        assertEquals("no limit stays none", 0, AgvnFrameLock.fps(GameExeResolver.Engine.GAMEMAKER.name(), 0));
        assertEquals(90, AgvnFrameLock.fps(GameExeResolver.Engine.GAMEMAKER.name(), 90));
        assertEquals("time-based engines keep the phone's limit", 30, AgvnFrameLock.fps(GameExeResolver.Engine.UNITY.name(), 30));
        assertEquals(24, AgvnFrameLock.fps("", 24));
    }
}
