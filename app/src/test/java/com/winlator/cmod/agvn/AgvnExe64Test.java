/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;

/** A Ren'Py 7 game runs its 64-bit exe, not "-32.exe": Being Adik 0.12.1 on a Mali-G68, 17/10/2026. */
public class AgvnExe64Test {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** A PE file with no section for {@code machine}: 0x8664 (64-bit) or 0x14c (32-bit). */
    private static byte[] pe(int machine) {
        int peAt = 0x80, optSize = 0xf0;
        ByteBuffer b = ByteBuffer.allocate(0x400).order(ByteOrder.LITTLE_ENDIAN);
        b.put(0, (byte) 'M').put(1, (byte) 'Z').putInt(0x3c, peAt);
        b.put(peAt, (byte) 'P').put(peAt + 1, (byte) 'E');
        b.putShort(peAt + 4, (short) machine).putShort(peAt + 20, (short) optSize);
        b.putShort(peAt + 24, (short) (machine == 0x8664 ? 0x20b : 0x10b));
        return b.array();
    }

    /** A Ren'Py 7.4 PC build: "{@code name}.exe", "{@code name}-32.exe" and the lib folders named. */
    private File renpy(String folder, String name, int machine, String... libs) throws Exception {
        File dir = tmp.newFolder(folder);
        Files.write(new File(dir, name + ".exe").toPath(), pe(machine));
        Files.write(new File(dir, name + "-32.exe").toPath(), pe(0x14c));
        for (String lib : libs) assertTrue(new File(dir, "lib/" + lib).mkdirs());
        assertTrue(new File(dir, "game").mkdir());
        Files.write(new File(dir, "game/script.rpa").toPath(), new byte[]{1});
        return dir;
    }

    @Test
    public void aRenpyGameIsAddedWithItsSixtyFourBitExe() throws Exception {
        File dir = renpy("adik", "BeingADIK", 0x8664, "windows-i686", "windows-x86_64");
        assertEquals(GameExeResolver.Engine.RENPY, GameExeResolver.detectEngine(dir));
        assertEquals("BeingADIK.exe", GameExeResolver.resolveExe(dir, GameExeResolver.Engine.RENPY));
        assertEquals(new File(dir, "BeingADIK.exe"), AgvnExe64.twin(new File(dir, "BeingADIK-32.exe")));
        assertNull("the 64-bit exe has no twin", AgvnExe64.twin(new File(dir, "BeingADIK.exe")));
        assertEquals("not two games", Collections.singletonList("BeingADIK.exe"),
                GameExeResolver.gameExes(dir, GameExeResolver.Engine.UNKNOWN));
    }

    @Test
    public void theThirtyTwoBitExeStaysWhenItIsTheOnlyOneThatStarts() throws Exception {
        File old = renpy("old", "Game", 0x14c, "windows-i686");
        assertNull("both 32-bit", AgvnExe64.twin(new File(old, "Game-32.exe")));
        File cut = renpy("cut", "Game", 0x8664, "windows-i686");
        assertNull("no 64-bit Python beside it", AgvnExe64.twin(new File(cut, "Game-32.exe")));
        File py3 = renpy("py3", "Game", 0x8664, "py3-windows-x86_64");
        assertEquals(new File(py3, "Game.exe"), AgvnExe64.twin(new File(py3, "Game-32.exe")));
        assertNull(AgvnExe64.twin(new File(py3, "-32.exe")));
        assertNull("no twin on disk", AgvnExe64.twin(new File(py3, "Other-32.exe")));
    }

    @Test
    public void theLaunchStartsTheTwin() throws Exception {
        File dir = renpy("Being", "BeingADIK", 0x8664, "windows-x86_64");
        Map<String, String> drives = Collections.singletonMap("D", dir.getParent());
        assertEquals("\"D:\\Being\\BeingADIK.exe\" -x", AgvnExe64.apply("\"D:\\Being\\BeingADIK-32.exe\" -x", drives));
        assertEquals("Chạy bản 64-bit của game: BeingADIK.exe thay cho BeingADIK-32.exe (đã chọn trong Tự sửa lỗi)",
                AgvnExe64.takeNote());
        assertNull("once", AgvnExe64.takeNote());
        String own = "\"D:\\Being\\BeingADIK.exe\"";
        assertSame("already the 64-bit exe", own, AgvnExe64.apply(own, drives));
        assertNull(AgvnExe64.takeNote());
    }

    @Test
    public void aUnixPathToo() throws Exception {
        assumeTrue(File.separatorChar == '/'); // a Windows path is no unix one
        File dir = renpy("unix", "BeingADIK", 0x8664, "windows-x86_64");
        String unix = new File(dir, "BeingADIK-32.exe").getPath();
        assertEquals("\"" + new File(dir, "BeingADIK.exe").getPath() + "\"",
                AgvnExe64.apply(unix, Collections.<String, String>emptyMap()));
    }
}
