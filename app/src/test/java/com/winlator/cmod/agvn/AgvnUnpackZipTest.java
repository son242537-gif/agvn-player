/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.winlator.cmod.agvn.AgvnUnpackException.Reason;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Arrays;

public class AgvnUnpackZipTest {
    private static final String NAME = AgvnUnpackFixtures.name("Truyện hay", "Truyen hay");
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    private File downloads, games;

    @Before
    public void folders() throws Exception {
        downloads = tmp.newFolder("Download");
        games = tmp.newFolder("AGVN");
    }

    /** {@code files}: name, content, name, content...; {@code password} null for none. */
    private File zip(String name, String password, EncryptionMethod method, String... files) throws Exception {
        File out = new File(downloads, name);
        try (ZipFile z = password != null ? new ZipFile(out, password.toCharArray()) : new ZipFile(out)) {
            for (int i = 0; i < files.length; i += 2) {
                ZipParameters p = new ZipParameters();
                p.setFileNameInZip(files[i]);
                p.setCompressionMethod(CompressionMethod.STORE);
                if (password != null) {
                    p.setEncryptFiles(true);
                    p.setEncryptionMethod(method);
                }
                z.addStream(new ByteArrayInputStream(AgvnUnpackFixtures.bytes(files[i + 1])), p);
            }
        }
        return out;
    }

    private AgvnUnpack.Result unpack(File archive, String password) throws AgvnUnpackException {
        return AgvnUnpack.extract(archive, password, games, new AgvnUnpackFixtures.Quiet());
    }

    private AgvnUnpackException failure(File archive, String password) {
        String before = Arrays.toString(games.list());
        try {
            unpack(archive, password);
        } catch (AgvnUnpackException e) {
            assertEquals("the half-written folder is removed", before, Arrays.toString(games.list()));
            return e;
        }
        fail("unpacked");
        return null;
    }

    @Test
    public void looseFilesGoIntoAFolderOfTheirOwnWithNoMedia() throws Exception {
        File z = zip(NAME + ".zip", null, null, "Game.exe", "MZ", "ảnh/cg01.png", "png", "video/op.mp4", "mp4");
        AgvnUnpackFixtures.Quiet seen = new AgvnUnpackFixtures.Quiet();
        AgvnUnpack.Result r = AgvnUnpack.extract(z, null, games, seen);
        assertEquals(new File(games, NAME), r.dir);
        assertEquals(3, r.files);
        assertEquals("png", AgvnUnpackFixtures.read(new File(r.dir, "ảnh/cg01.png")));
        assertTrue("the Gallery is kept out", new File(r.dir, ".nomedia").isFile());
        assertEquals("nothing spills beside the archive", 1, downloads.list().length);
        assertEquals("only the game folder is left", 1, games.list().length);
        assertEquals(seen.total, seen.done);
        assertEquals("video/op.mp4", seen.last);
    }

    @Test
    public void aSingleTopFolderIsNotNestedTwice() throws Exception {
        File z = zip("tai-ve-123.zip", null, null, "My Game/", "", "My Game/Game.exe", "MZ", "My Game/www/index.html", "<html>");
        AgvnUnpack.Result r = unpack(z, null);
        assertEquals(new File(games, "My Game"), r.dir);
        assertTrue(new File(r.dir, "Game.exe").isFile());
        assertTrue(new File(r.dir, "www/index.html").isFile());
        assertFalse(new File(r.dir, "My Game").exists());
        assertTrue(new File(r.dir, ".nomedia").isFile());
    }

    @Test
    public void anExistingGameFolderIsNeverOverwritten() throws Exception {
        File old = AgvnUnpackFixtures.write(new File(games, "Game/save.dat"), AgvnUnpackFixtures.bytes("my save"));
        File z = zip("Game.zip", null, null, "Game.exe", "MZ", "save.dat", "empty");
        AgvnUnpack.Result r = unpack(z, null);
        assertEquals(new File(games, "Game (2)"), r.dir);
        assertEquals("my save", AgvnUnpackFixtures.read(old));
        assertEquals(new File(games, "Game (3)"), unpack(z, null).dir);
    }

    @Test
    public void aPastedPasswordWorksAndAWrongOneIsToldApart() throws Exception {
        for (EncryptionMethod method : new EncryptionMethod[]{EncryptionMethod.ZIP_STANDARD, EncryptionMethod.AES}) {
            File z = zip("Khoa-" + method + ".zip", "agvn.io", method, "Game.exe", "MZ", "data/a.dat", "0123456789");
            assertEquals(Reason.NEED_PASSWORD, failure(z, null).reason);
            assertEquals(Reason.NEED_PASSWORD, failure(z, " \n").reason);
            assertEquals(Reason.WRONG_PASSWORD, failure(z, "agvn.io.vn").reason);
            AgvnUnpack.Result r = unpack(z, "  agvn.io \r\n"); // copied from the site with what was around it
            assertEquals("0123456789", AgvnUnpackFixtures.read(new File(r.dir, "data/a.dat")));
        }
        File spaced = zip("Space.zip", " có dấu cách ", EncryptionMethod.AES, "a.txt", "a");
        assertEquals("a password that does end in a space", 1, unpack(spaced, " có dấu cách \n").files);
        assertEquals("ＡＢＣ", AgvnUnpack.trimPassword(" 　ＡＢＣ​\t"));
    }

    @Test
    public void entriesClimbingOutAreLeftOut() throws Exception {
        File z = zip("Evil.zip", null, null, "../evil.txt", "x", "a/../../evil2.txt", "x", "..\\evil3.txt", "x", "/abs/ok.txt", "ok", "Game.exe", "MZ");
        AgvnUnpack.Result r = unpack(z, null);
        assertEquals(3, r.rejected);
        assertEquals(2, r.files);
        assertEquals("ok", AgvnUnpackFixtures.read(new File(r.dir, "abs/ok.txt")));
        assertEquals(Arrays.asList("Evil"), Arrays.asList(games.list()));
        assertFalse(new File(tmp.getRoot(), "evil.txt").exists());
        assertFalse(new File(tmp.getRoot(), "evil2.txt").exists());
        assertFalse(new File(tmp.getRoot(), "evil3.txt").exists());
    }

    @Test
    public void aBrokenFileIsNamedAndTheHalfWrittenFolderRemoved() throws Exception {
        File z = zip("Hong.zip", null, null, "Game.exe", "MZ", "data/b.dat", "ABCDEFGHIJKLMNOP", "z.txt", "z");
        byte[] all = AgvnUnpackFixtures.readBytes(z);
        int at = new String(all, java.nio.charset.StandardCharsets.ISO_8859_1).indexOf("ABCDEFGHIJKLMNOP");
        all[at + 3] ^= 0x55; // one byte went bad in the download
        AgvnUnpackFixtures.write(z, all);
        AgvnUnpackException e = failure(z, null);
        assertEquals(Reason.DAMAGED, e.reason);
        assertEquals("data/b.dat", e.detail);
        // a download that stopped early: the end, which lists the files, is not there
        AgvnUnpackFixtures.write(z, Arrays.copyOf(all, all.length - 40));
        assertEquals(Reason.DAMAGED, failure(z, null).reason);
        assertEquals("a wrong password is not blamed", Reason.DAMAGED, failure(z, "abc").reason);
    }

    @Test
    public void damageInALockedZipIsNotCalledAWrongPassword() throws Exception {
        File z = zip("KhoaHong.zip", "pw", EncryptionMethod.ZIP_STANDARD, "a.txt", "aaaa", "b.txt", "0123456789ABCDEF0123456789ABCDEF");
        byte[] all = AgvnUnpackFixtures.readBytes(z);
        int central = new String(all, java.nio.charset.StandardCharsets.ISO_8859_1).indexOf("PK\u0001\u0002");
        all[central - 24] ^= 0x55; // inside b.txt's locked bytes, before the 16 bytes that close the entry
        AgvnUnpackFixtures.write(z, all);
        AgvnUnpackException e = failure(z, "pw");
        assertEquals(Reason.DAMAGED, e.reason);
        assertEquals("b.txt", e.detail);
    }

    @Test
    public void cancelAndNoRoomLeaveNothingBehind() throws Exception {
        File z = zip("Game.zip", null, null, "Game.exe", "MZ", "data/a.dat", "0123456789");
        AgvnUnpack.Listener stop = new AgvnUnpack.Listener() {
            int calls;

            @Override
            public void onProgress(long doneBytes, long totalBytes, String name) {
                calls++;
            }

            @Override
            public boolean cancelled() {
                return calls >= 2; // after the first file
            }
        };
        try {
            AgvnUnpack.extract(z, null, games, stop);
            fail();
        } catch (AgvnUnpackException e) {
            assertEquals(Reason.CANCELLED, e.reason);
        }
        assertEquals(0, games.list().length);
        try {
            AgvnUnpack.extract(z, null, games, new AgvnUnpackFixtures.Quiet(), AgvnUnpack.SPARE_BYTES + 5);
            fail();
        } catch (AgvnUnpackException e) {
            assertEquals(Reason.NO_SPACE, e.reason);
            assertEquals("12 bytes to unpack, room for 5", 7, e.bytes);
        }
        assertEquals(0, games.list().length);
    }
}
