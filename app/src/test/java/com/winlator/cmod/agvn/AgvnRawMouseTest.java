/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/** Touches as a real mouse (raw input) for Unity games with the Input System, or as "Tự sửa lỗi" sets a game. */
public class AgvnRawMouseTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File exe(String game) throws IOException {
        File dir = tmp.newFolder(game);
        File exe = new File(dir, game + ".exe");
        Files.write(exe.toPath(), "MZ".getBytes(StandardCharsets.US_ASCII));
        assertTrue(new File(dir, game + "_Data").mkdir());
        return exe;
    }

    @Test
    public void aUnityGameWithTheInputSystem() throws IOException {
        // Open At Nine: Mono, its assemblies in <game>_Data/Managed
        File mono = exe("OpenAtNine");
        File managed = new File(mono.getParentFile(), "OpenAtNine_Data/Managed");
        assertTrue(managed.mkdir());
        Files.write(new File(managed, "UnityEngine.dll").toPath(), new byte[4]);
        assertFalse(AgvnUnityInput.usesInputSystem(mono));
        Files.write(new File(managed, "Unity.InputSystem.dll").toPath(), new byte[4]);
        assertTrue(AgvnUnityInput.usesInputSystem(mono));
        // IL2CPP: the assembly's name in its metadata, here across two read blocks
        File il2cpp = exe("Vtuber");
        File metadata = new File(il2cpp.getParentFile(), "Vtuber_Data/il2cpp_data/Metadata");
        assertTrue(metadata.mkdirs());
        byte[] name = "Unity.InputSystem".getBytes(StandardCharsets.US_ASCII);
        byte[] data = new byte[(1 << 20) + 64];
        System.arraycopy(name, 0, data, (1 << 20) - 5, name.length);
        File meta = new File(metadata, "global-metadata.dat");
        Files.write(meta.toPath(), data);
        assertTrue(AgvnUnityInput.usesInputSystem(il2cpp));
        Files.write(meta.toPath(), "UnityEngine.InputModule UnityEngine.UI".getBytes(StandardCharsets.US_ASCII));
        assertFalse(AgvnUnityInput.usesInputSystem(il2cpp));
        // no <game>_Data: not a Unity game
        assertFalse(AgvnUnityInput.usesInputSystem(new File(tmp.getRoot(), "Game.exe")));
        assertFalse(AgvnUnityInput.usesInputSystem(null));
    }

    @Test
    public void theGamesOwnSettingWins() {
        assertTrue(AgvnRawMouse.wanted("", true));
        assertTrue(AgvnRawMouse.wanted(null, true));
        assertFalse(AgvnRawMouse.wanted("", false));
        assertTrue("Tự sửa lỗi turned it on", AgvnRawMouse.wanted("1", false));
        assertFalse("Tự sửa lỗi turned it off", AgvnRawMouse.wanted("0", true));
    }

    @Test
    public void agvnWinhandlerIsWrittenOnce() throws IOException {
        File windows = tmp.newFolder("windows");
        File target = new File(windows, AgvnRawMouse.EXE);
        byte[] exe = "MZ agvn".getBytes(StandardCharsets.US_ASCII);
        assertTrue(AgvnRawMouse.install(exe, target));
        assertArrayEquals(exe, Files.readAllBytes(target.toPath()));
        assertTrue(target.setLastModified(1000));
        assertTrue(AgvnRawMouse.install(exe, target));
        assertEquals("the same file is left alone", 1000, target.lastModified());
        byte[] newer = Arrays.copyOf(exe, exe.length + 1);
        assertTrue(AgvnRawMouse.install(newer, target));
        assertArrayEquals(newer, Files.readAllBytes(target.toPath()));
        assertFalse(AgvnRawMouse.install(null, target));
    }
}
