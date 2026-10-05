/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * A KiriKiri game's own size, read where the game reads its Config.tjs, and the screen it gets: never smaller than the
 * game, or it stays black in full screen (Wine lists only modes up to the screen's size).
 */
public class AgvnKirikiriTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String[] ONE = {"system/Config.tjs"};

    private static void write(File dir, String name, byte[] data) throws IOException {
        Files.write(new File(dir, name).toPath(), data);
    }

    private static byte[] archive(int width, int height) {
        return Xp3Fixture.archive(false, false, false, ONE, new byte[][]{Xp3Fixture.config(width, height)});
    }

    @Test
    public void theSizeComesFromDataXp3() throws IOException {
        File game = tmp.newFolder("Game");
        write(game, "data.xp3", archive(1280, 720));
        write(game, "game.exe", "MZ".getBytes(StandardCharsets.US_ASCII));
        assertEquals("1280x720", AgvnKirikiri.gameSize(game));
    }

    @Test
    public void everyFormKrkrrelWrites() throws IOException {
        // version 2 (the index behind a "cushion"), packed index, packed files, a file in a folder of any case
        for (int form = 0; form < 8; form++) {
            File game = tmp.newFolder("form" + form);
            write(game, "data.xp3", Xp3Fixture.archive((form & 1) != 0, (form & 2) != 0, (form & 4) != 0,
                    new String[]{"startup.tjs", "System/CONFIG.TJS"},
                    new byte[][]{"Scripts.execStorage(\"system/Initialize.tjs\");".getBytes(StandardCharsets.US_ASCII),
                            Xp3Fixture.config(1024, 768)}));
            assertEquals("form " + form, "1024x768", AgvnKirikiri.gameSize(game));
        }
    }

    @Test
    public void theLastPatchWins() throws IOException {
        File game = tmp.newFolder("patched");
        write(game, "data.xp3", archive(800, 600));
        assertEquals("800x600", AgvnKirikiri.gameSize(game));
        write(game, "patch.xp3", archive(1024, 768));
        write(game, "patch3.xp3", Xp3Fixture.archive(false, true, true, new String[]{"scenario/first.ks"},
                new byte[][]{"*start\r\n".getBytes(StandardCharsets.US_ASCII)})); // no Config.tjs: next patch
        assertEquals("1024x768", AgvnKirikiri.gameSize(game));
        write(game, "patch2.xp3", archive(1280, 720));
        assertEquals("1280x720", AgvnKirikiri.gameSize(game));
        List<String> order = new ArrayList<>();
        for (File f : AgvnKirikiri.archives(game.listFiles())) order.add(f.getName());
        assertEquals("[patch3.xp3, patch2.xp3, patch.xp3, data.xp3]", order.toString());
    }

    @Test
    public void systemConfigBeforeAnotherConfig() throws IOException {
        File game = tmp.newFolder("plugins");
        write(game, "data.xp3", Xp3Fixture.archive(true, true, false, new String[]{"plugin/config.tjs", "system/Config.tjs"},
                new byte[][]{Xp3Fixture.config(640, 480), Xp3Fixture.config(1280, 720)}));
        assertEquals("1280x720", AgvnKirikiri.gameSize(game));
    }

    @Test
    public void theUnpackedDataFolder() throws IOException {
        File game = tmp.newFolder("unpacked");
        File system = new File(new File(game, "data"), "System");
        system.mkdirs();
        // Shift-JIS, no mark: ASCII stays as it is
        byte[] sjis = ";scWidth = 960;\r\n;scHeight = 540;\r\n// 画面".getBytes(java.nio.charset.Charset.forName("Shift_JIS"));
        write(system, "config.tjs", sjis);
        write(game, "bgm.xp3", archive(640, 480)); // another archive comes after the data folder
        assertEquals("960x540", AgvnKirikiri.gameSize(game));
    }

    @Test
    public void kirikirisOwnTextForms() {
        String text = "// 設定\r\n;scWidth = 1600;\r\n;scHeight = 900;\r\n";
        for (int mode = 0; mode <= 2; mode++)
            assertEquals("mode " + mode, "1600x900", AgvnKirikiri.fromConfig(Xp3Fixture.scrambled(text, mode)));
        assertEquals("1600x900", AgvnKirikiri.fromConfig(Xp3Fixture.concat(new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf},
                text.getBytes(StandardCharsets.UTF_8))));
        assertNull(AgvnKirikiri.fromConfig(Xp3Fixture.scrambled(text, 3))); // a mode KiriKiri does not know
    }

    @Test
    public void commentsAndOtherLinesDoNotCount() {
        String text = "// ;scWidth = 640;\r\n/* old size\r\n;scWidth = 800;\r\n;scHeight = 600;\r\n*/\r\n"
                + ";scWidth = 1280; // 画面幅\r\n;scWidthMax = 1920;\r\n;scHeight=720;\r\n";
        assertEquals("1280x720", AgvnKirikiri.fromConfig(text.getBytes(StandardCharsets.UTF_8)));
        assertNull(AgvnKirikiri.fromConfig(";scWidth = 1280;\r\n".getBytes(StandardCharsets.UTF_8))); // no height
        assertNull(AgvnKirikiri.fromConfig(";scWidth = 1280;\r\n;scHeight = 72;".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void anEncryptedOrForeignArchiveGivesNothing() throws IOException {
        File game = tmp.newFolder("encrypted");
        byte[] config = Xp3Fixture.config(1280, 720);
        for (int i = 0; i < config.length; i++) config[i] ^= 0x5a; // the game's own cipher
        write(game, "data.xp3", Xp3Fixture.archive(true, true, false, ONE, new byte[][]{config}));
        assertNull(AgvnKirikiri.gameSize(game));
        File broken = tmp.newFolder("broken");
        byte[] cut = archive(1280, 720);
        write(broken, "data.xp3", java.util.Arrays.copyOf(cut, cut.length - 20)); // index cut short
        write(broken, "other.xp3", "not an archive at all".getBytes(StandardCharsets.US_ASCII));
        assertNull(AgvnKirikiri.gameSize(broken));
        assertNull(AgvnKirikiri.gameSize(new File(tmp.getRoot(), "missing")));
    }

    @Test
    public void theScreenIsNeverSmallerThanTheGame() {
        assertEquals("1280x720", AgvnKirikiri.notSmaller("854x480", "1280x720"));
        assertEquals("1280x720", AgvnKirikiri.notSmaller("1280x720", "800x600")); // a small frame: "Vừa màn hình"
        assertEquals("800x600", AgvnKirikiri.notSmaller("960x544", "800x600")); // too low
        assertEquals("1600x900", AgvnKirikiri.notSmaller("1600x900", "1280x720")); // the player's larger screen stays
        assertEquals("1024x768", AgvnKirikiri.notSmaller(null, "1023x767")); // even sizes
        assertEquals("854x480", AgvnKirikiri.notSmaller("854x480", null));
        assertEquals("854x480", AgvnKirikiri.notSmaller("854x480", "-")); // not readable
        assertNull(AgvnKirikiri.notSmaller(null, ""));
    }
}
