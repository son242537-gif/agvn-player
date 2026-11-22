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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * Idol Ikusei Simulator (Magine, DirectX 12 only, POCO F8 Pro, 22/11/2026) closed with "gpu_unsupported" on Wine's own
 * d3d12.dll and ran on VKD3D-Proton 2.14.1.
 */
public class AgvnVkd3dTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** A 32-bit PE file whose import table names {@code first} and KERNEL32.dll (as AgvnPeFactsTest's). */
    private static byte[] pe(String first) {
        int peAt = 0x80, optSize = 0xe0, table = peAt + 24 + optSize;
        ByteBuffer b = ByteBuffer.allocate(0x800).order(ByteOrder.LITTLE_ENDIAN);
        b.put(0, (byte) 'M').put(1, (byte) 'Z').putInt(0x3c, peAt);
        b.put(peAt, (byte) 'P').put(peAt + 1, (byte) 'E');
        b.putShort(peAt + 4, (short) 0x14c).putShort(peAt + 6, (short) 2).putShort(peAt + 20, (short) optSize);
        b.putShort(peAt + 24, (short) 0x10b).putInt(peAt + 24 + 28, 0x400000);
        b.putInt(peAt + 24 + 96 + 8, 0x2000).putInt(peAt + 24 + 96 + 12, 60);
        section(b, table, ".text", 0x1000, 0x400);
        section(b, table + 40, ".rdata", 0x2000, 0x600);
        b.putInt(0x600 + 12, 0x2100).putInt(0x600 + 20 + 12, 0x2110);
        b.position(0x700);
        b.put((first + "\0").getBytes(StandardCharsets.US_ASCII));
        b.position(0x710);
        b.put("KERNEL32.dll\0".getBytes(StandardCharsets.US_ASCII));
        return b.array();
    }

    private static void section(ByteBuffer b, int at, String name, int va, int raw) {
        byte[] n = Arrays.copyOf(name.getBytes(StandardCharsets.US_ASCII), 8);
        for (int i = 0; i < 8; i++) b.put(at + i, n[i]);
        b.putInt(at + 8, 0x200).putInt(at + 12, va).putInt(at + 16, 0x200).putInt(at + 20, raw);
    }

    private File write(File dir, String name, byte[] data) throws Exception {
        File f = new File(dir, name);
        Files.write(f.toPath(), data);
        return f;
    }

    @Test
    public void aMagineGameNeedsDirectX12() {
        assertEquals("engine Magine", AgvnVkd3d.why("MAGINE", null));
    }

    @Test
    public void aGameThatImportsOnlyDirectX12NeedsIt() throws Exception {
        File dir = tmp.newFolder("dx12");
        File exe = write(dir, "Game.exe", pe("d3d12.dll"));
        write(dir, "helper.dll", pe("KERNEL32.dll"));
        assertEquals("Game.exe nạp d3d12.dll", AgvnVkd3d.why("UNKNOWN", exe));
        File dll = write(tmp.newFolder("engine-dll"), "Game.exe", pe("KERNEL32.dll"));
        write(dll.getParentFile(), "Render.dll", pe("D3D12.dll"));
        assertEquals("from a DLL of the game, any case", "Render.dll nạp d3d12.dll", AgvnVkd3d.why("", dll));
    }

    @Test
    public void aGameThatCanDrawOtherwiseIsLeftAsItIs() throws Exception {
        File dir = tmp.newFolder("both");
        File exe = write(dir, "Game.exe", pe("d3d12.dll"));
        write(dir, "RHI.dll", pe("d3d11.dll"));
        assertNull("DirectX 11 too (Unreal 5 picks 12 when it works)", AgvnVkd3d.why("UNREAL", exe));
        File gl = tmp.newFolder("gl");
        File glExe = write(gl, "Game.exe", pe("d3d12.dll"));
        write(gl, "gl.dll", pe("opengl32.dll"));
        assertNull(AgvnVkd3d.why("", glExe));
        File dx11 = write(tmp.newFolder("dx11"), "Game.exe", pe("d3d11.dll"));
        assertNull("no Direct3D 12 at all", AgvnVkd3d.why("UNITY", dx11));
        assertNull("no exe", AgvnVkd3d.why("", new File(tmp.getRoot(), "none.exe")));
    }

    @Test
    public void onlyAGameWithNoVkd3dGetsOne() {
        assertTrue(AgvnVkd3d.none("None"));
        assertTrue(AgvnVkd3d.none(""));
        assertTrue(AgvnVkd3d.none(null));
        assertFalse("the player's choice stays", AgvnVkd3d.none("2.8"));
        assertTrue(new File("src/main/assets/dxwrapper/vkd3d-" + AgvnVkd3d.VERSION + ".tzst").isFile());
    }

    @Test
    public void magineIsSeenByItsPacks() throws Exception {
        File game = tmp.newFolder("IDOL-IKUSEI-SIMULATOR");
        File content = new File(game, "content");
        assertTrue(content.mkdirs());
        write(game, "HuntingWoman3Gamma.exe", pe("KERNEL32.dll"));
        write(content, "catalog.json", "{}".getBytes(StandardCharsets.UTF_8));
        write(content, "base.pak", "GEPK0001 rest".getBytes(StandardCharsets.US_ASCII));
        assertEquals(GameExeResolver.Engine.MAGINE, GameExeResolver.detectEngine(game));
        assertEquals("a game added before 0.1.105 gets it at its start", "MAGINE",
                AgvnEngineRecheck.engineNow("UNKNOWN", game));
        assertNull(AgvnEngineRecheck.engineNow("MAGINE", game));
        write(content, "base.pak", "PK\u0003\u0004 zip".getBytes(StandardCharsets.ISO_8859_1));
        assertEquals("another engine's packs", GameExeResolver.Engine.UNKNOWN, GameExeResolver.detectEngine(game));
    }
}
