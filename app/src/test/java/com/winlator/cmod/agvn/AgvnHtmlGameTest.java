package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

public class AgvnHtmlGameTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File file(File root, String rel, String content) throws Exception {
        File f = new File(root, rel);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    @Test
    public void findsIndexForMvMzAndTyranoOnly() throws Exception {
        File mv = tmp.newFolder("mv");
        file(mv, "www/index.html", "");
        file(mv, "www/js/rpg_core.js", "");
        assertEquals(new File(mv, "www/index.html"), AgvnHtmlGame.indexFor(mv, GameExeResolver.detectEngine(mv)));

        File mz = tmp.newFolder("mz");
        file(mz, "index.html", "");
        file(mz, "js/rmmz_core.js", "");
        assertEquals(new File(mz, "index.html"), AgvnHtmlGame.indexFor(mz, GameExeResolver.detectEngine(mz)));

        File tyrano = tmp.newFolder("tyrano");
        file(tyrano, "index.html", "");
        file(tyrano, "tyrano/tyrano.js", "");
        assertEquals(GameExeResolver.Engine.TYRANO, GameExeResolver.detectEngine(tyrano));
        assertEquals(new File(tyrano, "index.html"), AgvnHtmlGame.indexFor(tyrano, GameExeResolver.Engine.TYRANO));

        File packed = tmp.newFolder("packed"); // MV packed into package.nw: no index.html, stays on Wine
        file(packed, "www/js/rpg_core.js", "");
        assertNull(AgvnHtmlGame.indexFor(packed, GameExeResolver.Engine.RPGMAKER_MV));
        assertNull(AgvnHtmlGame.indexFor(mv, GameExeResolver.Engine.UNITY));
    }

    @Test
    public void runnerFollowsProfileElseHtmlWhenPossible() throws Exception {
        File index = file(tmp.getRoot(), "g/index.html", "");
        AgvnProfile p = AgvnProfile.defaultFor("g");
        assertTrue(AgvnHtmlGame.useHtml(p, index));
        assertTrue(AgvnHtmlGame.useHtml(null, index));
        p.runner = "wine";
        assertFalse(AgvnHtmlGame.useHtml(p, index));
        p.runner = "html";
        assertFalse(AgvnHtmlGame.useHtml(p, null));
        assertTrue(AgvnHtmlGame.isValidRunner(null));
        assertFalse(AgvnHtmlGame.isValidRunner("exe"));
    }

    @Test
    public void eachGameHasItsOwnOriginEvenForMvWww() throws Exception {
        File a = file(tmp.getRoot(), "Game A/www/index.html", "");
        File b = file(tmp.getRoot(), "Game B/www/index.html", "");
        File a2 = file(tmp.getRoot(), "sd/Game A/www/index.html", "");
        assertNotEquals(AgvnHtmlGame.hostFor(a), AgvnHtmlGame.hostFor(b));
        assertEquals(AgvnHtmlGame.hostFor(a), AgvnHtmlGame.hostFor(a2)); // moved folder keeps its saves
        assertTrue(AgvnHtmlGame.hostFor(a).matches("g[0-9a-f]+\\.agvn\\.game"));
    }

    @Test
    public void readsRunnerExtrasAndContainerIdFromDesktopFile() throws Exception {
        File desktop = file(tmp.getRoot(), "g.desktop", "[Desktop Entry]\nName=g\nExec=env wine \"/x/Game.exe\"\n\n"
                + "[Extra Data]\nagvnRunner=html\nagvnHtmlIndex=/x/www/index.html\ncontainer_id:3\n");
        Map<String, String> extras = AgvnHtmlGame.readExtras(desktop);
        assertEquals("html", extras.get(AgvnHtmlGame.EXTRA_RUNNER));
        assertEquals("/x/www/index.html", extras.get(AgvnHtmlGame.EXTRA_INDEX));
        assertNull(extras.get("Name"));
        assertEquals(3, AgvnHtmlGame.containerIdIn(desktop));
    }

    @Test
    public void servesFilesIgnoringCaseButNeverOutsideTheGame() throws Exception {
        File root = tmp.newFolder("www");
        File actor = file(root, "img/pictures/Actor1.png", "x");
        file(root, "audio/bgm/Theme 1.ogg", "x");
        file(tmp.getRoot(), "secret.txt", "x");
        assertEquals(actor, AgvnHtmlFiles.resolve(root, "/img/pictures/Actor1.png"));
        assertEquals(actor, AgvnHtmlFiles.resolve(root, "/IMG/Pictures/actor1.PNG"));
        assertEquals(new File(root, "audio/bgm/Theme 1.ogg"), AgvnHtmlFiles.resolve(root, "/audio/bgm/Theme%201.ogg"));
        assertNull(AgvnHtmlFiles.resolve(root, "/../secret.txt"));
        assertNull(AgvnHtmlFiles.resolve(root, "/%2e%2e/secret.txt"));
        assertNull(AgvnHtmlFiles.resolve(root, "/img/missing.png"));
        assertNull(AgvnHtmlFiles.resolve(root, "/img"));
    }

    @Test
    public void mimeTypes() {
        assertEquals("application/javascript", AgvnHtmlFiles.mimeType("rpg_core.js"));
        assertEquals("audio/ogg", AgvnHtmlFiles.mimeType("Battle1.OGG"));
        assertEquals("application/octet-stream", AgvnHtmlFiles.mimeType("Actor1.rpgmvp"));
        assertTrue(AgvnHtmlFiles.isText("text/html"));
        assertFalse(AgvnHtmlFiles.isText("image/png"));
    }
}
