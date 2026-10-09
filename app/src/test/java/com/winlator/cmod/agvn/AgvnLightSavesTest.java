/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** "Nhập save" / "Xuất save" for "Chạy nhẹ" games: RPG Maker MV/MZ saves as files, and Godot's user:// folder. */
public class AgvnLightSavesTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void rpgMakerSavesAreFilesAsOnAPc() throws IOException {
        File www = tmp.newFolder("Game", "www");
        AgvnHtmlSaves saves = new AgvnHtmlSaves(www);
        assertTrue(saves.writable());
        assertFalse("no probe left", new File(www, "save/.agvn-probe").exists());
        assertFalse("a save folder that cannot be made: saves stay in the browser",
                new AgvnHtmlSaves(tmp.newFile("index-is-a-file")).writable());
        assertFalse(saves.any());
        assertNull(saves.read("file1.rpgsave"));
        assertTrue(saves.write("file1.rpgsave", "N4IgdghgtgpiBcIDKALCBjAFgSwDYA"));
        // MZ's saves are pako's text with characters up to 255: written as UTF-8, as NW.js's fs.writeFileSync does
        String mz = "x\u009cåÿ\u0001";
        assertTrue(saves.write("file0.rmmzsave", mz));
        File mzFile = new File(www, "save/file0.rmmzsave");
        assertArrayEquals(mz.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(mzFile.toPath()));
        assertEquals(mz, saves.read("file0.rmmzsave"));
        assertTrue(saves.any() && saves.exists("file1.rpgsave"));
        assertTrue(saves.write("file1.rpgsave", "second"));
        assertEquals("second", saves.read("file1.rpgsave"));
        assertFalse("no part file left", new File(www, "save/file1.rpgsave.agvn-part").exists());
        saves.remove("file1.rpgsave");
        assertFalse(saves.exists("file1.rpgsave"));
        String[] notSaves = {"../file1.rpgsave", "save/file1.rpgsave", "file1.json", "file1.rpgsave.bak", "", null};
        for (String bad : notSaves) {
            assertFalse(bad, AgvnHtmlSaves.valid(bad));
            assertFalse(bad, saves.write(bad, "x"));
        }
        assertTrue(AgvnHtmlSaves.valid("config.rmmzsave") && AgvnHtmlSaves.valid("global.rpgsave"));
        // where the library's "Nhập save" / "Xuất save" look for an MV game made for PC: the same folder
        File game = www.getParentFile();
        assertEquals(new File(www, "save"), AgvnSaveLocations.inGameFolder(game, "RPGMAKER_MV").get(0).dir);
    }

    @Test
    public void pluginsNameTheirOwnDataAsOnAPc() throws IOException {
        // an achievements plugin's "fileMy Plugin Data.rpgsave" was refused, and its game stopped on "Cannot write"
        // (the maintainer's report of 09/10/2026)
        String[] names = {"fileMy Plugin Data.rpgsave", "mydata.rpgsave", "my data (1).rpgsave", "Thành tựu.rmmzsave",
                "Tha\u0300nh tu\u0323u.rmmzsave", "セーブデータ[2].rpgsave", "a.b.rpgsave", "file1.rpgsave"};
        File www = tmp.newFolder("www");
        AgvnHtmlSaves saves = new AgvnHtmlSaves(www);
        for (String name : names) {
            assertTrue(name, AgvnHtmlSaves.valid(name));
            if (!nameable(new File(www, "save/" + name))) continue;
            assertTrue(name, saves.write(name, "N4Ig"));
            assertEquals(name, "N4Ig", saves.read(name));
        }
        String sixty = new String(new char[60]).replace('\0', 'ạ');
        if (nameable(new File(www, sixty))) // 60 letters of 3 bytes, with the part file still a file name
            assertTrue(saves.write(sixty + ".rpgsave", "x") && new File(www, "save/" + sixty + ".rpgsave").isFile());
        String[] refused = {sixty + "a.rpgsave", " lead.rpgsave", ".hidden.rpgsave", "a:b.rpgsave", "a?.rpgsave",
                "a\\b.rpgsave", "a/b.rpgsave", "a*b.rpgsave", "a\"b.rpgsave", "a<b>.rpgsave", "a|b.rpgsave", "file1.RPGSAVE"};
        for (String name : refused) assertFalse(name, AgvnHtmlSaves.valid(name));
    }

    /** False where file names cannot hold {@code f}'s: Linux in the POSIX locale (the cloud). Android's are UTF-8. */
    private static boolean nameable(File f) {
        try {
            f.toPath();
            return true;
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /** project.binary as Godot writes it: "ECFG", a count, then keys and their encoded values. */
    private static byte[] settings(Map<String, Object> read) {
        Map<String, Object> values = new HashMap<>(read);
        values.put("display/window/size/viewport_width", 1280); // a setting this does not read, skipped
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        put(out, le(8).putInt(0x47464345).putInt(values.size()));
        for (Map.Entry<String, Object> e : values.entrySet()) {
            byte[] key = e.getKey().getBytes(StandardCharsets.UTF_8);
            put(out, le(4).putInt(key.length));
            out.write(key, 0, key.length);
            ByteBuffer v;
            if (e.getValue() instanceof String) {
                byte[] text = ((String) e.getValue()).getBytes(StandardCharsets.UTF_8);
                v = le(8 + (text.length + 3) / 4 * 4).putInt(4).putInt(text.length).put(text);
            } else if (e.getValue() instanceof Boolean) {
                v = le(8).putInt(1).putInt((Boolean) e.getValue() ? 1 : 0);
            } else {
                v = le(8).putInt(2).putInt((Integer) e.getValue());
            }
            put(out, le(4).putInt(v.capacity()));
            put(out, v);
        }
        return out.toByteArray();
    }

    @Test
    public void godotSavesWhereItsWindowsVersionDoes() throws IOException {
        Map<String, Object> party = new HashMap<>();
        party.put(AgvnGodotUserDir.NAME, "Party Me");
        assertEquals("AppData/Roaming/Godot/app_userdata/Party Me", userDir(party));
        Map<String, Object> own = new HashMap<>();
        own.put(AgvnGodotUserDir.NAME, "Truyện: Mùa hè?");
        own.put(AgvnGodotUserDir.OWN, true);
        assertEquals("AppData/Roaming/Truyện- Mùa hè-", userDir(own));
        own.put(AgvnGodotUserDir.OWN_NAME, "Studio/Game..");
        assertEquals("its own folder: paths allowed, \"..\" not", "AppData/Roaming/Studio/Game-", userDir(own));
        assertEquals("AppData/Roaming/Godot/app_userdata/[unnamed project]", userDir(Collections.emptyMap()));
        assertEquals("a/b", AgvnGodotUserDir.safe(" a\\b ", true));
        assertEquals("a-b", AgvnGodotUserDir.safe("a/b", false));
        assertEquals("twodots", AgvnGodotUserDir.safe("..", false));
        byte[] notSettings = "nope".getBytes(StandardCharsets.US_ASCII);
        assertTrue("not a project.binary", AgvnGodotUserDir.settings(notSettings).isEmpty());

        // read from the game's pack, beside its exe, as "Chạy nhẹ" and the library find it
        File dir = tmp.newFolder("PartyMe");
        Files.write(new File(dir, "PartyMe.pck").toPath(), pack(settings(party)));
        File exe = new File(dir, "PartyMe.exe");
        Files.write(exe.toPath(), "MZ".getBytes(StandardCharsets.US_ASCII));
        assertEquals("AppData/Roaming/Godot/app_userdata/Party Me", AgvnGodotUserDir.of(exe));
        assertEquals(Collections.singletonList("AppData/Roaming/Godot/app_userdata/Party Me"),
                AgvnSaveLocations.inProfile(exe, dir, "GODOT", Collections.emptyList()));
    }

    private static String userDir(Map<String, Object> values) {
        return AgvnGodotUserDir.folder(AgvnGodotUserDir.settings(settings(values)));
    }

    /** A Godot 4.5+ pack (format 3, directory at the end) holding only project.binary. */
    private static byte[] pack(byte[] projectBinary) {
        byte[] name = "project.binary".getBytes(StandardCharsets.UTF_8);
        int padded = (name.length + 3) / 4 * 4, header = 104;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        put(out, le(header).putInt(AgvnGodotFiles.MAGIC).putInt(3).putInt(4).putInt(5).putInt(0).putInt(0)
                .putLong(header).putLong(header + projectBinary.length)); // file base, directory
        out.write(projectBinary, 0, projectBinary.length);
        put(out, le(8).putInt(1).putInt(padded));
        byte[] path = new byte[padded];
        System.arraycopy(name, 0, path, 0, name.length);
        out.write(path, 0, padded);
        put(out, le(36).putLong(0).putLong(projectBinary.length).put(new byte[16]).putInt(0));
        return out.toByteArray();
    }

    private static ByteBuffer le(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static void put(ByteArrayOutputStream out, ByteBuffer b) {
        out.write(b.array(), 0, b.capacity());
    }

    @Test
    public void godotForAndroidDrawsAsTheProjectSaysForPhones() {
        // "Tự sửa lỗi" offers the other renderer of a "Chạy nhẹ" Godot game: Compatibility is OpenGL, Mobile is Vulkan
        Map<String, Object> compat = new HashMap<>();
        compat.put(AgvnGodotUserDir.METHOD_MOBILE, "gl_compatibility");
        Map<String, Object> read = AgvnGodotUserDir.settings(settings(compat));
        assertEquals("gl_compatibility", read.get(AgvnGodotUserDir.METHOD_MOBILE));
        assertEquals("gl", AgvnGodotGame.lightRenderer("", read));
        assertEquals("a project that names none draws with Mobile", "vulkan",
                AgvnGodotGame.lightRenderer(null, AgvnGodotUserDir.settings(settings(Collections.emptyMap()))));
        assertEquals("vulkan", AgvnGodotGame.lightRenderer(null, null));
        assertEquals("the one picked before", "vulkan", AgvnGodotGame.lightRenderer("vulkan", read));
    }
}
