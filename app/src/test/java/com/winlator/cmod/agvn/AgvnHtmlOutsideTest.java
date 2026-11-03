/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * An HTML game read its pictures from its maker's server once served from https:// (03/11/2026), and a page asking
 * for initial-scale=0.8 ran off the right edge of the screen.
 */
public class AgvnHtmlOutsideTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File write(String rel, String text) throws Exception {
        File f = new File(tmp.getRoot(), rel);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    @Test
    public void aRequestToAnotherHostGetsTheGamesOwnFileItNames() throws Exception {
        write("game/index.html", "");
        write("game/images/cg/A 01.png", "");
        write("game/logo.png", "");
        File root = new File(tmp.getRoot(), "game");
        assertEquals("/images/cg/A%2001.png", AgvnHtmlOutside.localPath(root, "/images/cg/A%2001.png"));
        assertEquals("its maker's folders go", "/images/cg/A%2001.png",
                AgvnHtmlOutside.localPath(root, "/cdn/v2/images/cg/A%2001.png"));
        assertEquals("/logo.png", AgvnHtmlOutside.localPath(root, "/logo.png"));
        assertNull("never down to a bare file name", AgvnHtmlOutside.localPath(root, "/cdn/logo.png"));
        assertNull("its server's API", AgvnHtmlOutside.localPath(root, "/api/save"));
        assertNull(AgvnHtmlOutside.localPath(root, "/"));
        assertNull("a folder is no file", AgvnHtmlOutside.localPath(root, "/images/cg"));
        assertNull(AgvnHtmlOutside.localPath(root, "/../game/logo.png"));
    }

    @Test
    public void aPageAskingToBeShownSmallerGetsTheWideViewport() throws Exception {
        assertEquals(Float.valueOf(0.8f), AgvnHtmlViewport.initialScale("<head><meta name=\"viewport\" content=\"width="
                + "device-width, initial-scale=0.8, maximum-scale=5.0, viewport-fit=cover\"></head>"));
        assertEquals(Float.valueOf(0.5f), AgvnHtmlViewport.initialScale(
                "<META content='initial-scale=.5' NAME='viewport'>"));
        assertNull("RPG Maker MV's", AgvnHtmlViewport.initialScale("<meta name=\"viewport\" content=\"user-scalable=no\">"));
        assertNull(AgvnHtmlViewport.initialScale("<meta charset=\"utf-8\"><meta name=\"description\" content=\"initial-scale=0.5\">"));
        assertNull(AgvnHtmlViewport.initialScale(null));
        assertTrue(AgvnHtmlViewport.zoomsOut(write("react/index.html",
                "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=0.8\"></head></html>")));
        assertFalse("Tyrano's: scale 1", AgvnHtmlViewport.zoomsOut(write("tyrano/index.html",
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, user-scalable=no\">")));
        assertFalse(AgvnHtmlViewport.zoomsOut(new File(tmp.getRoot(), "none.html")));
    }
}
