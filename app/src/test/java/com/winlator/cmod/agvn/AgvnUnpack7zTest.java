/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.winlator.cmod.agvn.AgvnUnpackException.Reason;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

public class AgvnUnpack7zTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    private static final String NAME = AgvnUnpackFixtures.name("Truyện hay", "Truyen hay");
    private File downloads, games;

    @Before
    public void folders() throws Exception {
        downloads = tmp.newFolder("Download");
        games = new File(tmp.getRoot(), "AGVN");
    }

    private AgvnUnpack.Result unpack(File archive, String password) throws AgvnUnpackException {
        return AgvnUnpack.extract(archive, password, games, new AgvnUnpackFixtures.Quiet());
    }

    private Reason failure(File archive, String password) {
        try {
            unpack(archive, password);
            fail("unpacked");
            return null;
        } catch (AgvnUnpackException e) {
            assertNull("nothing left behind", games.list() == null || games.list().length == 0 ? null : games.list()[0]);
            return e.reason;
        }
    }

    @Test
    public void looseFilesGoIntoAFolderNamedAfterTheArchive() throws Exception {
        File z = AgvnUnpackFixtures.sevenZ(new File(downloads, NAME + ".7z"), "Game.exe", "MZ", "ảnh/cg01.png", "png", "video/op.mp4", "mp4");
        AgvnUnpack.Result r = unpack(z, null);
        assertEquals(new File(games, NAME), r.dir);
        assertEquals(3, r.files);
        assertEquals("png", AgvnUnpackFixtures.read(new File(r.dir, "ảnh/cg01.png")));
        assertTrue("Gallery kept out", new File(r.dir, ".nomedia").isFile());
        assertEquals("nothing beside the archive", 1, downloads.list().length);
    }

    @Test
    public void aSingleTopFolderIsTheGameFolder() throws Exception {
        File z = AgvnUnpackFixtures.sevenZ(new File(downloads, "tai-ve-123.7z"), "My Game/Game.exe", "MZ", "My Game/www/index.html", "<html>");
        AgvnUnpack.Result r = unpack(z, null);
        assertEquals(new File(games, "My Game"), r.dir);
        assertTrue(new File(r.dir, "Game.exe").isFile());
        assertTrue(new File(r.dir, "www/index.html").isFile());
        assertFalse(new File(r.dir, "My Game").exists());
    }

    @Test
    public void partsAreReadAsOneArchive() throws Exception {
        File z = AgvnUnpackFixtures.sevenZ(new File(downloads, "Game.7z"), "Game.exe", "MZ", "data/a.dat", "0123456789abcdefghij0123456789", "data/b.dat", "bbb");
        List<File> parts = AgvnUnpackFixtures.cut(z, 60);
        assertTrue(parts.size() >= 3);
        AgvnUnpack.Result r = unpack(parts.get(1), null); // any part may be picked
        assertEquals(new File(games, "Game"), r.dir);
        assertEquals("0123456789abcdefghij0123456789", AgvnUnpackFixtures.read(new File(r.dir, "data/a.dat")));
    }

    @Test
    public void aMissingPartIsNamed() throws Exception {
        File z = AgvnUnpackFixtures.sevenZ(new File(downloads, "Game.7z"), "Game.exe", "MZ", "data/a.dat", "0123456789abcdefghij0123456789", "data/b.dat", "bbb");
        List<File> parts = AgvnUnpackFixtures.cut(z, 60);
        File last = parts.get(parts.size() - 1), middle = parts.get(1);
        byte[] kept = AgvnUnpackFixtures.readBytes(middle);
        assertTrue(middle.delete());
        try {
            unpack(parts.get(0), null);
            fail();
        } catch (AgvnUnpackException e) {
            assertEquals(Reason.MISSING_PART, e.reason);
            assertEquals("Game.7z.002", e.detail);
        }
        // the last part not downloaded: every part there is full size, the one after them is named
        AgvnUnpackFixtures.write(middle, kept);
        assertTrue(last.delete());
        try {
            unpack(parts.get(0), null);
            fail();
        } catch (AgvnUnpackException e) {
            assertEquals(Reason.MISSING_PART, e.reason);
            assertEquals(last.getName(), e.detail);
        }
    }

    @Test
    public void aLockedFileAsksForItsPasswordAndTellsAWrongOne() throws Exception {
        File z = AgvnUnpackFixtures.locked7z(new File(downloads, "Khoa.7z"), "Game/chào.txt", AgvnUnpackFixtures.bytes("xin chào các bạn"), "agvn.io", false);
        assertEquals(Reason.NEED_PASSWORD, failure(z, null));
        assertEquals(Reason.WRONG_PASSWORD, failure(z, "agvn.io.vn"));
        AgvnUnpack.Result r = unpack(z, " agvn.io\n"); // pasted with a space and a line break
        assertEquals(new File(games, "Khoa"), r.dir); // one file in a folder: still the archive's own folder
        assertEquals("xin chào các bạn", AgvnUnpackFixtures.read(new File(r.dir, "Game/chào.txt")));
    }

    @Test
    public void aLockedFileListAsksForItsPasswordAndTellsAWrongOne() throws Exception {
        File z = AgvnUnpackFixtures.locked7z(new File(downloads, "Kin.7z"), "readme.txt", AgvnUnpackFixtures.bytes("đọc tôi"), "mật khẩu", true);
        assertEquals(Reason.NEED_PASSWORD, failure(z, null));
        assertEquals(Reason.NEED_PASSWORD, failure(z, "  "));
        assertEquals(Reason.WRONG_PASSWORD, failure(z, "mat khau"));
        AgvnUnpack.Result r = unpack(z, "mật khẩu ");
        assertEquals("đọc tôi", AgvnUnpackFixtures.read(new File(r.dir, "readme.txt")));
    }

    @Test
    public void aBrokenArchiveIsDamagedNotAWrongPassword() throws Exception {
        File z = AgvnUnpackFixtures.sevenZ(new File(downloads, "Hong.7z"), "Game.exe", "MZ-0123456789");
        byte[] all = AgvnUnpackFixtures.readBytes(z);
        AgvnUnpackFixtures.write(z, java.util.Arrays.copyOf(all, all.length - 20)); // the download stopped early
        assertEquals(Reason.DAMAGED, failure(z, null));
        assertEquals(Reason.DAMAGED, failure(z, "abc"));
    }
}
