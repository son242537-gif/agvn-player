/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.winlator.cmod.agvn.AgvnUnpackException.Reason;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.Random;

/**
 * Archives the Java readers cannot read, made by 7-Zip itself and unpacked through it. Needs 7-Zip's program for this
 * machine ({@link Agvn7zzHost}: scripts/agvn/7zip/build-7zz.sh --host); skipped without it.
 */
public class AgvnUnpack7zzTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    private File program, downloads, games, src;
    private byte[] big;

    @Before
    public void folders() throws Exception {
        program = Agvn7zzHost.program();
        Assume.assumeTrue("no 7zz for this machine: run scripts/agvn/7zip/build-7zz.sh --host", program != null);
        downloads = tmp.newFolder("Download");
        games = tmp.newFolder("AGVN");
        src = tmp.newFolder("src");
        big = new byte[300_000];
        new Random(11).nextBytes(big);
        // text packs well: PPMd and BCJ2 are really used on it
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 4000; i++) text.append("label start_").append(i).append(":\n    \"Xin chào\"\n");
        AgvnUnpackFixtures.write(new File(src, "Hoa/data/big.bin"), big);
        AgvnUnpackFixtures.write(new File(src, "Hoa/script.rpy"), AgvnUnpackFixtures.bytes(text.toString()));
        AgvnUnpackFixtures.write(new File(src, "Hoa/Hoa.exe"), AgvnUnpackFixtures.bytes("MZ exe"));
        new File(src, "Hoa/saves").mkdirs();
    }

    /** 7-Zip packs src/Hoa into Download/{@code name} with {@code switches}. */
    private File pack(String name, String... switches) throws Exception {
        String[] args = new String[switches.length + 3];
        args[0] = "a";
        System.arraycopy(switches, 0, args, 1, switches.length);
        args[switches.length + 1] = new File(downloads, name).getPath();
        args[switches.length + 2] = "Hoa";
        Agvn7zzHost.run(src, args);
        return new File(downloads, name);
    }

    private AgvnUnpack.Result unpack(File archive, String password) throws AgvnUnpackException {
        AgvnUnpack.Result r = AgvnUnpack.extract(archive, password, games, program, new AgvnUnpackFixtures.Quiet());
        assertEquals("nothing but the game's folder is left", 1, games.list().length);
        return r;
    }

    private Reason failure(File archive, String password) {
        try {
            AgvnUnpack.extract(archive, password, games, program, new AgvnUnpackFixtures.Quiet());
        } catch (AgvnUnpackException e) {
            assertEquals("nothing left behind", 0, games.list().length);
            return e.reason;
        }
        fail("unpacked");
        return null;
    }

    /** The game as packed ("Hoa": a top folder named "Game" is a part of a game): all files, the one top folder taken as the game's, .nomedia, the empty folder. */
    private void assertGame(AgvnUnpack.Result r) throws Exception {
        assertEquals(new File(games, "Hoa"), r.dir);
        assertArrayEquals(big, AgvnUnpackFixtures.readBytes(new File(r.dir, "data/big.bin")));
        assertEquals("MZ exe", AgvnUnpackFixtures.read(new File(r.dir, "Hoa.exe")));
        assertTrue(new File(r.dir, "script.rpy").length() > 100_000);
        assertTrue(new File(r.dir, AgvnUnpack.NOMEDIA).isFile());
        assertTrue(new File(r.dir, "saves").isDirectory());
        assertEquals(3, r.files);
        assertEquals(0, r.rejected);
        AgvnUnpackFixtures.delete(r.dir);
    }

    @Test
    public void sevenZipMethodsJavaCannotRead() throws Exception {
        // each is first tried by commons-compress, which gives up on the method
        assertGame(unpack(pack("ppmd.7z", "-m0=PPMd"), null));
        assertGame(unpack(pack("bcj2.7z", "-m0=BCJ2", "-m1=LZMA:d16", "-m2=LZMA:d16", "-m3=LZMA:d16", "-mb0:1", "-mb0s1:2", "-mb0s2:3"), null));
        File locked = pack("locked-ppmd.7z", "-m0=PPMd", "-pbí mật 1");
        assertEquals(Reason.NEED_PASSWORD, failure(locked, null));
        assertEquals(Reason.WRONG_PASSWORD, failure(locked, "sai"));
        assertGame(unpack(locked, " bí mật 1\n")); // pasted with a space and a line break
        File names = pack("locked-names-ppmd.7z", "-m0=PPMd", "-psecret", "-mhe=on");
        assertEquals(Reason.NEED_PASSWORD, failure(names, null));
        assertEquals(Reason.WRONG_PASSWORD, failure(names, "Secret"));
        assertGame(unpack(names, "secret"));
    }

    @Test
    public void zipMethodsJavaCannotRead() throws Exception {
        assertGame(unpack(pack("deflate64.zip", "-mm=Deflate64"), null));
        assertGame(unpack(pack("lzma.zip", "-mm=LZMA"), null));
        File locked = pack("deflate64-aes.zip", "-mm=Deflate64", "-mem=AES256", "-ppw");
        assertEquals(Reason.NEED_PASSWORD, failure(locked, null));
        assertEquals(Reason.WRONG_PASSWORD, failure(locked, "pW"));
        assertGame(unpack(locked, "pw"));
    }

    @Test
    public void tarAndPackedTar() throws Exception {
        File tar = pack("Hoa.tar", "-ttar");
        assertEquals(AgvnArchiveKind.Type.TAR, AgvnArchiveKind.resolve(tar).type);
        assertFalse(AgvnArchiveKind.resolve(tar).tarWrapped);
        for (String kind : new String[]{"gzip:.tar.gz", "gzip:.tgz", "xz:.tar.xz", "bzip2:.tar.bz2"}) {
            String type = kind.substring(0, kind.indexOf(':')), name = "Truyen" + kind.substring(kind.indexOf(':') + 1);
            Agvn7zzHost.run(downloads, "a", "-t" + type, name, "Hoa.tar");
            assertTrue(name, AgvnArchiveKind.resolve(new File(downloads, name)).tarWrapped);
            AgvnUnpackFixtures.Quiet seen = new AgvnUnpackFixtures.Quiet();
            AgvnUnpack.Result r = AgvnUnpack.extract(new File(downloads, name), null, games, program, seen);
            assertEquals(name + ": the progress ends full", seen.total, seen.done);
            assertGame(r);
        }
        assertGame(unpack(tar, null));
        if (Agvn7zzHost.zstd(tar, new File(downloads, "Truyen.tar.zst"))) assertGame(unpack(new File(downloads, "Truyen.tar.zst"), null));
        // cut short: nothing is kept
        byte[] gz = AgvnUnpackFixtures.readBytes(new File(downloads, "Truyen.tar.gz"));
        assertEquals(Reason.DAMAGED, failure(AgvnUnpackFixtures.write(new File(downloads, "Cut.tar.gz"), Arrays.copyOf(gz, gz.length / 2)), null));
    }

    @Test
    public void setsCutBySevenZip() throws Exception {
        pack("Hoa.7z", "-m0=PPMd", "-v60k"); // Hoa.7z.001...: commons-compress joins them, then cannot read PPMd
        assertTrue(new File(downloads, "Hoa.7z.003").isFile());
        AgvnUnpack.Result r = unpack(new File(downloads, "Hoa.7z.002"), null);
        assertTrue("every part is the archive's", r.archiveFiles.size() >= 3);
        assertGame(r);
        pack("Zip.zip", "-mm=Deflate64", "-v60k"); // Zip.zip.001...
        assertGame(unpack(new File(downloads, "Zip.zip.001"), null));
        assertTrue(new File(downloads, "Zip.zip.002").delete());
        try {
            AgvnUnpack.extract(new File(downloads, "Zip.zip.001"), null, games, program, new AgvnUnpackFixtures.Quiet());
            fail();
        } catch (AgvnUnpackException e) {
            assertEquals(Reason.MISSING_PART, e.reason);
            assertEquals("Zip.zip.002", e.detail);
        }
    }

    @Test
    public void damageCancelRoomAndAnExistingGame() throws Exception {
        File z = pack("Hoa.7z", "-m0=PPMd", "-mx1");
        byte[] bytes = AgvnUnpackFixtures.readBytes(z);
        for (int i = 150_000; i < 150_040; i++) bytes[i] ^= 0x55;
        assertEquals(Reason.DAMAGED, failure(AgvnUnpackFixtures.write(new File(downloads, "Hong.7z"), bytes), null));
        try {
            AgvnUnpack.extract(z, null, games, program, new AgvnUnpackFixtures.Quiet(), AgvnUnpack.SPARE_BYTES + 5);
            fail();
        } catch (AgvnUnpackException e) {
            assertEquals(Reason.NO_SPACE, e.reason);
        }
        AgvnUnpack.Listener stop = new AgvnUnpack.Listener() {
            int calls;

            @Override
            public void onProgress(long doneBytes, long totalBytes, String name) {
                calls++;
            }

            @Override
            public boolean cancelled() {
                return calls >= 1; // once unpacking has begun
            }
        };
        try {
            AgvnUnpack.extract(z, null, games, program, stop);
            fail();
        } catch (AgvnUnpackException e) {
            assertEquals(Reason.CANCELLED, e.reason);
        }
        assertEquals(0, games.list().length);
        // a game of that name is there: it is not touched, the new one gets "(2)"
        AgvnUnpackFixtures.write(new File(games, "Hoa/save.dat"), AgvnUnpackFixtures.bytes("mine"));
        AgvnUnpack.Result r = AgvnUnpack.extract(z, null, games, program, new AgvnUnpackFixtures.Quiet());
        assertEquals(new File(games, "Hoa (2)"), r.dir);
        assertEquals("mine", AgvnUnpackFixtures.read(new File(games, "Hoa/save.dat")));
        assertEquals(1, new File(games, "Hoa").list().length);
    }
}
