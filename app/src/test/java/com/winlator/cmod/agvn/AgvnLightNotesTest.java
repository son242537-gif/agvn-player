/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * What a "Chạy nhẹ" HTML game leaves for "Gửi nhật ký" besides logcat: Vệ Tinh Đức (an RPG Maker web build) showed no
 * text on a HONOR AAK-AN00 (16/10/2026), whose logcat came encrypted, and its session file said only that it started.
 */
public class AgvnLightNotesTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void aListKeepsTheFirstDifferentItems() {
        String list = AgvnLightSession.added("", "/fonts/VN Font.ttf", 3);
        assertEquals("/fonts/VN Font.ttf", list);
        list = AgvnLightSession.added(list, "/data/Map001.json", 3);
        assertEquals("/fonts/VN Font.ttf | /data/Map001.json", list);
        assertNull("already there", AgvnLightSession.added(list, "/fonts/VN Font.ttf", 3));
        list = AgvnLightSession.added(list, "/audio/se/Cursor1.ogg", 3);
        assertNull("full", AgvnLightSession.added(list, "/audio/se/Cancel1.ogg", 3));
        assertEquals(3, list.split(" \\| ").length);
    }

    @Test
    public void aSoundAskedForAgainAndAgainIsToldOnce() {
        List<String> told = new ArrayList<>();
        Consumer<String> keeper = AgvnLightSession.firstDifferent(2, told::add);
        for (int i = 0; i < 50; i++) keeper.accept("/audio/se/Cursor1.ogg"); // MV fetches a sound each time it plays
        keeper.accept(null);
        keeper.accept("/fonts/VN Font.ttf");
        keeper.accept("/data/Map001.json"); // past the limit: no more reads and writes of the session's file
        assertEquals(Arrays.asList("/audio/se/Cursor1.ogg", "/fonts/VN Font.ttf"), told);
    }

    @Test
    public void aMissingFileIsToldAndAMissingPictureIsShownEmpty() throws IOException {
        File root = tmp.newFolder("game");
        List<String> standIns = new ArrayList<>(), missing = new ArrayList<>();
        AgvnHtmlFiles.serve(root, "index.html", "/fonts/VN%20Font.ttf", null, standIns::add, missing::add);
        AgvnHtmlFiles.serve(root, "index.html", "/img/system/Window.png", null, standIns::add, missing::add);
        assertEquals(Collections.singletonList("/fonts/VN Font.ttf (not-found in the game folder)"), missing);
        assertEquals(Collections.singletonList("/img/system/Window.png (not-found in the game folder)"), standIns);
    }

    /** Why a picture was shown empty: "missing" could not tell a file not there from one the app cannot read. */
    @Test
    public void aPictureShownEmptySaysWhy() throws IOException {
        File root = tmp.newFolder("mz");
        File system = new File(root, "img/system");
        assertTrue(system.mkdirs());
        Files.write(new File(system, "Window.png_").toPath(), new byte[0]);
        Files.write(new File(root, "audio").toPath(), new byte[1]); // a file where the game has a folder
        assertEquals("not-found in img/system", AgvnHtmlStandIn.why(root, "/IMG/System/ButtonSet.png_", null));
        assertEquals("not-found in img", AgvnHtmlStandIn.why(root, "/img/pictures/a.png", null));
        assertEquals("not-found in the game folder", AgvnHtmlStandIn.why(root, "/movies/a.webm", null));
        assertEquals("not-found (audio is a file)", AgvnHtmlStandIn.why(root, "/audio/se/a.ogg", null));
        assertEquals("short: 0 bytes", AgvnHtmlStandIn.why(root, "/img/system/Window.png_", new File(system, "Window.png_")));
        assertEquals("not-found (outside the game folder)", AgvnHtmlStandIn.why(root, "/../x.png", null));
        // "no-access to …" (a folder that cannot be listed) needs a phone without storage permission: tests run as root
        assertTrue(AgvnHtmlStandIn.systemPicture("/img/system/ButtonSet.png_ (not-found in img/system)"));
        assertTrue(AgvnHtmlStandIn.systemPicture("/Img/System/IconSet.png (short: 0 bytes)"));
        assertTrue(!AgvnHtmlStandIn.systemPicture("/img/pictures/a.png (not-found in img/pictures)"));
        assertTrue(!AgvnHtmlStandIn.systemPicture(null));
    }

    /** The bar's words for the first system picture shown empty. */
    @Test
    public void thePlayerIsToldWhichFileAndWhy() {
        String item = "/img/system/ButtonSet.png_ (not-found in img/system)";
        assertEquals("img/system/ButtonSet.png_", AgvnHtmlPictureNotice.file(item));
        assertEquals("not-found in img/system", AgvnHtmlPictureNotice.why(item));
        assertEquals("không có trong img/system", AgvnHtmlPictureNotice.reason("not-found in img/system"));
        assertEquals("không có trong thư mục game", AgvnHtmlPictureNotice.reason("not-found in the game folder"));
        assertEquals("app không đọc được thư mục img/system", AgvnHtmlPictureNotice.reason("no-access to img/system"));
        assertEquals("tệp chỉ có 0 byte, không phải ảnh", AgvnHtmlPictureNotice.reason("short: 0 bytes"));
        assertEquals("không có tệp này", AgvnHtmlPictureNotice.reason(""));
        assertEquals("img/a (b).png", AgvnHtmlPictureNotice.file("/img/a (b).png (not-found in img)"));
        assertEquals("", AgvnHtmlPictureNotice.why("/img/a.png"));
    }

    @Test
    public void aFileThatIsThereTellsNothing() throws IOException {
        File root = tmp.newFolder("game");
        File fonts = new File(root, "fonts");
        assertTrue(fonts.mkdirs());
        Files.write(new File(fonts, "gamefont.css").toPath(), "@font-face {}".getBytes(StandardCharsets.UTF_8));
        List<String> told = new ArrayList<>();
        AgvnHtmlFiles.serve(root, "index.html", "/Fonts/GameFont.css", null, told::add, told::add); // case ignored
        AgvnHtmlFiles.serve(root, "index.html", "/fonts/gamefont.css", null, null, null); // a HEAD: nobody to tell
        assertEquals(Collections.emptyList(), told);
    }

    @Test
    public void aPageMessageNamesItsFileInTheGameFolder() {
        assertEquals("Uncaught TypeError: this.textColor is not a function (/js/plugins/Việt Hoá.js:12)",
                AgvnHtmlConsole.brief("Uncaught TypeError: this.textColor is not a function",
                        "https://g.agvn.game/js/plugins/Vi%E1%BB%87t%20Ho%C3%A1.js", 12));
        assertEquals("a plus is a plus", "Failed (/js/plugins/A+B.js:3)",
                AgvnHtmlConsole.brief("Failed", "https://g.agvn.game/js/plugins/A+B.js", 3));
        assertEquals("no script to name", "Script error.", AgvnHtmlConsole.brief("Script error.", "", 0));
        assertEquals("Script error.", AgvnHtmlConsole.brief("Script error.", null, 0));
    }
}
