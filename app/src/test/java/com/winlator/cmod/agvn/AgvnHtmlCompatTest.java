package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class AgvnHtmlCompatTest {
    private static final String TAG = "<script src=\"" + AgvnHtmlFiles.COMPAT_PATH + "\"></script>";

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
    public void bundledCompatScriptKeepsProcessUndefined() throws Exception {
        String js = new String(Files.readAllBytes(new File("src/main/assets/agvn/html-compat.js").toPath()), StandardCharsets.UTF_8);
        // RPG Maker treats require + process as NW.js and would then save to real files, which the phone cannot do
        assertTrue(js.contains("window.require = function"));
        assertTrue(!js.contains("window.process ="));
        assertTrue(js.contains("SM.catchException = function"));
    }
}
