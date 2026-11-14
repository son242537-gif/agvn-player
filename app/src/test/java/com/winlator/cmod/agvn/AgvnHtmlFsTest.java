/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** fs.readdirSync and fs.statSync of "Chạy nhẹ" (an RPG Maker MV game's mod loader, 0.1.77, 06/11/2026). */
public class AgvnHtmlFsTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void foldersAndFilesOfAGameFolder() throws Exception {
        File game = tmp.newFolder("game");
        File img = new File(game, "Mods/ModA/img");
        assertTrue(img.mkdirs());
        assertTrue(new File(img, "a.png").createNewFile());
        assertEquals("list", AgvnHtmlFs.op("/__agvn/fs/list/mods/moda"));
        assertEquals("stat", AgvnHtmlFs.op("/__agvn/fs/stat"));
        assertEquals("", AgvnHtmlFs.op("/__agvn/fs/delete/x"));
        assertEquals("/mods/moda", AgvnHtmlFs.path("/__agvn/fs/list/mods/moda"));
        assertEquals("/", AgvnHtmlFs.path("/__agvn/fs/list"));
        assertEquals("ignoring case, as on Windows", new File(game, "Mods/ModA"), AgvnHtmlFs.find(game, "/mods/moda"));
        assertEquals(game, AgvnHtmlFs.find(game, "/"));
        assertEquals(new File(img, "a.png"), AgvnHtmlFs.find(game, "/mods/ModA/img/a%2Epng"));
        assertNull(AgvnHtmlFs.find(game, "/mods/nope"));
        assertNull("never above the game", AgvnHtmlFs.find(img, "/../../.."));
        assertEquals("[{\"name\":\"a.png\",\"dir\":false},{\"name\":\"sub\",\"dir\":true}]",
                AgvnHtmlFs.list(Arrays.asList("a.png", "sub"), new HashSet<>(Arrays.asList("sub"))));
        assertEquals("{\"dir\":true,\"size\":0,\"mtime\":5}", AgvnHtmlFs.stat(true, 0, 5));
    }

    @Test
    public void foldersOfAnAppAsar() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("index.html", "<html>".getBytes(StandardCharsets.UTF_8));
        files.put("Mods/ModA/img/a.png", new byte[]{1});
        files.put("Mods/ModB/info.json", new byte[]{2});
        File archive = new File(tmp.getRoot(), "app.asar");
        AgvnElectronGameTest.asar(archive, files);
        AgvnAsar asar = AgvnAsar.of(archive);
        Set<String> dirs = new HashSet<>();
        List<String> top = asar.children("", dirs);
        assertEquals(Arrays.asList("index.html", "Mods"), top);
        assertEquals(new HashSet<>(Arrays.asList("Mods")), dirs);
        assertEquals(Arrays.asList("ModA", "ModB"), asar.children("/mods/", new HashSet<>()));
        assertEquals(Arrays.asList("a.png"), asar.children("mods/moda/img", new HashSet<>()));
        assertNull(asar.children("mods/nope", new HashSet<>()));
    }

    @Test
    public void aWwwPathInAGameWhoseRootIsItsWwwFolder() throws Exception {
        // SRD_PreloaderCore (0.1.93, 14/11/2026): the plugin found the game folder as "/www/" from the page's address
        File www = tmp.newFolder("Game", "www");
        File pictures = new File(www, "img/pictures");
        assertTrue(pictures.mkdirs());
        assertTrue(new File(pictures, "a.png").createNewFile());
        assertEquals(pictures, AgvnHtmlFs.find(www, "/www/img/pictures"));
        assertEquals(www, AgvnHtmlFs.find(www, "/www"));
        assertEquals(new File(pictures, "a.png"), AgvnHtmlFiles.fileFor(www, "/www/img/pictures/a.png"));
        assertEquals("/img/pictures", AgvnHtmlFiles.withoutRootName(www, "/WWW/img/pictures"));
        assertNull("another game's root", AgvnHtmlFiles.withoutRootName(tmp.newFolder("other"), "/www/img"));
        File inner = new File(www, "www");
        assertTrue(inner.mkdirs());
        assertNull("a real www folder inside wins", AgvnHtmlFiles.withoutRootName(www, "/www/img"));
    }
}
