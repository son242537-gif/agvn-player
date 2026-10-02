/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class AgvnFontFallbackTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** The block as the bundled Proton prefix has it, between two other keys. */
    private static final String USER_REG = "WINE REGISTRY Version 2\n\n#arch=win64\n\n"
            + "[Software\\\\Wine\\\\Fonts\\\\Replacements] 1746463196\n#time=1dbbddc558c2c16\n"
            + "\"Palatino Linotype\"=\"Times New Roman\"\n\"Segoe UI\"=\"Times New Roman\"\n\n"
            + "[Software\\\\Wine\\\\WineDbg] 1746463196\n\"ShowCrashDialog\"=dword:00000000\n";

    @Test
    public void escapesJapaneseNamesLikeWine() {
        assertEquals("\\xff2d\\xff33 \\x30b4\\x30b7\\x30c3\\x30af", AgvnFontFallback.escape("ＭＳ ゴシック"));
        assertEquals("a\\\"b\\\\c", AgvnFontFallback.escape("a\"b\\c"));
    }

    @Test
    public void addsTheMissingNamesInsideTheExistingKey() {
        String out = AgvnFontFallback.withReplacements(USER_REG);
        int key = out.indexOf(AgvnFontFallback.KEY), next = out.indexOf("[Software\\\\Wine\\\\WineDbg]");
        String block = out.substring(key, next);
        assertTrue(block.contains("\"Segoe UI\"=\"Times New Roman\"\n"));
        assertTrue(block.contains("\n\"MS Gothic\"=\"Source Han Sans CN\"\n"));
        assertTrue(block.contains("\n\"\\xff2d\\xff33 \\x30b4\\x30b7\\x30c3\\x30af\"=\"Source Han Sans CN\"\n"));
        assertTrue("a blank line still ends the key", block.endsWith("\"\n\n"));
        assertEquals(AgvnFontFallback.NAMES.length + 2, block.split("\n\"").length - 1);
        assertTrue(out.endsWith("\"ShowCrashDialog\"=dword:00000000\n"));
        assertNull("nothing left to add", AgvnFontFallback.withReplacements(out));
    }

    @Test
    public void keepsAChoiceAlreadyMadeAndAddsTheKeyWhenMissing() {
        String own = USER_REG.replace("\"Segoe UI\"", "\"ms gothic\"=\"Arial\"\n\"Segoe UI\"");
        String out = AgvnFontFallback.withReplacements(own);
        assertTrue(out.contains("\"ms gothic\"=\"Arial\""));
        assertEquals(-1, out.indexOf("\"MS Gothic\"="));

        String bare = "WINE REGISTRY Version 2\n\n[Software\\\\Wine] 1\n\"Version\"=\"win10\"";
        String added = AgvnFontFallback.withReplacements(bare);
        assertTrue(added.startsWith(bare + "\n\n" + AgvnFontFallback.KEY + " "));
        assertTrue(added.endsWith("\"游明朝\"=\"Source Han Sans CN\"\n".replace("游明朝", AgvnFontFallback.escape("游明朝"))));
    }

    @Test
    public void writesTheFileOnceAndKeepsOtherBytes() throws Exception {
        File reg = tmp.newFile("user.reg");
        byte[] original = (USER_REG + "\"Caf\u00e9\"=\"\\x00e9\"\n").getBytes(StandardCharsets.UTF_8);
        Files.write(reg.toPath(), original);
        AgvnFontFallback.apply(reg);
        byte[] first = Files.readAllBytes(reg.toPath());
        assertTrue(new String(first, StandardCharsets.UTF_8).contains("\"MS Mincho\"=\"Source Han Sans CN\""));
        assertTrue("the UTF-8 bytes of other lines are untouched", new String(first, StandardCharsets.UTF_8).contains("\"Caf\u00e9\""));
        AgvnFontFallback.apply(reg);
        assertArrayEquals(first, Files.readAllBytes(reg.toPath()));
    }
}
