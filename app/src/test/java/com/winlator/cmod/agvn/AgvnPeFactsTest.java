/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
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

/** What an exe of an engine AGVN does not know says of itself, for the session log. */
public class AgvnPeFactsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * A 32-bit exe: .text at 0x400, then {@code third} (a packer's section name, or .rdata) at 0x600 holding the
     * import table (RVA 0x2000) that names d3d9.dll and KERNEL32.dll, then {@code appended} after its last section.
     */
    private static byte[] exe(String third, byte[] appended) {
        int peAt = 0x80, optSize = 0xe0, table = peAt + 24 + optSize;
        ByteBuffer b = ByteBuffer.allocate(0x800 + appended.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put(0, (byte) 'M').put(1, (byte) 'Z').putInt(0x3c, peAt);
        b.put(peAt, (byte) 'P').put(peAt + 1, (byte) 'E');
        b.putShort(peAt + 4, (short) 0x14c).putShort(peAt + 6, (short) 2).putShort(peAt + 20, (short) optSize);
        b.putShort(peAt + 24, (short) 0x10b).putInt(peAt + 24 + 28, 0x400000);
        b.putInt(peAt + 24 + 96 + 8, 0x2000).putInt(peAt + 24 + 96 + 12, 60); // the import directory
        section(b, table, ".text", 0x1000, 0x400);
        section(b, table + 40, third, 0x2000, 0x600);
        b.putInt(0x600 + 12, 0x2100).putInt(0x600 + 20 + 12, 0x2110); // two descriptors' names, then a zero one
        b.position(0x700);
        b.put("d3d9.dll\0".getBytes(StandardCharsets.US_ASCII));
        b.position(0x710);
        b.put("KERNEL32.dll\0".getBytes(StandardCharsets.US_ASCII));
        b.position(0x800);
        b.put(appended);
        return b.array();
    }

    private static void section(ByteBuffer b, int at, String name, int va, int raw) {
        byte[] n = Arrays.copyOf(name.getBytes(StandardCharsets.US_ASCII), 8);
        for (int i = 0; i < 8; i++) b.put(at + i, n[i]);
        b.putInt(at + 8, 0x200).putInt(at + 12, va).putInt(at + 16, 0x200).putInt(at + 20, raw);
    }

    private File write(String name, byte[] data) throws Exception {
        File f = tmp.newFile(name);
        Files.write(f.toPath(), data);
        return f;
    }

    @Test
    public void whatTheExeLoadsAndHolds() throws Exception {
        File plain = write("a.exe", exe(".rdata", new byte[0]));
        assertEquals("File exe: 32-bit · nạp: d3d9, kernel32", AgvnPeFacts.describe(plain));
        byte[] zip = new byte[(int) AgvnPeFacts.BIG_OVERLAY * 2];
        zip[0] = 'P';
        zip[1] = 'K';
        zip[2] = 3;
        zip[3] = 4;
        File packed = write("b.exe", exe(".enigma1", zip));
        assertEquals("File exe: 32-bit · nạp: d3d9, kernel32 · đóng gói: Enigma Virtual Box · 2 MB nối sau exe"
                + " (file zip: LÖVE, NW.js hoặc file nén tự giải nén)", AgvnPeFacts.describe(packed));
    }

    @Test
    public void aPythonGameAndWhatIsNoExe() throws Exception {
        byte[] python = new byte[(int) AgvnPeFacts.BIG_OVERLAY + 100];
        byte[] cookie = "MEI\u000c\u000b\n\u000b\u000e".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(cookie, 0, python, python.length - 88, cookie.length);
        assertEquals("File exe: 32-bit · nạp: d3d9, kernel32 · 1 MB nối sau exe (PyInstaller, game Python)",
                AgvnPeFacts.describe(write("c.exe", exe(".rdata", python))));
        File text = tmp.newFile("readme.exe");
        Files.write(text.toPath(), "not a program".getBytes(StandardCharsets.US_ASCII));
        assertNull(AgvnPeFacts.describe(text));
        assertNull(AgvnPeFacts.describe(new File(tmp.getRoot(), "missing.exe")));
    }
}
