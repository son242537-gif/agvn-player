/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The game's own pictures for its cover and icon, instead of the Ren'Py or RPG Maker icon of its .exe. */
public class AgvnGameArtTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void renpyMenuArtLooseAndPacked() throws Exception {
        File root = tmp.newFolder("Loose");
        write(new File(root, "game/gui/main_menu.png"), 10);
        write(new File(root, "game/gui/game_menu.png"), 10);
        write(new File(root, "game/gui/overlay/main_menu.png"), 900); // the translucent band, never art
        write(new File(root, "game/images/menu/Title Screen.jpg"), 500);
        write(new File(root, "game/images/bg/park.jpg"), 800);
        assertEquals(Arrays.asList("game/gui/main_menu.png", "game/images/menu/Title Screen.jpg", "game/gui/game_menu.png"),
                names(root, AgvnCoverSources.candidates(root)));

        File packed = tmp.newFolder("Packed");
        File game = new File(packed, "game");
        assertTrue(game.mkdirs());
        AgvnGameArchivesTest.renpyArchive(game);
        List<AgvnArt> art = AgvnCoverSources.candidates(packed);
        assertEquals("archive.rpa:gui/main_menu.png", art.get(0).name);
        assertEquals("archive.rpa:images/Title BG.jpg", art.get(1).name);
        assertArrayEquals(AgvnGameArchivesTest.filled(20, 'm'), art.get(0).read());
        assertEquals(2, art.size());
    }

    @Test
    public void renpyArtNames() {
        for (String yes : new String[]{"images/bg_menu.png", "title1.png", "gui/main_menu_background.webp", "keyart.jpg",
                "images/Title Screen 2.jpg".toLowerCase(java.util.Locale.ROOT), "mm-bg.png", "bg_title.jpg", "splash.png"}) {
            assertTrue(yes, AgvnRenpyArt.isArt(yes));
        }
        for (String no : new String[]{"menu.png", "gui/overlay/main_menu.png", "images/bg/park.jpg", "gui/button/title.png",
                "gui/main_menu.png", "gui/game_menu.jpg", "title.txt", "subtitle.png"}) {
            assertTrue(no, !AgvnRenpyArt.isArt(no));
        }
    }

    @Test
    public void renpyIconUnlessDefault() throws Exception {
        File root = tmp.newFolder("Icon");
        File icon = new File(root, "game/gui/window_icon.png");
        Files.createDirectories(icon.getParentFile().toPath());
        Files.write(icon.toPath(), png(250, 250)); // Ren'Py's own, recoloured
        assertNull(AgvnCoverSources.icon(root));
        Files.write(icon.toPath(), png(256, 256));
        assertNotNull(AgvnCoverSources.icon(root));
        assertArrayEquals(png(256, 256), AgvnCoverSources.icon(root).read());
        assertNull(AgvnCoverSources.icon(tmp.newFolder("NoRenpy")));
    }

    @Test
    public void mvTitleFromSystemJsonAlsoEncrypted() throws Exception {
        File root = tmp.newFolder("MV");
        File titles = new File(root, "www/img/titles1");
        write(new File(titles, "Big.png"), 700);
        byte[] castle = png(816, 624);
        Files.write(new File(titles, "Castle.rpgmvp").toPath(), encryptMv(castle));
        write(new File(titles, "Broken.png_"), 900);
        Files.createDirectories(new File(root, "www/data").toPath());
        Files.write(new File(root, "www/data/System.json").toPath(),
                "\uFEFF{\"gameTitle\":\"X\",\"title1Name\":\"Castle\",\"hasEncryptedImages\":true}".getBytes(StandardCharsets.UTF_8));
        List<AgvnArt> art = AgvnCoverSources.candidates(root);
        assertEquals(Arrays.asList("www/img/titles1/Castle.rpgmvp", "www/img/titles1/Broken.png_", "www/img/titles1/Big.png"), names(root, art));
        assertArrayEquals(castle, art.get(0).read()); // restored without the key
        assertNull(art.get(1).read()); // not an RPGMV file: the next one is used
        assertEquals("Castle", AgvnMvArt.titleName(new File(root, "www/data/System.json")));
    }

    @Test
    public void rgssTitleLooseNamedBySystem() throws Exception {
        File root = tmp.newFolder("Ace");
        write(new File(root, "Graphics/Titles1/Small.png"), 100);
        write(new File(root, "Graphics/Titles1/Large.jpg"), 900);
        write(new File(root, "Graphics/Titles2/Frame.png"), 2000); // a frame over the title, not the picture
        Files.createDirectories(new File(root, "Data").toPath());
        Files.write(new File(root, "Data/System.rvdata2").toPath(), marshalSystem("@title1_name", "Small", true));
        assertEquals(Arrays.asList("Graphics/Titles1/Small.png", "Graphics/Titles1/Large.jpg"), names(root, AgvnCoverSources.candidates(root)));

        File vx = tmp.newFolder("VX");
        write(new File(vx, "Graphics/System/Title.png"), 300);
        write(new File(vx, "Graphics/System/Window.png"), 3000);
        assertEquals(Arrays.asList("Graphics/System/Title.png"), names(vx, AgvnCoverSources.candidates(vx)));
    }

    @Test
    public void rgssTitlePackedInArchive() throws Exception {
        File root = tmp.newFolder("XP");
        byte[] title = png(640, 480);
        AgvnGameArchivesTest.writeRgss(new File(root, "Game.rgssad"), 1,
                new String[]{"Data\\System.rxdata", "Graphics\\Titles\\Other.png", "Graphics\\Titles\\Hero.png"},
                new byte[][]{marshalSystem("@title_name", "Hero", false), new byte[5000], title});
        List<AgvnArt> art = AgvnCoverSources.candidates(root);
        assertEquals("Game.rgssad:Graphics/Titles/Hero.png", art.get(0).name);
        assertArrayEquals(title, art.get(0).read());
        assertEquals("Game.rgssad:Graphics/Titles/Other.png", art.get(1).name);
    }

    @Test
    public void rgssTitleFromRtpOnThePhone() throws Exception {
        File root = tmp.newFolder("UsesRtp");
        Files.write(new File(root, "Game.ini").toPath(),
                "[Game]\nRTP=RPGVXAce\nLibrary=System\\RGSS301.dll\n".getBytes(StandardCharsets.US_ASCII));
        Files.createDirectories(new File(root, "Data").toPath());
        Files.write(new File(root, "Data/System.rvdata2").toPath(), marshalSystem("@title1_name", "Book", true));
        write(new File(root, "Graphics/Titles1/Unused.png"), 30);
        File rtpRoot = tmp.newFolder("RTP");
        write(new File(rtpRoot, "RPGVXAce/Graphics/Titles1/Book.png"), 60);
        List<AgvnArt> art = AgvnCoverSources.candidates(root, rtpRoot);
        assertEquals(new File(rtpRoot, "RPGVXAce/Graphics/Titles1/Book.png").getPath(), art.get(0).name);
        assertEquals(Arrays.asList("Graphics/Titles1/Unused.png"), names(root, AgvnCoverSources.candidates(root)));
    }

    @Test
    public void readsTitleNameFromMarshal() {
        assertEquals("Book", AgvnRgssArt.titleName(marshalSystem("@title1_name", "Book", true)));
        assertEquals("001-Title01", AgvnRgssArt.titleName(marshalSystem("@title_name", "001-Title01", false)));
        assertNull(AgvnRgssArt.titleName(marshalSystem("@title1_name", "", true)));
        assertNull(AgvnRgssArt.titleName(new byte[]{4, 8, '0'}));
    }

    @Test
    public void namedCoverComesFirst() throws Exception {
        File root = tmp.newFolder("Both");
        write(new File(root, "Graphics/Titles1/T.png"), 50);
        write(new File(root, "Poster.png"), 5);
        assertEquals(Arrays.asList("Poster.png", "Graphics/Titles1/T.png"), names(root, AgvnCoverSources.candidates(root)));
        assertTrue(AgvnCoverSources.candidates(new File(root, "missing")).isEmpty());
    }

    /** RPG::System as Marshal writes it: only the parts the reader looks at, around the title's name. */
    static byte[] marshalSystem(String attribute, String title, boolean ruby19) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(4);
        out.write(8);
        put(out, "o:\u0010RPG::System\u0007:\u0011@window_skin\"\u0006X".getBytes(StandardCharsets.ISO_8859_1));
        out.write(':');
        out.write(attribute.length() + 5);
        put(out, attribute.getBytes(StandardCharsets.US_ASCII));
        if (ruby19) out.write('I');
        out.write('"');
        out.write(title.length() + 5);
        put(out, title.getBytes(StandardCharsets.UTF_8));
        if (ruby19) put(out, new byte[]{6, ':', 6, 'E', 'T'});
        return out.toByteArray();
    }

    private static void put(ByteArrayOutputStream out, byte[] b) {
        out.write(b, 0, b.length);
    }

    static byte[] png(int w, int h) {
        byte[] b = new byte[40];
        byte[] head = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
        System.arraycopy(head, 0, b, 0, head.length);
        for (int i = 0; i < 4; i++) {
            b[16 + i] = (byte) (w >>> (24 - 8 * i));
            b[20 + i] = (byte) (h >>> (24 - 8 * i));
        }
        return b;
    }

    /** As RPG Maker MV deploys an image: the "RPGMV" header, then the PNG with its first 16 bytes XOR-ed with the key. */
    static byte[] encryptMv(byte[] png) {
        byte[] out = new byte[png.length + 16];
        System.arraycopy("RPGMV\0\0\0\0\u0003\u0001\0\0\0\0\0".getBytes(StandardCharsets.ISO_8859_1), 0, out, 0, 16);
        System.arraycopy(png, 0, out, 16, png.length);
        for (int i = 0; i < 16; i++) out[16 + i] ^= (byte) (0x5A + i);
        return out;
    }

    private static void write(File f, int size) throws Exception {
        Files.createDirectories(f.getParentFile().toPath());
        Files.write(f.toPath(), new byte[size]);
    }

    private static List<String> names(File root, List<AgvnArt> art) {
        List<String> out = new ArrayList<>();
        for (AgvnArt a : art) out.add(a.name.startsWith(root.getPath()) ? a.name.substring(root.getPath().length() + 1) : a.name);
        return out;
    }
}
