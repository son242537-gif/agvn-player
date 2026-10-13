/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** NW.js page games for "Chạy nhẹ": Love My Back's folder, and the ones that need Node and stay on Windows. */
public class AgvnNwjsGameTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File file(File root, String rel, String content) throws Exception {
        File f = new File(root, rel);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    /** Love My Back (14/10/2026): NW.js beside game.exe, index.html + script.js + asset/. */
    private File loveMyBack(String packageJson) throws Exception {
        File dir = tmp.newFolder();
        file(dir, "game.exe", "MZ");
        file(dir, "nw.dll", "");
        file(dir, "index.html", "<html></html>");
        file(dir, "script.js", "");
        file(dir, "asset/a.png", "");
        file(dir, "package.json", packageJson);
        return dir;
    }

    @Test
    public void anNwjsPageGameStartsOnItsPage() throws Exception {
        File dir = loveMyBack("{\n  \"name\": \"game\",\n  \"main\": \"index.html\"\n}");
        assertEquals(GameExeResolver.Engine.UNKNOWN, GameExeResolver.detectEngine(dir));
        assertEquals(new File(dir, "index.html"), AgvnNwjsGame.index(dir));
        assertEquals(new File(dir, "index.html"), AgvnHtmlGame.indexFor(dir, GameExeResolver.Engine.UNKNOWN));
        assertNull("another engine keeps its own rules", AgvnHtmlGame.indexFor(dir, GameExeResolver.Engine.UNITY));
    }

    @Test
    public void aWindowInPackageJsonIsNwjsWithoutItsRuntime() throws Exception {
        File dir = tmp.newFolder(); // a phone copy: no nw.dll
        file(dir, "index.html", "");
        file(dir, "package.json", "﻿{\"main\":\"index.html\",\"window\":{\"width\":1280}}");
        assertEquals(new File(dir, "index.html"), AgvnNwjsGame.index(dir));
        File site = tmp.newFolder(); // a web page's package.json (npm): not a game
        file(site, "index.html", "");
        file(site, "package.json", "{\"main\":\"index.html\",\"scripts\":{}}");
        assertNull(AgvnNwjsGame.index(site));
    }

    @Test
    public void readsTheMainPageAsNwjsWrotesIt() {
        assertEquals("index.html", AgvnNwjsGame.page("index.html"));
        assertEquals("index.html", AgvnNwjsGame.page("app://./index.html"));
        assertEquals("game.htm", AgvnNwjsGame.page(" ./game.htm?lang=ja#top "));
        assertNull("a Node app", AgvnNwjsGame.page("main.js"));
        assertNull("a page in another folder: its root is not the game's", AgvnNwjsGame.page("www/index.html"));
        assertNull(AgvnNwjsGame.page(""));
        assertNull(AgvnNwjsGame.page(null));
        assertEquals("index.html", AgvnNwjsGame.main("{\"name\":\"x\", \"main\" : \"index.html\"}"));
    }

    @Test
    public void aPageNamedOtherwiseOnDiskIsFound() throws Exception {
        File dir = loveMyBack("{\"main\":\"index.html\"}");
        new File(dir, "index.html").delete();
        file(dir, "Index.html", "");
        assertEquals("Index.html", AgvnNwjsGame.index(dir).getName());
        new File(dir, "Index.html").delete();
        assertNull("no page: Windows", AgvnNwjsGame.index(dir));
    }

    @Test
    public void aGameThatNeedsNodeStaysOnWindows() throws Exception {
        assertNull(AgvnNwjsGame.index(loveMyBack("{\"main\":\"index.html\",\"node-main\":\"start.js\"}")));
        assertNull(AgvnNwjsGame.index(loveMyBack("{\"main\":\"index.html\",\"bg-script\":\"bg.js\"}")));
        File modules = loveMyBack("{\"main\":\"index.html\"}");
        file(modules, "node_modules/x/index.js", "");
        assertNull(AgvnNwjsGame.index(modules));
        File addon = loveMyBack("{\"main\":\"index.html\"}");
        file(addon, "greenworks-win64.node", "");
        assertNull(AgvnNwjsGame.index(addon));
        assertNull("no package.json", AgvnNwjsGame.index(tmp.newFolder()));
    }
}
