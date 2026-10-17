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
import java.util.Collections;

/** A game that draws with OpenGL alone gets no DXVK fix to try: The Agnietta (cocos2d-x), 17/10/2026. */
public class AgvnGlGameTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** A 32-bit PE whose import table (in .rdata, RVA 0x2000) names {@code dlls}. */
    private static byte[] pe(String... dlls) {
        int peAt = 0x80, optSize = 0xe0, table = peAt + 24 + optSize;
        ByteBuffer b = ByteBuffer.allocate(0x800).order(ByteOrder.LITTLE_ENDIAN);
        b.put(0, (byte) 'M').put(1, (byte) 'Z').putInt(0x3c, peAt);
        b.put(peAt, (byte) 'P').put(peAt + 1, (byte) 'E');
        b.putShort(peAt + 4, (short) 0x14c).putShort(peAt + 6, (short) 2).putShort(peAt + 20, (short) optSize);
        b.putShort(peAt + 24, (short) 0x10b).putInt(peAt + 24 + 28, 0x400000);
        b.putInt(peAt + 24 + 96 + 8, 0x2000).putInt(peAt + 24 + 96 + 12, 20 * (dlls.length + 1));
        section(b, table, ".text", 0x1000, 0x400);
        section(b, table + 40, ".rdata", 0x2000, 0x600);
        for (int i = 0; i < dlls.length; i++) {
            int name = 0x100 + i * 0x20; // the names after the descriptors, in the same section
            b.putInt(0x600 + i * 20 + 12, 0x2000 + name);
            byte[] text = (dlls[i] + "\0").getBytes(StandardCharsets.US_ASCII);
            for (int j = 0; j < text.length; j++) b.put(0x600 + name + j, text[j]);
        }
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
    public void readsWhatEachModuleImports() throws Exception {
        File dll = write(tmp.getRoot(), "glew32.dll", pe("OPENGL32.dll", "KERNEL32.dll"));
        assertEquals(Arrays.asList("kernel32", "opengl32"), AgvnPeFacts.imports(dll));
        assertNull(AgvnPeFacts.imports(write(tmp.getRoot(), "notes.dll", "text".getBytes(StandardCharsets.US_ASCII))));
    }

    @Test
    public void aCocosGameDrawsWithOpenGlAlone() throws Exception {
        File game = tmp.newFolder("agnietta");
        File exe = write(game, "Game.exe", pe("glew32.dll", "libcocos2d.dll", "mozjs-33.dll", "OPENGL32.dll", "KERNEL32.dll"));
        write(game, "glew32.dll", pe("OPENGL32.dll", "KERNEL32.dll"));
        write(game, "libcocos2d.dll", pe("OPENGL32.dll", "glew32.dll", "WS2_32.dll"));
        assertTrue(AgvnGlGame.openGlOnly(exe, false));
    }

    @Test
    public void anyDirectXMeansDxvkMayRun() throws Exception {
        File game = tmp.newFolder("angle");
        File exe = write(game, "Game.exe", pe("OPENGL32.dll", "KERNEL32.dll"));
        write(game, "libGLESv2.dll", pe("d3dcompiler_47.dll", "KERNEL32.dll")); // ANGLE draws OpenGL ES with Direct3D
        assertFalse(AgvnGlGame.openGlOnly(exe, false));
        File dx = tmp.newFolder("dx");
        assertFalse("no OpenGL at all", AgvnGlGame.openGlOnly(write(dx, "Game.exe", pe("d3d9.dll", "KERNEL32.dll")), false));
        File unity = tmp.newFolder("unity");
        write(unity, "UnityPlayer.dll", pe("OPENGL32.dll", "KERNEL32.dll"));
        assertFalse("an engine AGVN knows loads Direct3D itself",
                AgvnGlGame.openGlOnly(write(unity, "Game.exe", pe("UnityPlayer.dll")), true));
    }

    @Test
    public void namesOfDirectX() {
        for (String dll : new String[]{"d3d9", "d3d11", "d3dx9_43", "d3dcompiler_47", "dxgi", "ddraw"}) assertTrue(dll, AgvnGlGame.directX(dll));
        assertFalse(AgvnGlGame.directX("opengl32"));
        assertFalse(AgvnGlGame.drawsWithOpenGlAlone(Collections.singletonList(Collections.singletonList("kernel32"))));
    }
}
