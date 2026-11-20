/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.winlator.cmod.agvn.AgvnUnpackException.Reason;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Split ZIPs, names of old ZIPs, and files that are no ZIP or 7z. */
public class AgvnUnpackPartsTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    private File downloads, games;
    private byte[] big;

    @Before
    public void folders() throws Exception {
        downloads = tmp.newFolder("Download");
        games = tmp.newFolder("AGVN");
        big = new byte[200_000];
        new Random(7).nextBytes(big);
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

    /** Game.zip with data/big.bin (200 kB, stored) and Game.exe; cut by zip4j into 64 kB parts when {@code split}. */
    private File zip(String password, boolean split) throws Exception {
        File src = tmp.newFolder("src");
        AgvnUnpackFixtures.write(new File(src, "data/big.bin"), big);
        AgvnUnpackFixtures.write(new File(src, "Game.exe"), AgvnUnpackFixtures.bytes("MZ"));
        File out = new File(downloads, "Game.zip");
        ZipParameters p = new ZipParameters();
        p.setCompressionMethod(CompressionMethod.STORE);
        p.setIncludeRootFolder(false);
        if (password != null) {
            p.setEncryptFiles(true);
            p.setEncryptionMethod(EncryptionMethod.AES);
        }
        try (ZipFile z = password != null ? new ZipFile(out, password.toCharArray()) : new ZipFile(out)) {
            if (split) z.createSplitZipFileFromFolder(src, p, true, 65536);
            else z.addFolder(src, p);
        }
        return out;
    }

    @Test
    public void aSplitZipIsReadFromAnyOfItsParts() throws Exception {
        File z = zip("pw", true);
        assertTrue(new File(downloads, "Game.z01").isFile() && new File(downloads, "Game.z03").isFile());
        AgvnUnpack.Result r = AgvnUnpack.extract(new File(downloads, "Game.z02"), "pw ", games, new AgvnUnpackFixtures.Quiet());
        assertEquals(new File(games, "Game"), r.dir);
        assertArrayEquals(big, AgvnUnpackFixtures.readBytes(new File(r.dir, "data/big.bin")));
        assertEquals(z.getParentFile(), downloads);
    }

    @Test
    public void aMissingPartOfASplitZipIsNamed() throws Exception {
        File z = zip(null, true);
        File z01 = new File(downloads, "Game.z01"), z02 = new File(downloads, "Game.z02"), z03 = new File(downloads, "Game.z03");
        byte[] kept = AgvnUnpackFixtures.readBytes(z02);
        assertTrue(z02.delete());
        assertEquals(Reason.MISSING_PART, failure(z, null, "Game.z02"));
        // the highest part gone: no gap in the names, the ZIP itself says how many parts it has
        AgvnUnpackFixtures.write(z02, kept);
        assertTrue(z03.delete());
        assertEquals(Reason.MISSING_PART, failure(z, null, "Game.z03"));
        assertTrue(z.delete());
        assertEquals(Reason.MISSING_PART, failure(z01, null, "Game.zip"));
    }

    @Test
    public void aZipCutIntoNumberedPartsIsRead() throws Exception {
        for (String password : new String[]{null, "pw"}) {
            List<File> parts = AgvnUnpackFixtures.cut(zip(password, false), 70_000);
            assertEquals("Game.zip.003", parts.get(2).getName());
            AgvnUnpack.Result r = AgvnUnpack.extract(parts.get(2), password, games, new AgvnUnpackFixtures.Quiet());
            assertArrayEquals(big, AgvnUnpackFixtures.readBytes(new File(r.dir, "data/big.bin")));
            assertEquals("MZ", AgvnUnpackFixtures.read(new File(r.dir, "Game.exe")));
            AgvnUnpackFixtures.delete(r.dir);
            File last = parts.get(parts.size() - 1);
            assertTrue(last.delete()); // the last part not downloaded: the one after the full-size parts is named
            assertEquals(Reason.MISSING_PART, failure(parts.get(0), password, last.getName()));
            assertTrue(parts.get(1).delete());
            assertEquals(Reason.MISSING_PART, failure(parts.get(0), password, "Game.zip.002"));
            for (File f : downloads.listFiles()) assertTrue(f.delete());
            AgvnUnpackFixtures.delete(new File(tmp.getRoot(), "src"));
        }
    }

    /** A ZIP whose names are stored in {@code charset} without the UTF-8 flag, as old Windows tools wrote them. */
    private File oldZip(String charset, boolean unicodeExtra, String... names) throws Exception {
        File out = new File(downloads, "old.zip");
        try (ZipArchiveOutputStream z = new ZipArchiveOutputStream(out)) {
            z.setEncoding(charset);
            z.setUseLanguageEncodingFlag(false);
            z.setCreateUnicodeExtraFields(unicodeExtra ? ZipArchiveOutputStream.UnicodeExtraFieldPolicy.ALWAYS
                    : ZipArchiveOutputStream.UnicodeExtraFieldPolicy.NEVER);
            for (String name : names) {
                z.putArchiveEntry(new ZipArchiveEntry(name));
                z.write(AgvnUnpackFixtures.bytes(name));
                z.closeArchiveEntry();
            }
        }
        return out;
    }

    @Test
    public void namesOfOldZipsComeOutReadable() throws Exception {
        Assume.assumeTrue(Charset.isSupported("windows-31j") && Charset.isSupported("GBK"));
        Assume.assumeTrue("this machine's file names hold Unicode", AgvnUnpackFixtures.name("ゲーム", "").length() > 0);
        String[][] cases = {
                {"windows-31j", "ゲーム/セーブデータ.dat", "ゲーム/立ち絵①.png", "readme.txt"},
                {"GBK", "游戏/存档说明.txt", "游戏/图片.png", "readme.txt"},
                {"UTF-8", "Truyện/Chương 1.txt", "Truyện/ảnh nền.png", "readme.txt"}};
        for (String[] c : cases) {
            String[] names = Arrays.copyOfRange(c, 1, c.length);
            AgvnUnpack.Result r = AgvnUnpack.extract(oldZip(c[0], false, names), null, games, new AgvnUnpackFixtures.Quiet());
            for (String name : names) assertEquals(c[0], name, AgvnUnpackFixtures.read(new File(r.dir, name)));
            AgvnUnpackFixtures.delete(r.dir);
        }
        // names in a charset no rule guesses, with the Unicode name WinRAR and Info-ZIP add
        String[] thai = {"เกม/บันทึก.txt", "เกม/รูป.png"};
        AgvnUnpack.Result r = AgvnUnpack.extract(oldZip("x-windows-874", true, thai), null, games, new AgvnUnpackFixtures.Quiet());
        assertEquals(new File(games, "เกม"), r.dir);
        assertEquals(thai[1], AgvnUnpackFixtures.read(new File(r.dir, "รูป.png")));
    }

    @Test
    public void rarIsToldApartByItsBytes() throws Exception {
        byte[] rar = {'R', 'a', 'r', '!', 0x1A, 7, 1, 0, 1, 2, 3};
        // RAR goes to 7-Zip's program (AgvnUnpack7zzRarTest); without it, as on a machine that has none, it cannot be read
        assertEquals(Reason.UNSUPPORTED_METHOD, failure(AgvnUnpackFixtures.write(new File(downloads, "Game.rar"), rar), null, null));
        assertEquals(Reason.UNSUPPORTED_METHOD, failure(AgvnUnpackFixtures.write(new File(downloads, "Renamed.zip"), rar), null, null));
        assertEquals(Reason.UNSUPPORTED_METHOD, failure(AgvnUnpackFixtures.write(new File(downloads, "Game.part1.rar"), new byte[0]), null, null));
        assertEquals(Reason.NOT_ARCHIVE, failure(AgvnUnpackFixtures.write(new File(downloads, "video.mp4"), new byte[]{0, 0, 0, 24}), null, null));
        assertEquals("a web page saved as .zip", Reason.DAMAGED,
                failure(AgvnUnpackFixtures.write(new File(downloads, "404.zip"), AgvnUnpackFixtures.bytes("<html>Not found</html>")), null, null));
    }
}
