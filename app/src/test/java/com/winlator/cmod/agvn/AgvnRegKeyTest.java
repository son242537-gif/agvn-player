/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Map;

/** One key of a Wine registry file edited as text, the rest of the file as it was. */
public class AgvnRegKeyTest {
    private static final String REG = "WINE REGISTRY Version 2\n;; All keys relative to \\\\Machine\n\n"
            + "[Software\\\\Microsoft\\\\Windows NT\\\\CurrentVersion\\\\Fonts] 1753725033\n"
            + "#time=1dbffe81de649b0\n"
            + "\"Arial (TrueType)\"=\"arial.ttf\"\n"
            + "\"Tahoma (TrueType)\"=\"tahoma.ttf\"\n"
            + "\n[Software\\\\Microsoft\\\\Windows NT\\\\CurrentVersion\\\\FontSubstitutes] 1753725033\n"
            + "\"MS Shell Dlg\"=\"Tahoma\"\n"
            + "\"Count\"=dword:00000001\n";
    private static final String FONTS = "Software\\\\Microsoft\\\\Windows NT\\\\CurrentVersion\\\\Fonts";

    @Test
    public void readsStringsWithTheirEscapes() {
        assertArrayEquals(new String[]{"a\"b", "C:\\x"}, AgvnRegKey.parse("\"a\\\"b\"=\"C:\\\\x\""));
        assertArrayEquals(new String[]{"ＭＳ ゴシック", "Z:\\f"},
                AgvnRegKey.parse("\"\\xff2d\\xff33 \\x30b4\\x30b7\\x30c3\\x30af\"=\"Z:\\\\f\""));
        // Wine writes four digits when a hex digit follows, else as few as needed
        assertArrayEquals(new String[]{"é1", "é"}, AgvnRegKey.parse("\"\\x00e91\"=\"\\xe9\""));
        assertNull(AgvnRegKey.parse("\"Count\"=dword:00000001"));
        assertNull(AgvnRegKey.parse("#time=1dbffe81de649b0"));
        assertNull(AgvnRegKey.parse("\"cut"));
    }

    @Test
    public void changesOnlyItsOwnKey() {
        AgvnRegKey key = new AgvnRegKey(REG, FONTS);
        Map<String, String> values = key.strings();
        assertEquals("arial.ttf", values.get("Arial (TrueType)"));
        assertEquals(2, values.size());
        key.set("arial (truetype)", "arial.ttf");
        assertNull("the same value again is no change", key.text());
        key.set("TAHOMA (TrueType)", "C:\\windows\\Fonts\\agvn\\tahoma.ttc");
        key.set("Verdana (TrueType)", "verdana.ttf");
        key.remove("Arial (TrueType)");
        key.remove("Nothing (TrueType)");
        String out = key.text();
        assertEquals("WINE REGISTRY Version 2\n;; All keys relative to \\\\Machine\n\n"
                + "[Software\\\\Microsoft\\\\Windows NT\\\\CurrentVersion\\\\Fonts] 1753725033\n"
                + "#time=1dbffe81de649b0\n"
                + "\"Tahoma (TrueType)\"=\"C:\\\\windows\\\\Fonts\\\\agvn\\\\tahoma.ttc\"\n"
                + "\"Verdana (TrueType)\"=\"verdana.ttf\"\n"
                + "\n[Software\\\\Microsoft\\\\Windows NT\\\\CurrentVersion\\\\FontSubstitutes] 1753725033\n"
                + "\"MS Shell Dlg\"=\"Tahoma\"\n"
                + "\"Count\"=dword:00000001\n", out);
        AgvnRegKey last = new AgvnRegKey(REG, FONTS.replace("\\Fonts", "\\FontSubstitutes"));
        assertEquals("Tahoma", last.strings().get("MS Shell Dlg"));
        last.set("ＭＳ ゴシック", "Tahoma");
        assertTrue(last.text().endsWith("\"Count\"=dword:00000001\n\"\\xff2d\\xff33 \\x30b4\\x30b7\\x30c3\\x30af\""
                + "=\"Tahoma\"\n"));
    }

    @Test
    public void aMissingKeyIsAddedAtTheEnd() {
        AgvnRegKey key = new AgvnRegKey(REG, "Software\\\\Wine\\\\Fonts\\\\External Fonts");
        assertTrue(key.strings().isEmpty());
        key.remove("Tahoma (TrueType)");
        assertNull(key.text());
        key.set("A", "b");
        assertEquals(REG + "\n[Software\\\\Wine\\\\Fonts\\\\External Fonts] 1700000000\n\"A\"=\"b\"\n", key.text());
    }
}
