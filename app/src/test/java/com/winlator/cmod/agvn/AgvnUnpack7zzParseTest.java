/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.winlator.cmod.agvn.AgvnUnpack7zzList.Entry;
import com.winlator.cmod.agvn.AgvnUnpackException.Reason;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** What 7-Zip 25.01 printed (src/test/resources/agvn/7zz), read as the app reads it. Runs on every machine. */
public class AgvnUnpack7zzParseTest {
    private static byte[] captured(String name) throws IOException {
        try (InputStream in = AgvnUnpack7zzParseTest.class.getResourceAsStream("/agvn/7zz/" + name)) {
            assertNotNull(name, in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    private static List<Entry> list(String name) throws IOException {
        return AgvnUnpack7zzList.parse(new ByteArrayInputStream(captured(name)));
    }

    private static Entry find(List<Entry> entries, String path) {
        for (Entry e : entries) if (e.path.equals(path)) return e;
        throw new AssertionError("no " + path);
    }

    private static AgvnUnpackException classify(String name, boolean hadPassword, List<Entry> entries, boolean set) throws IOException {
        // the app reads the errors through the console reader, which drops empty lines
        AgvnUnpack7zzConsole console = new AgvnUnpack7zzConsole(new ByteArrayInputStream(captured(name)), null);
        console.run();
        assertFalse(name, AgvnUnpack7zzErrors.ok(2, console.text()));
        return AgvnUnpack7zzErrors.classify(console.text(), 2, hadPassword, entries, set);
    }

    @Test
    public void lists() throws IOException {
        List<Entry> z = list("list-7z.out"); // a 7z has no "Folder" line: "Attributes = D drwxr-xr-x"
        assertEquals(6, z.size());
        assertTrue(find(z, "Game").dir);
        assertTrue(find(z, "Game/empty").dir);
        Entry data = find(z, "Game/data.bin");
        assertFalse(data.dir);
        assertEquals(300000, data.size);
        assertEquals("names are UTF-8", 1, find(z, "Game/Tên tiếng Việt.txt").size);

        List<Entry> zip = list("list-zip-deflate64.out");
        assertTrue(find(zip, "Game").dir);
        assertEquals(300000, find(zip, "Game/data.bin").size);

        List<Entry> rar = list("list-rar5.out");
        assertEquals(5, rar.size());
        assertTrue(find(rar, "Game/www").dir);
        assertFalse(find(rar, "Game/data.bin").encrypted);
        assertFalse(find(rar, "Game/data.bin").link);
        assertEquals(30000, find(rar, "Game/data.bin").size);
        for (Entry e : list("list-rar5-locked.out")) assertTrue(e.path, e.encrypted);
    }

    @Test
    public void linksAndClimbingPathsAreSeenInAList() throws IOException {
        List<Entry> tar = list("list-tar-links.out");
        assertFalse(find(tar, "Game/ok.txt").link);
        assertTrue("symbolic link", find(tar, "Game/link").link);
        assertTrue("symbolic link out of the folder", find(tar, "Game/out").link);
        assertTrue("hard link", find(tar, "Game/hard").link);
        assertNull(AgvnArchiveNames.clean(find(tar, "../escape.txt").path));
        assertNull(AgvnArchiveNames.clean(find(tar, "Game/../../esc2.txt").path));
        assertEquals("abs.txt", AgvnArchiveNames.clean(find(tar, "/abs.txt").path));
    }

    @Test
    public void passwordErrors() throws IOException {
        assertEquals(Reason.NEED_PASSWORD, classify("list-7z-locked-names.err", false, null, false).reason);
        assertEquals(Reason.WRONG_PASSWORD, classify("list-7z-locked-names-wrong.err", true, null, false).reason);
        assertEquals(Reason.WRONG_PASSWORD, classify("unpack-7z-locked-names-wrong.err", true, null, false).reason);
        assertEquals("RAR5 checks the password", Reason.WRONG_PASSWORD, classify("unpack-rar5-wrong-password.err", true, null, false).reason);
        assertEquals(Reason.NEED_PASSWORD, classify("unpack-rar5-wrong-password.err", false, null, false).reason);

        // "Data Error in encrypted file. Wrong password?": wrong while no locked file was read before it, damage after
        List<Entry> entries = new ArrayList<>();
        for (String path : new String[]{"Game", "Game/Tên tiếng Việt.txt", "Game/data.bin"}) {
            Entry e = new Entry();
            e.path = path;
            e.dir = path.equals("Game");
            e.size = e.dir ? 0 : 10;
            e.encrypted = !e.dir;
            entries.add(e);
        }
        AgvnUnpackException first = classify("unpack-7z-wrong-password.err", true, entries, false);
        assertEquals(Reason.WRONG_PASSWORD, first.reason);
        String later = "ERROR: CRC Failed in encrypted file. Wrong password? : Game/data.bin\n";
        AgvnUnpackException damaged = AgvnUnpack7zzErrors.classify(later, 2, true, entries, false);
        assertEquals(Reason.DAMAGED, damaged.reason);
        assertEquals("Game/data.bin", damaged.detail);
    }

    @Test
    public void archiveErrors() throws IOException {
        AgvnUnpackException crc = classify("unpack-7z-crc.err", false, null, false);
        assertEquals(Reason.DAMAGED, crc.reason);
        assertEquals("Game/data.bin", crc.detail);
        assertEquals(Reason.DAMAGED, classify("unpack-7z-cut-short.err", false, null, false).reason);
        assertEquals(Reason.DAMAGED, classify("list-7z-cut-short.err", false, null, false).reason);
        assertEquals("a set cut short: a part is missing", Reason.MISSING_PART, classify("unpack-7z-set-part-missing.err", false, null, true).reason);
        AgvnUnpackException last = classify("unpack-rar5-set-last-missing.err", false, null, true);
        assertEquals(Reason.MISSING_PART, last.reason);
        assertEquals("set.part4.rar", last.detail);
        assertEquals(Reason.MISSING_PART, classify("unpack-rar5-set-first-missing.err", false, null, true).reason);
        AgvnUnpackException method = classify("unpack-zip-unknown-method.err", false, null, false);
        assertEquals(Reason.UNSUPPORTED_METHOD, method.reason);
        assertEquals("Game/a.txt", method.detail);
        assertEquals(Reason.NOT_ARCHIVE, classify("unpack-not-archive.err", false, null, false).reason);
        assertEquals(Reason.NOT_ARCHIVE, classify("list-not-archive.err", false, null, false).reason);
        assertEquals(Reason.NO_SPACE, classify("unpack-no-space.err", false, null, false).reason);
        AgvnUnpackException write = classify("unpack-cannot-create-folder.err", false, null, false);
        assertEquals(Reason.CANNOT_WRITE, write.reason);
        assertEquals("a/g.tar/o/", write.detail);
    }

    @Test
    public void wordingsNotCapturedHere() {
        // from 7-Zip's source (ExtractCallbackConsole.cpp, MainAr.cpp); no archive on the build machine makes them
        assertEquals(Reason.WRONG_PASSWORD, AgvnUnpack7zzErrors.classify("ERROR: CRC Failed in encrypted file. Wrong password? : a.txt\n", 2, true, null, false).reason);
        assertEquals(Reason.WRONG_PASSWORD, AgvnUnpack7zzErrors.classify("ERRORS:\nHeaders Error in encrypted archive. Wrong password?\n", 2, true, null, false).reason);
        assertEquals(Reason.UNSUPPORTED_METHOD, AgvnUnpack7zzErrors.classify("ERROR: Can't allocate required memory!\n", 8, false, null, false).reason);
        assertEquals(Reason.UNSUPPORTED_METHOD, AgvnUnpack7zzErrors.classify("", 137, false, null, false).reason);
        AgvnUnpackException file = AgvnUnpack7zzErrors.classify("ERROR: Cannot open output file : errno=22 : Invalid argument : /sdcard/AGVN/.x/a:b.txt\n", 2, false, null, false);
        assertEquals(Reason.CANNOT_WRITE, file.reason);
        assertEquals(Reason.DAMAGED, AgvnUnpack7zzErrors.classify("ERROR: Unexpected end of data : Game/a.bin\n", 2, false, null, false).reason);
        assertEquals(Reason.MISSING_PART, AgvnUnpack7zzErrors.classify("ERROR: Unavailable data : Game/a.bin\n", 2, false, null, true).reason);
        // a phone's shared storage keeps no Unix permissions: the files are written all the same
        String modes = "ERROR: Cannot set file attribute : errno=1 : Operation not permitted : /sdcard/AGVN/.x/a.sh\n"
                + "ERROR: Cannot set file attribute : errno=1 : Operation not permitted : /sdcard/AGVN/.x/b\n";
        assertTrue(AgvnUnpack7zzErrors.ok(2, modes));
        assertFalse(AgvnUnpack7zzErrors.ok(2, modes + "ERROR: CRC Failed : c.bin\n"));
        assertEquals("c.bin", AgvnUnpack7zzErrors.classify(modes + "ERROR: CRC Failed : c.bin\n", 2, false, null, false).detail);
        assertFalse("exit 2 with nothing said is a failure", AgvnUnpack7zzErrors.ok(2, ""));
        AgvnUnpack7zzConsole console = new AgvnUnpack7zzConsole(new ByteArrayInputStream(modes.getBytes(StandardCharsets.UTF_8)), null);
        console.run();
        assertFalse("7-Zip is not stopped for it", console.failedFor(0));
        assertTrue("a warning is no failure", AgvnUnpack7zzErrors.ok(1, "WARNINGS:\nThere are data after the end of archive\n"));
        assertFalse(AgvnUnpack7zzErrors.ok(0, "ERROR: Data Error : a\n"));
    }

    @Test
    public void progressIsReadBetweenBackspaces() throws IOException {
        List<String> seen = new ArrayList<>();
        AgvnUnpack7zzConsole console = new AgvnUnpack7zzConsole(new ByteArrayInputStream(captured("unpack-7z-crc.out")),
                (percent, name) -> seen.add(percent + ":" + name));
        console.run();
        assertEquals("progress is no error text", "", console.text());
        assertTrue(seen.toString(), seen.contains("0:null"));
        assertTrue(seen.toString(), seen.contains("100:Game/www/index.html"));

        seen.clear();
        byte[] typed = " 42% 17 - thư mục/ảnh 1.png\b\b\b  7% 3 T Game/data.bin\r  0% 1 Open\b  0M Scan\b 99%\b100% - a - b.txt\n".getBytes(StandardCharsets.UTF_8);
        console = new AgvnUnpack7zzConsole(new ByteArrayInputStream(typed), (percent, name) -> seen.add(percent + ":" + name));
        console.run();
        assertEquals("[42:thư mục/ảnh 1.png, 7:Game/data.bin, 99:null, 100:a - b.txt]", seen.toString());
    }
}
