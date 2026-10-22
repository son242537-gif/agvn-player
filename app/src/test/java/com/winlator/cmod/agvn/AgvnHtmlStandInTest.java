/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/** An empty picture in place of a missing or broken RPG Maker MV/MZ encrypted one (Yarisutemesubuta, 10/10/2026). */
public class AgvnHtmlStandInTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String KEY = "d41d8cd98f00b204e9800998ecf8427e";

    private File game(String systemJson) throws IOException {
        File root = tmp.newFolder();
        File data = new File(root, "data");
        assertTrue(data.mkdirs());
        if (systemJson != null) {
            Files.write(new File(data, "System.json").toPath(), systemJson.getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(new File(root, "img/pictures").mkdirs());
        return root;
    }

    private static File file(File root, String path, int size) throws IOException {
        File f = new File(root, path);
        Files.write(f.toPath(), new byte[size]);
        return f;
    }

    @Test
    public void anEmptyOrMissingPictureIsAnEmptyOneTheEngineCanDecrypt() throws IOException {
        File root = game("{\"gameTitle\":\"Yari\",\"hasEncryptedImages\":true,\"encryptionKey\":\"" + KEY + "\"}");
        byte[] png = AgvnHtmlFiles.TRANSPARENT_PNG;
        // the report: an empty file, which stopped the game with "RangeError: Invalid typed array length: 16"
        File empty = file(root, "img/pictures/Ero1.rpgmvp", 0);
        byte[] out = AgvnHtmlStandIn.bytesFor(root, "/img/pictures/Ero1.rpgmvp", empty, png);
        assertNotNull(out);
        assertArrayEquals("MV and MZ check the header byte by byte", AgvnHtmlStandIn.HEADER, Arrays.copyOf(out, 16));
        byte[] body = Arrays.copyOfRange(out, 16, out.length); // as Decrypter.decryptArrayBuffer does
        byte[] key = AgvnHtmlStandIn.key(root);
        for (int i = 0; i < 16; i++) body[i] ^= key[i];
        assertArrayEquals(png, body);
        assertArrayEquals("what the app's own reader makes of it", png, AgvnArt.restorePng(out));
        // missing, or shorter than the header and the 16 bytes the key hides; MZ's .png_ too
        assertNotNull(AgvnHtmlStandIn.bytesFor(root, "/img/pictures/gone.rpgmvp", null, png));
        File short31 = file(root, "img/pictures/a.png_", 31);
        assertNotNull(AgvnHtmlStandIn.bytesFor(root, "/img/pictures/a.png_", short31, png));
        // a whole one is the game's, and other files are not pictures to stand in for
        File whole = file(root, "img/pictures/b.rpgmvp", 32);
        assertNull(AgvnHtmlStandIn.bytesFor(root, "/img/pictures/b.rpgmvp", whole, png));
        assertNull(AgvnHtmlStandIn.bytesFor(root, "/audio/bgm/c.rpgmvo", null, png));
        assertNull(AgvnHtmlStandIn.bytesFor(root, "/img/pictures/d.png", null, png));
    }

    @Test
    public void aPlainFileTheGameLeftIsServedForItsEncryptedName() throws IOException {
        // Cultivator: img/system/Window.png plain, asked for as Window.rpgmvp; shown empty, every text was black
        File root = game("{\"hasEncryptedImages\":true,\"encryptionKey\":\"" + KEY + "\"}");
        assertTrue(new File(root, "img/system").mkdirs());
        byte[] window = "the game's own window skin, colours and all".getBytes(StandardCharsets.UTF_8);
        Files.write(new File(root, "img/system/Window.png").toPath(), window);
        byte[] out = AgvnHtmlStandIn.plainFor(root, "/img/system/Window.rpgmvp", null);
        assertNotNull(out);
        assertArrayEquals(AgvnHtmlStandIn.HEADER, Arrays.copyOf(out, 16));
        byte[] body = Arrays.copyOfRange(out, 16, out.length); // as Decrypter.decryptArrayBuffer does
        byte[] key = AgvnHtmlStandIn.key(root);
        for (int i = 0; i < 16; i++) body[i] ^= key[i];
        assertArrayEquals("the engine reads the plain picture", window, body);
        // MZ's .png_, and a sound (MV .rpgmvo, MZ .ogg_) the same way; a broken encrypted file gives way to it too
        assertNotNull(AgvnHtmlStandIn.plainFor(root, "/img/system/Window.png_", null));
        assertTrue(new File(root, "audio/se").mkdirs());
        Files.write(new File(root, "audio/se/Cursor1.ogg").toPath(), window);
        assertNotNull(AgvnHtmlStandIn.plainFor(root, "/audio/se/Cursor1.rpgmvo", null));
        assertNotNull(AgvnHtmlStandIn.plainFor(root, "/audio/se/Cursor1.ogg_", null));
        File broken = file(root, "img/system/W.rpgmvp", 0);
        assertNotNull(AgvnHtmlStandIn.plainFor(root, "/img/system/Window.rpgmvp", broken));
        // the game's own whole encrypted file, no plain file, a plain name, or no key: nothing in its place
        File whole = file(root, "img/system/Window.rpgmvp", 64);
        assertNull(AgvnHtmlStandIn.plainFor(root, "/img/system/Window.rpgmvp", whole));
        assertNull(AgvnHtmlStandIn.plainFor(root, "/img/system/IconSet.rpgmvp", null));
        assertNull(AgvnHtmlStandIn.plainFor(root, "/img/system/Window.png", null));
        File noKey = game(null);
        assertTrue(new File(noKey, "img/system").mkdirs());
        Files.write(new File(noKey, "img/system/Window.png").toPath(), window);
        assertNull(AgvnHtmlStandIn.plainFor(noKey, "/img/system/Window.rpgmvp", null));
    }

    @Test
    public void withoutTheGamesKeyNothingStandsIn() throws IOException {
        File noSystem = game(null);
        assertNull(AgvnHtmlStandIn.key(noSystem));
        assertNull(AgvnHtmlStandIn.bytesFor(noSystem, "/img/pictures/a.rpgmvp", null, AgvnHtmlFiles.TRANSPARENT_PNG));
        File noKey = game("{\"hasEncryptedImages\":false,\"encryptionKey\":\"\"}");
        assertNull(AgvnHtmlStandIn.key(noKey));
        File key = game("{\"encryptionKey\" : \"" + KEY.toUpperCase() + "\"}");
        assertEquals(16, AgvnHtmlStandIn.key(key).length);
        assertEquals((byte) 0xd4, AgvnHtmlStandIn.key(key)[0]);
    }

    @Test
    public void encryptedPicturesByTheirExtension() {
        assertTrue(AgvnHtmlStandIn.encryptedPicture("/img/pictures/Actor1.rpgmvp"));
        assertTrue(AgvnHtmlStandIn.encryptedPicture("/img/faces/Actor1.PNG_"));
        assertFalse(AgvnHtmlStandIn.encryptedPicture("/img/faces/Actor1.png"));
        assertFalse(AgvnHtmlStandIn.encryptedPicture("/audio/se/Cursor1.rpgmvo"));
        assertFalse(AgvnHtmlStandIn.encryptedPicture(null));
    }
}
