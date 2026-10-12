/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/** A font given another family name, and several names of one font as one collection. */
public class AgvnFontFileTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** A small font file: OS/2, head and a name table as DejaVu Sans has them (typographic family, "Book"). */
    static byte[] font(String family, String style, int weight) {
        String full = "Book".equals(style) ? family : family + " " + style;
        Object[][] names = {{3, 0, "Copyright (c) 2003 by Bitstream, Inc."}, {3, 1, family}, {3, 2, style},
                {3, 4, full}, {3, 6, family.replace(" ", "")}, {3, 13, "Bitstream Vera license"},
                {3, 16, family}, {1, 1, family}};
        ByteArrayOutputStream strings = new ByteArrayOutputStream();
        ByteBuffer name = ByteBuffer.allocate(6 + 12 * names.length);
        name.putShort((short) 0).putShort((short) names.length).putShort((short) (6 + 12 * names.length));
        for (Object[] n : names) {
            boolean windows = (int) n[0] == 3;
            byte[] text = ((String) n[2]).getBytes(windows ? StandardCharsets.UTF_16BE : StandardCharsets.ISO_8859_1);
            name.putShort((short) (int) n[0]).putShort((short) (windows ? 1 : 0))
                    .putShort((short) (windows ? 0x409 : 0)).putShort((short) (int) n[1])
                    .putShort((short) text.length).putShort((short) strings.size());
            strings.write(text, 0, text.length);
        }
        byte[] nameTable = concat(name.array(), strings.toByteArray());
        byte[] os2 = new byte[96];
        os2[4] = (byte) (weight >> 8);
        os2[5] = (byte) weight;
        byte[] head = new byte[54];
        head[12] = 0x5f;
        head[13] = 0x0f;
        head[14] = 0x3c;
        head[15] = (byte) 0xf5;
        return sfnt(new String[]{"OS/2", "head", "name"}, new byte[][]{os2, head, nameTable});
    }

    static byte[] sfnt(String[] tags, byte[][] tables) {
        int n = tags.length, at = 12 + 16 * n;
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        ByteBuffer dir = ByteBuffer.allocate(at);
        dir.putInt(0x00010000).putShort((short) n).putShort((short) 32).putShort((short) 1).putShort((short) 16);
        for (int i = 0; i < n; i++) {
            dir.putInt(AgvnFontFile.tag(tags[i])).putInt(AgvnFontFile.checksum(tables[i]))
                    .putInt(at + body.size()).putInt(tables[i].length);
            body.write(tables[i], 0, tables[i].length);
            while (body.size() % 4 != 0) body.write(0);
        }
        return concat(dir.array(), body.toByteArray());
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private File file(String name, byte[] data) throws Exception {
        File f = new File(tmp.getRoot(), name);
        Files.write(f.toPath(), data);
        return f;
    }

    @Test
    public void aRenamedFontKeepsItsLicense() throws Exception {
        AgvnFontFile font = AgvnFontFile.read(file("DejaVuSans-Bold.ttf", font("DejaVu Sans", "Bold", 700)));
        byte[] name = font.renamed("Segoe UI", "Bold");
        assertEquals("Segoe UI", AgvnFontFile.nameIn(name, 1));
        assertEquals("Bold", AgvnFontFile.nameIn(name, 2));
        assertEquals("Segoe UI Bold", AgvnFontFile.nameIn(name, 4));
        assertEquals("SegoeUI-Bold", AgvnFontFile.nameIn(name, 6));
        assertNull("no typographic family left to name it DejaVu Sans", AgvnFontFile.nameIn(name, 16));
        assertEquals("Copyright (c) 2003 by Bitstream, Inc.", AgvnFontFile.nameIn(name, 0));
        assertEquals("Bitstream Vera license", AgvnFontFile.nameIn(name, 13));
        byte[] regular = AgvnFontFile.read(file("DejaVuSans.ttf", font("DejaVu Sans", "Book", 400)))
                .renamed("Tahoma", "Regular");
        assertEquals("Tahoma", AgvnFontFile.nameIn(regular, 4));
        assertEquals("Tahoma", AgvnFontFile.nameIn(regular, 6));
        // the Macintosh record of the old family is gone too
        ByteBuffer b = ByteBuffer.wrap(regular);
        for (int i = 0; i < (b.getShort(2) & 0xffff); i++)
            assertTrue((b.getShort(6 + 12 * i) & 0xffff) == 3 || (b.getShort(12 + 12 * i) & 0xffff) != 1);
    }

    @Test
    public void oneCollectionHoldsBothNamesAndOneCopyOfTheRest() throws Exception {
        byte[] single = font("DejaVu Sans", "Book", 400);
        AgvnFontFile font = AgvnFontFile.read(file("DejaVuSans.ttf", single));
        byte[] ttc = font.collection(Arrays.asList(font.renamed("Tahoma", "Regular"),
                font.renamed("Segoe UI", "Regular")));
        File out = file("tahoma.ttc", ttc);
        assertEquals("Tahoma", AgvnFontFile.name(out, 0, 1));
        assertEquals("Segoe UI", AgvnFontFile.name(out, 1, 1));
        assertNull(AgvnFontFile.name(out, 2, 1));
        assertEquals("Segoe UI", AgvnFontFile.name(out, 1, 4));
        ByteBuffer b = ByteBuffer.wrap(ttc);
        assertEquals(AgvnFontFile.TTCF, b.getInt(0));
        assertEquals(2, b.getInt(8));
        int first = b.getInt(12), second = b.getInt(16), n = b.getShort(first + 4);
        for (int i = 0; i < n; i++) {
            int r1 = first + 12 + 16 * i, r2 = second + 12 + 16 * i;
            int tag = b.getInt(r1), at = b.getInt(r1 + 8), length = b.getInt(r1 + 12);
            assertEquals(tag, b.getInt(r2));
            assertEquals(0, at % 4);
            assertEquals("checksum of " + Integer.toHexString(tag), b.getInt(r1 + 4),
                    AgvnFontFile.checksum(Arrays.copyOfRange(ttc, at, at + length)));
            if (tag != AgvnFontFile.NAME) assertEquals("shared", at, b.getInt(r2 + 8));
        }
    }

    @Test
    public void aFileThatIsNoFontHasNoName() throws Exception {
        assertNull(AgvnFontFile.name(file("readme.txt", "not a font at all".getBytes(StandardCharsets.UTF_8)), 0, 1));
        assertNull(AgvnFontFile.name(file("empty.ttf", new byte[0]), 0, 1));
        assertEquals("Arial", AgvnFontFile.name(file("arial.ttf", font("Arial", "Book", 400)), 0, 1));
        assertNull("one face only", AgvnFontFile.name(new File(tmp.getRoot(), "arial.ttf"), 1, 1));
    }
}
