/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/** Cut-short exes and DLLs, by Wine's own rule: a file Wine can load is never reported. */
public class AgvnPeCheckTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** A PE32+ image with headers at 0x80; each section is {raw start, raw size, virtual size}. */
    private static byte[] pe(int sectionAlignment, int[]... sections) {
        int peAt = 0x80, optionalSize = 0xf0, table = peAt + 24 + optionalSize;
        int length = table + sections.length * 40;
        for (int[] s : sections) length = Math.max(length, s[0] + s[1]);
        ByteBuffer b = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        b.put(0, (byte) 'M').put(1, (byte) 'Z').putInt(0x3c, peAt);
        b.put(peAt, (byte) 'P').put(peAt + 1, (byte) 'E');
        b.putShort(peAt + 4, (short) 0x8664).putShort(peAt + 6, (short) sections.length).putShort(peAt + 20, (short) optionalSize);
        b.putShort(peAt + 24, (short) 0x20b).putInt(peAt + 24 + 32, sectionAlignment).putInt(peAt + 24 + 36, 0x200);
        for (int i = 0; i < sections.length; i++) {
            int at = table + i * 40;
            b.putInt(at + 8, sections[i][2]).putInt(at + 12, 0x1000 + i * 0x10000);
            b.putInt(at + 16, sections[i][1]).putInt(at + 20, sections[i][0]);
        }
        return b.array();
    }

    /** .text 0x400-0x2400 and .data 0x2400-0x2600: Wine needs the file up to 0x2600. */
    private static byte[] dll() {
        return pe(0x1000, new int[]{0x400, 0x2000, 0x1f00}, new int[]{0x2400, 0x200, 0x100});
    }

    private File write(String name, byte[] data, int length) throws Exception {
        File f = new File(tmp.getRoot(), name);
        Files.write(f.toPath(), Arrays.copyOf(data, length));
        return f;
    }

    @Test
    public void aWholeFileIsFineAndACutOneIsReported() throws Exception {
        assertNull(AgvnPeCheck.check(write("whole.dll", dll(), 0x2600)));
        AgvnPeCheck.Broken cut = AgvnPeCheck.check(write("UnityPlayer.dll", dll(), 0x1000));
        assertNotNull("cut inside .text, as on the phone of 02/10", cut);
        assertEquals(0x1000, cut.size);
        assertEquals(0x2600, cut.needed);
    }

    @Test
    public void theLastSectorMayBeShortLikeInWine() throws Exception {
        assertNull("Wine rounds the file size up to 512 bytes", AgvnPeCheck.check(write("a.dll", dll(), 0x2401)));
        assertNotNull("the last section starts past the end", AgvnPeCheck.check(write("b.dll", dll(), 0x2400)));
        // Wine maps no more file data than the section's virtual size: a bigger raw size is not needed
        byte[] clamped = pe(0x1000, new int[]{0x400, 0x3000, 0x100});
        assertNull(AgvnPeCheck.check(write("c.dll", clamped, 0x1400)));
        assertNotNull(AgvnPeCheck.check(write("d.dll", clamped, 0x1000)));
    }

    @Test
    public void headersCutShortOrEmptyFilesAreReported() throws Exception {
        AgvnPeCheck.Broken table = AgvnPeCheck.check(write("table.dll", dll(), 0x80 + 24 + 0x10));
        assertNotNull("cut inside the section table", table);
        assertEquals(0x80 + 24 + 0xf0 + 2 * 40, table.needed);
        AgvnPeCheck.Broken empty = AgvnPeCheck.check(write("empty.dll", new byte[0], 0));
        assertNotNull(empty);
        assertEquals(0, empty.needed);
    }

    @Test
    public void filesThatAreNotPeImagesAreLeftAlone() throws Exception {
        assertNull(AgvnPeCheck.check(write("notes.dll", "not a dll".getBytes(StandardCharsets.US_ASCII), 9)));
        byte[] dos = Arrays.copyOf(dll(), 0x80);
        dos[0x3c] = (byte) 0xf0; // a DOS exe: its "PE header" offset points past the end
        assertNull(AgvnPeCheck.check(write("dos.exe", dos, 0x80)));
        // section alignment under a page: Wine maps the whole file as it is
        byte[] flat = pe(0x200, new int[]{0x400, 0x2000, 0x2000});
        assertNull(AgvnPeCheck.check(write("flat.dll", flat, 0x1000)));
        assertNull(AgvnPeCheck.check(new File(tmp.getRoot(), "missing.exe")));
    }

    @Test
    public void scanChecksTheExeAndTheFilesNextToItOnce() throws Exception {
        File dir = tmp.newFolder("Game");
        File exe = new File(dir, "Game.exe");
        Files.write(exe.toPath(), dll());
        Files.write(new File(dir, "good.dll").toPath(), dll());
        Files.write(new File(dir, "cut.dll").toPath(), Arrays.copyOf(dll(), 0x1000));
        Files.write(new File(dir, "data.bin").toPath(), new byte[0]);
        List<AgvnPeCheck.Broken> broken = AgvnPeCheck.scan(exe, dir, dir, null);
        assertEquals(1, broken.size());
        assertEquals("cut.dll", broken.get(0).file.getName());
        assertEquals("cut.dll", AgvnGameFilesCheck.shown(broken.get(0).file, dir));
        assertEquals("Game/cut.dll", AgvnGameFilesCheck.shown(broken.get(0).file, tmp.getRoot()));
    }

    @Test
    public void sizesReadLikeAFileManager() {
        assertEquals("25,6 MB", AgvnGameFilesCheck.size(26_843_546));
        assertEquals("1 KB", AgvnGameFilesCheck.size(1));
        assertEquals("340 KB", AgvnGameFilesCheck.size(340 * 1024));
    }
}
