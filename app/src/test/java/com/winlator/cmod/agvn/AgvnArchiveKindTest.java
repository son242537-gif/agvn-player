/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.winlator.cmod.agvn.AgvnArchiveKind.Type;
import com.winlator.cmod.agvn.AgvnArchiveKind.Volumes;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;

public class AgvnArchiveKindTest {
    private static final byte[] ZIP = {'P', 'K', 3, 4, 20, 0, 0, 0}, SEVEN = {'7', 'z', (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C, 0, 4},
            RAR4 = {'R', 'a', 'r', '!', 0x1A, 7, 0, 0}, RAR5 = {'R', 'a', 'r', '!', 0x1A, 7, 1, 0};

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File file(String name, byte[] head) throws Exception {
        return AgvnUnpackFixtures.write(new File(tmp.getRoot(), name), head);
    }

    @Test
    public void theFirstBytesDecideNotTheExtension() throws Exception {
        assertEquals(Type.ZIP, AgvnArchiveKind.resolve(file("a.zip", ZIP)).type);
        assertEquals(Type.SEVEN_Z, AgvnArchiveKind.resolve(file("b.7z", SEVEN)).type);
        assertEquals(Type.RAR, AgvnArchiveKind.resolve(file("c.rar", RAR5)).type);
        assertEquals("a RAR renamed .zip", Type.RAR, AgvnArchiveKind.resolve(file("d.zip", RAR4)).type);
        assertEquals("a 7z without extension", Type.SEVEN_Z, AgvnArchiveKind.resolve(file("tai-ve", SEVEN)).type);
        assertEquals("a split ZIP's first part", Type.ZIP, AgvnArchiveKind.sniff(new byte[]{'P', 'K', 7, 8}, 4));
        assertEquals("no archive's bytes: the name", Type.ZIP, AgvnArchiveKind.resolve(file("e.zip", AgvnUnpackFixtures.bytes("<html>404"))).type);
        assertEquals("unreadable bytes: the name", Type.RAR, AgvnArchiveKind.resolve(file("f.part2.rar", new byte[]{1, 2, 3})).type);
        assertEquals(Type.UNKNOWN, AgvnArchiveKind.resolve(file("g.mp4", new byte[]{0, 0, 0, 24})).type);
    }

    @Test
    public void numberedPartsAreFoundBesideThePickedOne() throws Exception {
        file("Game.7z.001", SEVEN);
        File second = file("Game.7z.002", new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        file("Game.7z.003", new byte[]{9});
        file("Other.7z.001", SEVEN);
        Volumes v = AgvnArchiveKind.resolve(second);
        assertEquals(Type.SEVEN_Z, v.type);
        assertEquals("Game", v.baseName);
        assertEquals("Game.7z.001", v.first.getName());
        assertEquals(3, v.parts.size());
        assertNull(v.missing);
        assertNull("the last part is smaller: the set is whole", v.likelyMissingNext());
        assertTrue(new File(tmp.getRoot(), "Game.7z.003").delete());
        assertEquals("Game.7z.003", AgvnArchiveKind.resolve(second).likelyMissingNext());
    }

    @Test
    public void aGapInTheNumbersNamesTheMissingPart() throws Exception {
        File third = file("Game.zip.003", new byte[]{1});
        assertEquals("Game.zip.001", AgvnArchiveKind.resolve(third).missing);
        file("Game.zip.001", ZIP);
        Volumes v = AgvnArchiveKind.resolve(third);
        assertEquals("Game.zip.002", v.missing);
        assertEquals(Type.ZIP, v.type);
    }

    @Test
    public void aSplitZipOpensAtItsZipFile() throws Exception {
        File zip = file("Game.zip", ZIP);
        File z01 = file("Game.z01", new byte[]{'P', 'K', 7, 8});
        file("Game.z03", new byte[]{1});
        Volumes v = AgvnArchiveKind.resolve(z01);
        assertEquals(zip.getName(), v.first.getName());
        assertEquals("Game.z02", v.missing);
        file("Game.z02", new byte[]{1});
        assertNull(AgvnArchiveKind.resolve(zip).missing);
        assertTrue(zip.delete());
        v = AgvnArchiveKind.resolve(z01);
        assertEquals("Game.zip", v.missing);
        assertEquals(Type.ZIP, v.type);
    }

    @Test
    public void aFolderListsOneNamePerArchive() {
        String[] names = {"Game.zip", "Game.z01", "Game.z02", "B.7z.001", "B.7z.002", "C.zip.002", "D.part1.rar", "D.part2.rar",
                "E.rar", "E.r00", "ảnh.png", "setup.exe", "F.7Z"};
        assertEquals(Arrays.asList("Game.zip", "B.7z.001", "C.zip.002", "D.part1.rar", "D.part2.rar", "E.rar", "F.7Z"),
                AgvnArchiveKind.pickable(names));
        assertTrue(AgvnArchiveKind.pickable(null).isEmpty());
    }
}
