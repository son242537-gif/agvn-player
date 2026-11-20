/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;
import java.util.Random;

/**
 * name.part1.7z, name.part2.7z: archives of their own; name.part1.rar, name.part2.rar: the volumes of one RAR, or
 * archives of their own ({@link AgvnArchiveSeries}). Stored RAR5 written by {@link AgvnRar5Fixture}; unpacking RAR needs
 * 7-Zip's program for this machine ({@link Agvn7zzHost}).
 */
public class AgvnUnpackSeriesRarTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    private File downloads, games;
    private byte[] big;

    @Before
    public void folders() throws Exception {
        downloads = tmp.newFolder("Download");
        games = tmp.newFolder("AGVN");
        big = new byte[120_000];
        new Random(3).nextBytes(big);
    }

    private static byte[] b(String text) {
        return AgvnUnpackFixtures.bytes(text);
    }

    @Test
    public void sevenZArchivesOfTheirOwn() throws Exception {
        File first = AgvnUnpackFixtures.sevenZ(new File(downloads, "Bay.part1.7z"), "Bay/Game.exe", "MZ", "Bay/a.txt", "a");
        AgvnUnpackFixtures.sevenZ(new File(downloads, "Bay.part2.7z"), "Bay/data/b.txt", "b");
        AgvnUnpack.Result r = AgvnUnpack.extract(first, null, games, new AgvnUnpackFixtures.Quiet());
        assertEquals(new File(games, "Bay"), r.dir);
        assertEquals("b", AgvnUnpackFixtures.read(new File(r.dir, "data/b.txt")));
        assertEquals("a", AgvnUnpackFixtures.read(new File(r.dir, "a.txt")));
    }

    @Test
    public void rarVolumesStayOneArchiveAndWholeRarsAreASeries() throws Exception {
        // a real set: every volume says "volume"
        List<File> volumes = AgvnRar5Fixture.volumes(downloads, "Bo", "big.bin", big, 50_000);
        assertFalse(AgvnArchiveSeries.wholeRar(volumes.get(0)));
        assertFalse(AgvnArchiveSeries.wholeRar(volumes.get(1)));
        assertNull("left to 7-Zip as one archive", AgvnArchiveSeries.find(volumes.get(1)));
        assertEquals(3, AgvnArchiveKind.resolve(volumes.get(1)).files.size());
        // RAR 4's main header: volume flag set, then clear
        byte[] rar4 = {'R', 'a', 'r', '!', 0x1A, 7, 0, 0x11, 0x22, 0x73, 0x01, 0x01, 13, 0};
        assertFalse(AgvnArchiveSeries.wholeRar(rar4, rar4.length));
        rar4[10] = 0;
        assertTrue(AgvnArchiveSeries.wholeRar(rar4, rar4.length));
        assertFalse("not a RAR", AgvnArchiveSeries.wholeRar(b("PK\u0003\u0004............"), 16));

        // whole archives named like volumes
        File first = AgvnRar5Fixture.write(new File(downloads, "Rieng.part1.rar"), null, "Rieng/Game.exe", b("MZ"));
        AgvnRar5Fixture.write(new File(downloads, "Rieng.part2.rar"), null, "Rieng/data/big.bin", big);
        assertTrue(AgvnArchiveSeries.wholeRar(first));
        AgvnArchiveSeries s = AgvnArchiveSeries.find(first);
        assertNotNull(s);
        assertEquals(2, s.archives.size());
        File program = Agvn7zzHost.program();
        Assume.assumeTrue("no 7zz for this machine: run scripts/agvn/7zip/build-7zz.sh --host", program != null);
        AgvnUnpack.Result r = AgvnUnpack.extract(first, null, games, program, new AgvnUnpackFixtures.Quiet());
        assertEquals(new File(games, "Rieng"), r.dir);
        assertEquals("MZ", AgvnUnpackFixtures.read(new File(r.dir, "Game.exe")));
        assertArrayEquals(big, AgvnUnpackFixtures.readBytes(new File(r.dir, "data/big.bin")));
        assertArrayEquals(new String[]{"Rieng"}, games.list());
        // the real set still unpacks as one archive
        AgvnUnpack.Result set = AgvnUnpack.extract(volumes.get(2), null, games, program, new AgvnUnpackFixtures.Quiet());
        assertArrayEquals(big, AgvnUnpackFixtures.readBytes(new File(set.dir, "big.bin")));
        assertEquals(3, set.archiveFiles.size());
    }
}
