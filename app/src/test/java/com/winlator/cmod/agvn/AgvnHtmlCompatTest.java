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
    public void bundledCompatScriptNamesSavesAsTheAppTakesThem() throws Exception {
        // a plugin's own data gets its PC file name only when the app will take it: the page's rule is AgvnHtmlSaves's
        // (tools/agvn/tests/html_saves_sim.js plays it through with the rule read from AgvnHtmlSaves.java)
        assertTrue(compatJs().contains("'" + AgvnHtmlSaves.FILE.pattern().replace("\\", "\\\\") + "'"));
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
        // MZ: a narrow img/system/ButtonSet stretched, not "ButtonSet image is too small" (scripts/agvn/html runs it)
        assertTrue(js.contains("function wideButtons()") && js.contains("SM.run = function () { wideButtons();"));
    }

    @Test
    public void bundledCompatScriptLeavesTapsOnThePagesFieldsToTheBrowser() throws Exception {
        // RPG Maker prevents every touchstart inside the game, so a plugin's <input> and button over it took no tap
        // (tools/agvn/tests/html_form_taps_sim.js plays it through with the engines' own handlers)
        String js = compatJs();
        assertTrue(js.contains("function formTaps()") && js.contains("wideButtons(); formTaps(); return run.apply(this, arguments);"));
        // a game's language by its exe's name: path.parse(process.execPath).name
        assertTrue(js.contains("parse: function (p)") && js.contains("window.__agvnExe"));
        // also before the game starts, and when it bound its handler before html-compat.js could wrap it
        // (tools/agvn/tests/html_touch_sim.js dispatches through window, document and the field as a browser does)
        assertTrue("as soon as rpg_core has run", js.contains("document.addEventListener('load', function scripts()"));
        assertTrue("a handler bound before that", js.contains("G.isInsideCanvas = function () { return during ? false"));
        // a box that covers its own OK button on a shrunk game is narrowed, never on a game at full size
        assertTrue(js.contains("function fitField(f)") && js.contains("!(G._realScale < 0.95)"));
    }

    @Test
    public void bundledCompatScriptShrinksAPictureLargerThanTheGpuTakes() throws Exception {
        // Cultivator (MV, Mali-G610): "texImage2D: width or height out of range", then its events drew black
        // (tools/agvn/tests/html_texture_sim.js uploads a 768x12288 tileset as PIXI does)
        String js = compatJs();
        assertTrue(js.contains("function fitTextures(proto)") && js.contains("gl.getParameter(gl.MAX_TEXTURE_SIZE)"));
        for (String gl : new String[]{"WebGLRenderingContext", "WebGL2RenderingContext"})
            assertTrue(gl, js.contains("fitTextures(window." + gl + " && window." + gl + ".prototype);"));
    }

    /** A game may pick its language by path.parse(process.execPath).name: the exe it runs as on Windows. */
    @Test
    public void theExeAPageGameRunsAsIsTheOneItsShortcutStarts() throws Exception {
        File game = new File(tmp.getRoot(), "game");
        touch("game/Game.exe");
        touch("game/Game_E.exe");
        touch("game/GameLauncher.exe");
        File index = touch("game/www/index.html");
        assertEquals("Game_E.exe", AgvnHtmlExe.name(new File(game, "Game_E.exe"), game));
        assertEquals("the folder's main exe: Game.exe before Game_E.exe", "Game.exe", AgvnHtmlExe.name(null, game));
        assertEquals("never a tool", "Game.exe", AgvnHtmlExe.name(new File(game, "GameLauncher.exe"), game));
        touch("tools/Setup.exe");
        assertEquals(AgvnHtmlExe.DEFAULT, AgvnHtmlExe.name(null, new File(tmp.getRoot(), "tools")));
        assertEquals("an MV game's folder is the one above www/", "window.__agvnExe = 'Game.exe';\n",
                AgvnHtmlExe.line(null, AgvnLightGame.folderOf(index)));
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

    /** An RPG Maker MV game on NW.js reads names relative to its folder with package.json, above the page's www/. */
    @Test
    public void aNameReadRelativeToAnMvGamesFolderIsFoundAboveWww() throws Exception {
        File lng = touch("mv/lng.txt"); // fs.readFileSync("lng.txt") at the top of the game's rpg_managers.js
        touch("mv/package.json");
        File data = touch("mv/www/data/System.json");
        File www = data.getParentFile().getParentFile();
        assertEquals(lng, AgvnHtmlFiles.fileFor(www, "/lng.txt"));
        assertEquals("a plugin's path.join(process.cwd(), 'www/data/...')", data, AgvnHtmlFiles.fileFor(www, "/www/data/System.json"));
        assertEquals("the page's own file first", data, AgvnHtmlFiles.fileFor(www, "/data/System.json"));
        assertNull(AgvnHtmlFiles.fileFor(www, "/missing.txt"));
        assertEquals(new File(tmp.getRoot(), "mv"), AgvnNwjsGame.appDir(www));

        touch("mz/lng.txt");
        touch("mz/game/package.json"); // MZ, or an NW.js page game: package.json beside the page, which is the root
        File mz = new File(tmp.getRoot(), "mz/game");
        assertNull(AgvnNwjsGame.appDir(mz));
        assertNull("nothing above the game's own folder", AgvnHtmlFiles.fileFor(mz, "/lng.txt"));
        touch("web/lng.txt");
        touch("web/page/index.html"); // no package.json anywhere: a plain web game, served from its folder only
        assertNull(AgvnHtmlFiles.fileFor(new File(tmp.getRoot(), "web/page"), "/lng.txt"));
        assertNull(AgvnNwjsGame.appDir(null));
    }
}
