/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;

/** Game archives read for the library's pictures: Ren'Py .rpa (with its pickle index) and RPG Maker .rgssad/.rgss3a. */
public class AgvnGameArchivesTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** Python 3.12 pickle.dumps(index, 5) of the archive {@link #renpyArchive} builds, as Ren'Py 8 writes it. */
    static final String P5 = "80059552000000000000007d94288c116775692f6d61696e5f6d656e752e706e67945d944a714242424a5642424243"
            + "00948794618c13696d616765732f5469746c652042472e6a7067945d944a1a4242424a5c4242426803879461752e";
    /** Python 3.12 pickle.dumps(..., 2): bytes as _codecs.encode(text, "latin1") and bytes(). */
    static final String P2 = "80027d71002858130000006775692f77696e646f775f69636f6e2e706e6771015d71024a264242424a7042424263"
            + "5f636f646563730a656e636f64650a71035804000000c289504e710458060000006c6174696e3171058671065271078771"
            + "08615805000000612e74787471095d710a4a454242424a42424242635f5f6275696c74696e5f5f0a62797465730a710b29"
            + "52710c87710d61752e";

    @Test
    public void readsRenpy8AndPython3Pickles() throws Exception {
        Map<?, ?> index = (Map<?, ?>) AgvnPickle.load(hex(P5));
        assertEquals(Arrays.asList("gui/main_menu.png", "images/Title BG.jpg"), Arrays.asList(index.keySet().toArray()));
        List<?> second = (List<?>) ((List<?>) index.get("images/Title BG.jpg")).get(0);
        assertEquals(88L ^ 0x42424242L, second.get(0));
        assertArrayEquals(new byte[0], (byte[]) second.get(2)); // the memo's b"" again

        Map<?, ?> p2 = (Map<?, ?>) AgvnPickle.load(hex(P2));
        List<?> icon = (List<?>) ((List<?>) p2.get("gui/window_icon.png")).get(0);
        assertArrayEquals(new byte[]{(byte) 0x89, 'P', 'N'}, (byte[]) icon.get(2));
        assertArrayEquals(new byte[0], (byte[]) ((List<?>) ((List<?>) p2.get("a.txt")).get(0)).get(2));
    }

    @Test
    public void readsPython2Pickle() throws Exception {
        // Ren'Py 7: a str key (bytes, UTF-8), a large int as text (INT), a SHORT_BINSTRING prefix
        String name = "gui/tiêu đề.png";
        byte[] key = name.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        p.write(new byte[]{(byte) 0x80, 2, '}', 'q', 0, '(', 'U', (byte) key.length});
        p.write(key);
        p.write(new byte[]{'q', 1, ']', 'q', 2, '('});
        p.write("I4294967300\n".getBytes(StandardCharsets.US_ASCII));
        p.write(new byte[]{'K', 9, 'U', 0, 't', 'q', 3, 'a', 'u', '.'});
        Map<?, ?> index = (Map<?, ?>) AgvnPickle.load(p.toByteArray());
        List<?> where = (List<?>) ((List<?>) index.get(name)).get(0);
        assertEquals(4294967300L, where.get(0));
        assertEquals(9L, where.get(1));
        assertArrayEquals(new byte[0], (byte[]) where.get(2));
    }

    @Test(expected = java.io.IOException.class)
    public void neverRunsOtherCalls() throws Exception {
        AgvnPickle.load("cos\nsystem\n(S'echo'\ntR.".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    public void readsRenpyArchive() throws Exception {
        File rpa = renpyArchive(tmp.newFolder("game"));
        Map<String, AgvnRpaArchive.Entry> index = AgvnRpaArchive.index(rpa);
        assertEquals(2, index.size());
        AgvnRpaArchive.Entry menu = index.get("gui/main_menu.png");
        assertEquals(51, menu.offset);
        assertArrayEquals(filled(20, 'm'), AgvnRpaArchive.read(rpa, menu, 1000));
        assertArrayEquals(filled(30, 't'), AgvnRpaArchive.read(rpa, index.get("images/Title BG.jpg"), 1000));
        assertEquals(null, AgvnRpaArchive.read(rpa, menu, 10)); // larger than allowed
        assertTrue(AgvnRpaArchive.index(tmp.newFile("broken.rpa")).isEmpty());
    }

    @Test
    public void readsRgssArchives() throws Exception {
        byte[] title = filled(1001, 'x');
        for (int version : new int[]{1, 3}) {
            File archive = new File(tmp.getRoot(), version == 1 ? "Game.rgssad" : "Game.rgss3a");
            writeRgss(archive, version, new String[]{"Data\\System.rxdata", "Graphics\\Titles1\\Book.png"},
                    new byte[][]{filled(7, 's'), title});
            List<AgvnRgssArchive.Entry> entries = AgvnRgssArchive.list(archive);
            assertEquals(2, entries.size());
            assertEquals("Graphics/Titles1/Book.png", entries.get(1).name);
            assertArrayEquals(title, AgvnRgssArchive.read(archive, entries.get(1), 1 << 20));
            assertArrayEquals(filled(7, 's'), AgvnRgssArchive.read(archive, entries.get(0), 1 << 20));
        }
        assertTrue(AgvnRgssArchive.list(tmp.newFile("Game.rgss2a")).isEmpty());
    }

    /** game/archive.rpa as Ren'Py 8's archiver lays it out: header, "Made with Ren'Py." before each file, zlib index. */
    static File renpyArchive(File game) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("RPA-3.0 0000000000000076 42424242\n".getBytes(StandardCharsets.US_ASCII));
        for (byte[] data : new byte[][]{filled(20, 'm'), filled(30, 't')}) {
            out.write("Made with Ren'Py.".getBytes(StandardCharsets.US_ASCII));
            out.write(data);
        }
        assertEquals(118, out.size());
        Deflater deflater = new Deflater();
        deflater.setInput(hex(P5));
        deflater.finish();
        byte[] buf = new byte[4096];
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf));
        File rpa = new File(game, "archive.rpa");
        Files.write(rpa.toPath(), out.toByteArray());
        return rpa;
    }

    /** An RGSS archive as RPG Maker writes it (version 1: XP/VX, 3: VX Ace), the reverse of mkxp-z's reader. */
    static void writeRgss(File archive, int version, String[] names, byte[][] files) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("RGSSAD\0".getBytes(StandardCharsets.ISO_8859_1));
        out.write(version);
        if (version == 1) {
            int key = 0xDEADCAFE;
            for (int i = 0; i < names.length; i++) {
                byte[] name = names[i].getBytes(StandardCharsets.UTF_8);
                writeInt(out, name.length ^ key);
                key = key * 7 + 3;
                for (byte b : name) {
                    out.write(b ^ (byte) key);
                    key = key * 7 + 3;
                }
                writeInt(out, files[i].length ^ key);
                key = key * 7 + 3;
                byte[] data = files[i].clone();
                AgvnRgssArchive.decrypt(data, key); // XOR both ways
                out.write(data);
            }
        } else {
            int seed = 12345, base = seed * 9 + 3;
            writeInt(out, seed);
            int table = 8 + 4 + 4;
            for (String n : names) table += 16 + n.getBytes(StandardCharsets.UTF_8).length;
            int offset = table;
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            for (int i = 0; i < names.length; i++) {
                byte[] name = names[i].getBytes(StandardCharsets.UTF_8);
                int fileKey = 777 + i;
                writeInt(out, offset ^ base);
                writeInt(out, files[i].length ^ base);
                writeInt(out, fileKey ^ base);
                writeInt(out, name.length ^ base);
                for (int j = 0; j < name.length; j++) out.write(name[j] ^ (byte) (base >>> (8 * (j % 4))));
                byte[] enc = files[i].clone();
                AgvnRgssArchive.decrypt(enc, fileKey);
                data.write(enc);
                offset += enc.length;
            }
            writeInt(out, base); // offset 0: end of the table
            out.write(data.toByteArray());
        }
        Files.write(archive.toPath(), out.toByteArray());
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        for (int i = 0; i < 4; i++) out.write(v >>> (8 * i));
    }

    static byte[] filled(int n, char c) {
        byte[] b = new byte[n];
        Arrays.fill(b, (byte) c);
        return b;
    }

    static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }
}
