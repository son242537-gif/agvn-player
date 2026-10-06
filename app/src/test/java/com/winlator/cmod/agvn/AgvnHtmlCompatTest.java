package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class AgvnHtmlCompatTest {
    private static final String TAG = "<script src=\"" + AgvnHtmlFiles.COMPAT_PATH + "\"></script>";

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static String compatJs() throws Exception {
        return new String(Files.readAllBytes(new File("src/main/assets/agvn/html-compat.js").toPath()), StandardCharsets.UTF_8);
    }

    private File touch(String path) throws Exception {
        File f = new File(tmp.getRoot(), path);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), path.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    @Test
    public void compatScriptComesBeforeEveryGameScript() {
        assertEquals("<html><head>" + TAG + "<script src=\"js/rpg_core.js\"></script></head>",
                AgvnHtmlFiles.inject("<html><head><script src=\"js/rpg_core.js\"></script></head>"));
        assertEquals("<HEAD lang=\"ja\">" + TAG + "<title>x</title>", AgvnHtmlFiles.inject("<HEAD lang=\"ja\"><title>x</title>"));
        assertEquals("<header></header>" + TAG + "<script></script>", AgvnHtmlFiles.inject("<header></header><script></script>"));
        assertEquals(TAG + "<body></body>", AgvnHtmlFiles.inject("<body></body>"));
    }

    @Test
    public void placeholderPictureIsAValidPng() {
        byte[] png = AgvnHtmlFiles.TRANSPARENT_PNG;
        assertEquals((byte) 0x89, png[0]);
        assertEquals("PNG", new String(png, 1, 3, StandardCharsets.US_ASCII));
    }

    @Test
    public void bundledCompatScriptKeepsTheEngineOffNwjs() throws Exception {
        String js = compatJs();
        // RPG Maker treats require + typeof process === 'object' as NW.js and would then save through require('fs'),
        // whose stand-in keeps files in the browser; its browser save functions go to real files instead
        // (window.AgvnSaves). `process` is a function for plugins that read it (tools/agvn/tests/html_compat_sim.js)
        assertTrue(js.contains("window.require = function"));
        assertTrue(js.contains("var proc = function process() {};") && js.contains("window.process = proc;"));
        assertTrue(!js.contains("window.process = {"));
        assertTrue(js.contains("SM.catchException = function"));
        assertTrue(js.contains("filesForSaves(window.StorageManager);"));
        assertTrue(js.contains("window.AgvnSaves") && AgvnHtmlSaves.NAME.equals("AgvnSaves"));
    }

    @Test
    public void bundledCompatScriptLetsRpgMakerMzMoveWhileItShows() throws Exception {
        // MZ updates its scene only while document.hasFocus(), and WebView can leave the page without the focus:
        // every MZ game stood still on its first scene (tools/agvn/tests/html_focus_sim.js plays it through)
        String js = compatJs();
        assertTrue(js.contains("document.hasFocus = function () { return document.visibilityState !== 'hidden'; };"));
    }

    @Test
    public void bundledCompatScriptTellsRpgMakerMvItRunsOnAPc() throws Exception {
        String js = compatJs();
        // a PC game ships .ogg sound and .webm video: on a phone MV asks for .m4a/.mp4 and every sound is missing
        assertTrue(js.contains("Utils.isMobileDevice = function () { return false; };"));
        assertTrue(js.contains("Utils.isAndroidChrome = function () { return false; };"));
        assertTrue("MV only: MZ always asks for .ogg", js.contains("if (!isMz) desktopMv();"));
        // the game still fills the screen and goes quiet when the player leaves the app, as on the phone
        assertTrue(js.contains("'_defaultStretchMode'") && js.contains("'_shouldMuteOnHide'"));
    }

    @Test
    public void aSoundOrVideoIsServedInTheFormatTheGameShips() throws Exception {
        File root = tmp.getRoot();
        File bgm = touch("audio/bgm/Theme.rpgmvo");
        File se = touch("audio/se/click.ogg");
        File me = touch("audio/me/fanfare.m4a");
        File movie = touch("movies/intro.webm");
        File mz = touch("audio/bgs/rain.ogg_");
        assertEquals("encrypted MV sound asked for as .rpgmvm", bgm, AgvnHtmlFiles.fileFor(root, "/audio/bgm/Theme.rpgmvm"));
        assertEquals("upper/lower case still ignored", bgm, AgvnHtmlFiles.fileFor(root, "/Audio/BGM/theme.rpgmvm"));
        assertEquals(se, AgvnHtmlFiles.fileFor(root, "/audio/se/click.m4a"));
        assertEquals("a phone build that ships .m4a", me, AgvnHtmlFiles.fileFor(root, "/audio/me/fanfare.ogg"));
        assertEquals(movie, AgvnHtmlFiles.fileFor(root, "/movies/intro.mp4"));
        assertEquals("encrypted MZ sound", mz, AgvnHtmlFiles.fileFor(root, "/audio/bgs/rain.m4a_"));
        assertNull(AgvnHtmlFiles.fileFor(root, "/audio/bgm/missing.rpgmvm"));
        assertNull(AgvnHtmlFiles.sibling("/img/pictures/a.png"));
        assertNull(AgvnHtmlFiles.sibling(null));
        File m4a = touch("audio/se/click.m4a");
        assertEquals("the exact file wins when both are there", m4a, AgvnHtmlFiles.fileFor(root, "/audio/se/click.m4a"));
    }
}
