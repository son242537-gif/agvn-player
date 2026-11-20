/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.winlator.cmod.agvn.AgvnUnpackException.Reason;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * name.part1.zip, name.part2.zip...: archives of their own that hold one game between them (zip4j; 7z
 * and RAR in {@link AgvnUnpackSeriesRarTest}).
 */
public class AgvnUnpackSeriesTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    private File downloads, games;
    private byte[] big;
    private int made;

    @Before
    public void folders() throws Exception {
        downloads = tmp.newFolder("Download");
        games = tmp.newFolder("AGVN");
        big = new byte[120_000];
        new Random(3).nextBytes(big);
    }

    /** A ZIP of its own in Download; {@code files}: path, content, path, content... */
    private File zip(String name, String password, Object... files) throws Exception {
        File src = tmp.newFolder("src" + made++);
        for (int i = 0; i < files.length; i += 2) AgvnUnpackFixtures.write(new File(src, (String) files[i]), (byte[]) files[i + 1]);
        File out = new File(downloads, name);
        ZipParameters p = new ZipParameters();
        if (password != null) {
            p.setEncryptFiles(true);
            p.setEncryptionMethod(EncryptionMethod.AES);
        }
        try (ZipFile z = password != null ? new ZipFile(out, password.toCharArray()) : new ZipFile(out)) {
            for (File top : src.listFiles()) {
                if (top.isDirectory()) z.addFolder(top, p);
                else z.addFile(top, p);
            }
        }
        return out;
    }

    private Reason failure(File archive, String password, String detail) {
        try {
            AgvnUnpack.extract(archive, password, games, new AgvnUnpackFixtures.Quiet());
        } catch (AgvnUnpackException e) {
            assertEquals("nothing left behind", 0, games.list().length);
            if (detail != null) assertEquals(detail, e.detail);
            return e.reason;
        }
        fail("unpacked");
        return null;
    }

    private static byte[] b(String text) {
        return AgvnUnpackFixtures.bytes(text);
    }

    @Test
    public void theSeriesIsFoundFromAnyOfItsArchives() throws Exception {
        zip("Hoa.part1.zip", null, "a.txt", b("a"));
        File second = zip("Hoa.part2.zip", null, "b.txt", b("b"));
        zip("hoa.PART3.ZIP", null, "c.txt", b("c"));
        zip("Khac.part1.zip", null, "x.txt", b("x"));
        zip("Hoa.part1.7z", null, "x.txt", b("x")); // another kind: another series
        AgvnArchiveSeries s = AgvnArchiveSeries.find(second);
        assertEquals("Hoa", s.baseName);
        assertNull(s.missing);
        assertEquals("[Hoa.part1.zip, Hoa.part2.zip, hoa.PART3.ZIP]", names(s.archives));
        assertNull("one archive alone is no series", AgvnArchiveSeries.find(new File(downloads, "Khac.part1.zip")));
        assertNull(AgvnArchiveSeries.find(zip("Hoa.zip", null, "a.txt", b("a"))));
        assertNull(AgvnArchiveSeries.find(new File(downloads, "Hoa.zip.001")));
        // numbers written without zeros, past 9
        for (int i = 1; i <= 11; i++) AgvnUnpackFixtures.write(new File(downloads, "Dai.part" + i + ".7z"), b("x"));
        s = AgvnArchiveSeries.find(new File(downloads, "Dai.part10.7z"));
        assertEquals(11, s.archives.size());
        assertEquals("Dai.part2.7z", s.archives.get(1).getName());
        assertEquals("Dai.part11.7z", s.archives.get(10).getName());
    }

    private static String names(List<File> files) {
        List<String> out = new ArrayList<>();
        for (File f : files) out.add(f.getName());
        return out.toString();
    }

    @Test
    public void everyArchiveGoesIntoOneFolder() throws Exception {
        // each archive has the game's folder at its top, with a share of the files
        zip("Hoa v1.2.part1.zip", null, "Hoa/Game.exe", b("MZ"), "Hoa/www/data/Map001.json", b("{}"), "Hoa/readme.txt", b("old"));
        File second = zip("Hoa v1.2.part2.zip", null, "Hoa/www/img/big.bin", big, "Hoa/readme.txt", b("new"));
        zip("Hoa v1.2.part3.zip", null, "Hoa/www/audio/bgm.ogg", b("ogg"), "Hoa/www/index.html", b("<html>"), "Hoa/www/js/rpg_core.js", b("//"));
        AgvnUnpackFixtures.Quiet quiet = new AgvnUnpackFixtures.Quiet();
        AgvnUnpack.Result r = AgvnUnpack.extract(second, null, games, quiet);
        assertArrayEquals("only the game's folder is left", new String[]{"Hoa"}, games.list());
        assertEquals("the top folder all archives share is the game's folder", new File(games, "Hoa"), r.dir);
        assertEquals("MZ", AgvnUnpackFixtures.read(new File(r.dir, "Game.exe")));
        assertEquals("{}", AgvnUnpackFixtures.read(new File(r.dir, "www/data/Map001.json")));
        assertArrayEquals(big, AgvnUnpackFixtures.readBytes(new File(r.dir, "www/img/big.bin")));
        assertEquals("ogg", AgvnUnpackFixtures.read(new File(r.dir, "www/audio/bgm.ogg")));
        assertEquals("a file in two archives: the later one's", "new", AgvnUnpackFixtures.read(new File(r.dir, "readme.txt")));
        assertTrue(new File(r.dir, ".nomedia").isFile());
        assertEquals("all three may be deleted", 3, r.archiveFiles.size());
        assertEquals(8, r.files);
        assertEquals("progress ends at the whole series", quiet.total, quiet.done);
        long sizes = 0;
        for (File f : r.archiveFiles) sizes += f.length();
        assertEquals(sizes, quiet.total);
        assertTrue("the game is whole only with all the archives: found as one folder", AgvnGameScanner.isGameDir(r.dir));
    }

    @Test
    public void looseFilesAndATopFolderKeepTheirPlaces() throws Exception {
        // part 1 has the files loose, part 2 a folder of its own: as "extract here" would leave them
        File first = zip("Game.part01.zip", null, "Game.exe", b("MZ"), "data/a.bin", b("a"));
        zip("Game.part02.zip", null, "Extra/b.bin", b("b"), "Extra/c.bin", b("c"));
        AgvnUnpack.Result r = AgvnUnpack.extract(first, null, games, new AgvnUnpackFixtures.Quiet());
        assertEquals("named after the series", new File(games, "Game"), r.dir);
        assertEquals("a", AgvnUnpackFixtures.read(new File(r.dir, "data/a.bin")));
        assertEquals("b", AgvnUnpackFixtures.read(new File(r.dir, "Extra/b.bin")));
        // a second run never touches the first game
        AgvnUnpack.Result again = AgvnUnpack.extract(first, null, games, new AgvnUnpackFixtures.Quiet());
        assertEquals(new File(games, "Game (2)"), again.dir);
        assertEquals(2, games.list().length);
    }

    @Test
    public void aMissingNumberIsNamed() throws Exception {
        zip("Hoa.part1.zip", null, "a.txt", b("a"));
        File third = zip("Hoa.part3.zip", null, "c.txt", b("c"));
        assertEquals(Reason.MISSING_PART, failure(third, null, "Hoa.part2.zip"));
        File alone = zip("Mot.part02.7z", null, "c.txt", b("c"));
        assertEquals("the first one is not there", Reason.MISSING_PART, failure(alone, null, "Mot.part01.7z"));
    }

    @Test
    public void onePasswordOpensAllAndAFailureLeavesNothing() throws Exception {
        File first = zip("Khoa.part1.zip", "mật khẩu", "Khoa/Game.exe", b("MZ"));
        zip("Khoa.part2.zip", "mật khẩu", "Khoa/data.bin", big);
        assertEquals("asked once, before anything is written", Reason.NEED_PASSWORD, failure(first, null, null));
        assertEquals(Reason.WRONG_PASSWORD, failure(first, "sai", null));
        AgvnUnpack.Result r = AgvnUnpack.extract(first, " mật khẩu\n", games, new AgvnUnpackFixtures.Quiet());
        assertArrayEquals(big, AgvnUnpackFixtures.readBytes(new File(r.dir, "data.bin")));
        AgvnUnpackFixtures.delete(r.dir);

        // the second archive is broken: the first one's files go too
        File a = zip("Hong.part1.zip", null, "Hong/Game.exe", b("MZ"));
        AgvnUnpackFixtures.write(new File(downloads, "Hong.part2.zip"), b("PK\u0003\u0004 not a zip at all"));
        assertEquals(Reason.DAMAGED, failure(a, null, null));

        // "Hủy" while the second archive is written
        File c = zip("Huy.part1.zip", null, "Huy/Game.exe", b("MZ"));
        zip("Huy.part2.zip", null, "Huy/data.bin", big);
        long firstSize = c.length();
        AgvnUnpack.Listener stop = new AgvnUnpack.Listener() {
            boolean second;

            @Override
            public void onProgress(long doneBytes, long totalBytes, String name) {
                second |= doneBytes > firstSize;
            }

            @Override
            public boolean cancelled() {
                return second;
            }
        };
        try {
            AgvnUnpack.extract(c, null, games, stop);
            fail("not cancelled");
        } catch (AgvnUnpackException e) {
            assertEquals(Reason.CANCELLED, e.reason);
        }
        assertEquals("nothing left behind", 0, games.list().length);
    }
}
