/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.winlator.cmod.agvn.AgvnUnpackException.Reason;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Random;

/**
 * RAR5 through 7-Zip's program (stored RAR5 written by {@link AgvnRar5Fixture}: lists, sets, passwords; RAR's
 * compression itself is not tested here, nothing free creates it), and archives that try to leave the folder. Needs
 * 7-Zip's program for this machine ({@link Agvn7zzHost}); skipped without it.
 */
public class AgvnUnpack7zzRarTest {
    private static final String NAME = AgvnUnpackFixtures.name("Tên tiếng Việt.txt", "Ten.txt");
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    private File program, downloads, games;
    private byte[] big;

    @Before
    public void folders() throws Exception {
        program = Agvn7zzHost.program();
        Assume.assumeTrue("no 7zz for this machine: run scripts/agvn/7zip/build-7zz.sh --host", program != null);
        downloads = tmp.newFolder("Download");
        games = tmp.newFolder("AGVN");
        big = new byte[50_000];
        new Random(5).nextBytes(big);
    }

    private Reason failure(File archive, String password, String detail) {
        try {
            AgvnUnpack.extract(archive, password, games, program, new AgvnUnpackFixtures.Quiet());
        } catch (AgvnUnpackException e) {
            assertEquals("nothing left behind", 0, games.list().length);
            if (detail != null) assertEquals(detail, e.detail);
            return e.reason;
        }
        fail("unpacked");
        return null;
    }

    @Test
    public void rar5() throws Exception {
        File rar = AgvnRar5Fixture.write(new File(downloads, "Truyen [v1.0].rar"), null, "Hoa/", null, "Hoa/www/", null,
                "Hoa/" + NAME, AgvnUnpackFixtures.bytes("xin chào"), "Hoa/data.bin", big, "Hoa/www/index.html", AgvnUnpackFixtures.bytes("<html>"));
        assertEquals(AgvnArchiveKind.Type.RAR, AgvnArchiveKind.resolve(rar).type);
        AgvnUnpackFixtures.Quiet seen = new AgvnUnpackFixtures.Quiet();
        AgvnUnpack.Result r = AgvnUnpack.extract(rar, null, games, program, seen);
        assertEquals("the archive's one top folder is the game's", new File(games, "Hoa"), r.dir);
        assertEquals(3, r.files);
        assertArrayEquals(big, AgvnUnpackFixtures.readBytes(new File(r.dir, "data.bin")));
        assertEquals("xin chào", AgvnUnpackFixtures.read(new File(r.dir, NAME)));
        assertEquals("<html>", AgvnUnpackFixtures.read(new File(r.dir, "www/index.html")));
        assertTrue(new File(r.dir, AgvnUnpack.NOMEDIA).isFile());
        assertEquals(seen.total, seen.done);
        assertEquals(1, r.archiveFiles.size());
        // a RAR renamed .zip is still read as RAR; files loose at the top: the folder is named after the archive
        File loose = AgvnRar5Fixture.write(new File(downloads, "Ban moi.zip"), null, "Hoa.exe", AgvnUnpackFixtures.bytes("MZ"), "www/a.js", big);
        assertEquals(new File(games, "Ban moi"), AgvnUnpack.extract(loose, null, games, program, seen).dir);
        // without 7-Zip's program a RAR cannot be read
        try {
            AgvnUnpack.extract(rar, null, games, seen);
            fail();
        } catch (AgvnUnpackException e) {
            assertEquals(Reason.UNSUPPORTED_METHOD, e.reason);
        }
    }

    @Test
    public void rar5WithAPassword() throws Exception {
        File rar = AgvnRar5Fixture.write(new File(downloads, "Khoa.rar"), "agvn.io", "a.txt", AgvnUnpackFixtures.bytes("first locked file"), "b.bin", big);
        assertEquals(Reason.NEED_PASSWORD, failure(rar, null, null));
        assertEquals(Reason.WRONG_PASSWORD, failure(rar, "AGVN.io", null));
        AgvnUnpack.Result r = AgvnUnpack.extract(rar, "agvn.io \n", games, program, new AgvnUnpackFixtures.Quiet());
        assertEquals(new File(games, "Khoa"), r.dir);
        assertEquals("first locked file", AgvnUnpackFixtures.read(new File(r.dir, "a.txt")));
        assertArrayEquals(big, AgvnUnpackFixtures.readBytes(new File(r.dir, "b.bin")));
        // damage after the password was checked is damage, not a wrong password
        byte[] bytes = AgvnUnpackFixtures.readBytes(rar);
        bytes[bytes.length - 2000] ^= 0x10;
        AgvnUnpackFixtures.delete(r.dir);
        assertEquals(Reason.DAMAGED, failure(AgvnUnpackFixtures.write(new File(downloads, "Hong.rar"), bytes), "agvn.io", "b.bin"));
    }

    @Test
    public void rar5Set() throws Exception {
        List<File> parts = AgvnRar5Fixture.volumes(downloads, "Hoa", "Hoa/data.bin", big, 15_000);
        assertEquals(4, parts.size());
        AgvnUnpack.Result r = AgvnUnpack.extract(parts.get(2), null, games, program, new AgvnUnpackFixtures.Quiet()); // any part will do
        assertArrayEquals(big, AgvnUnpackFixtures.readBytes(new File(r.dir, "data.bin")));
        assertEquals("all parts are the archive's", parts, r.archiveFiles);
        AgvnUnpackFixtures.delete(r.dir);
        assertTrue(parts.get(3).delete()); // the last one: only 7-Zip can tell, as it reads
        assertEquals(Reason.MISSING_PART, failure(parts.get(0), null, "Hoa.part4.rar"));
        assertTrue(parts.get(1).delete()); // one in the middle: told before anything is read
        assertEquals(Reason.MISSING_PART, failure(parts.get(2), null, "Hoa.part2.rar"));
    }

    @Test
    public void linksAndClimbingPathsStayOut() throws Exception {
        File tar = new File(downloads, "Xau.tar");
        try (TarArchiveOutputStream out = new TarArchiveOutputStream(new FileOutputStream(tar))) {
            file(out, "Hoa/ok.txt");
            link(out, "Hoa/link", TarConstants.LF_SYMLINK, "ok.txt");
            link(out, "Hoa/out", TarConstants.LF_SYMLINK, "../../../outside");
            file(out, "Hoa/out/x.txt");
            link(out, "Hoa/hard", TarConstants.LF_LINK, "Hoa/ok.txt");
            file(out, "../escape.txt");
            file(out, "Hoa/../../esc2.txt");
        }
        File outside = tmp.newFolder("outside");
        AgvnUnpack.Result r = AgvnUnpack.extract(tar, null, games, program, new AgvnUnpackFixtures.Quiet());
        assertEquals(new File(games, "Hoa"), r.dir);
        assertEquals("ok.txt; what the archive puts below its link is left out with it", 1, r.files);
        assertEquals("two symbolic links, a hard link, two climbing paths", 5, r.rejected);
        assertEquals("data", AgvnUnpackFixtures.read(new File(r.dir, "ok.txt")));
        assertFalse(new File(r.dir, "out").exists());
        assertFalse(new File(r.dir, "link").exists());
        assertFalse(new File(r.dir, "hard").exists());
        assertFalse(new File(r.dir, "escape.txt").exists());
        assertFalse(new File(r.dir, "esc2.txt").exists());
        assertEquals(0, outside.list().length);
        assertEquals("only the game's folder", 1, games.list().length);
        assertEquals(3, tmp.getRoot().list().length);
    }

    private static void file(TarArchiveOutputStream out, String name) throws Exception {
        TarArchiveEntry e = new TarArchiveEntry(name, true); // true: the name as it is, "../" kept
        e.setSize(4);
        out.putArchiveEntry(e);
        out.write(AgvnUnpackFixtures.bytes("data"));
        out.closeArchiveEntry();
    }

    private static void link(TarArchiveOutputStream out, String name, byte type, String target) throws Exception {
        TarArchiveEntry e = new TarArchiveEntry(name, type);
        e.setLinkName(target);
        out.putArchiveEntry(e);
        out.closeArchiveEntry();
    }
}
