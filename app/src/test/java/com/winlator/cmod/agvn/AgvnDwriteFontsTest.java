/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
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
import java.util.Map;

/** Report 12/10/2026: DirectWrite drew no text, as Proton's prefix lists fonts whose files it lacks. */
public class AgvnDwriteFontsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private static final String NT = "[Software\\\\Microsoft\\\\Windows NT\\\\CurrentVersion\\\\Fonts] 1753725033\n";
    private static final String SUBST =
            "\n[Software\\\\Microsoft\\\\Windows NT\\\\CurrentVersion\\\\FontSubstitutes] 1\n"
            + "\"MS Shell Dlg\"=\"Tahoma\"\n";
    private File imageFs, wine, fonts, wineFonts;

    private File put(File dir, String name, byte[] data) throws Exception {
        File f = new File(dir, name);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), data);
        return f;
    }

    private static String line(String name, String data) {
        return "\"" + name + "\"=\"" + data.replace("\\", "\\\\") + "\"\n";
    }

    /** A Proton 10 container as created: the prefix's Fonts key, the app's fonts, Wine's own and the image's. */
    private void proton10() throws Exception {
        imageFs = tmp.newFolder("imagefs");
        wine = new File(imageFs, "home/xuser-1/.wine");
        fonts = new File(wine, "drive_c/windows/Fonts");
        wineFonts = new File(tmp.getRoot(), "proton-10/share/wine/fonts");
        put(fonts, "arial.ttf", AgvnFontFileTest.font("Arial", "Book", 400));
        put(fonts, "msyh.ttc", "a collection".getBytes(StandardCharsets.UTF_8));
        put(fonts, "webdings.ttf", AgvnFontFileTest.font("Webdings", "Book", 400));
        put(fonts, "verdana.ttf", AgvnFontFileTest.font("Verdana", "Book", 400));
        put(fonts, "07yasashisa.ttf", AgvnFontFileTest.font("07やさしさゴシック", "Book", 400));
        put(fonts, "app932.fon", new byte[]{1});
        put(wineFonts, "tahoma.ttf", AgvnFontFileTest.font("Tahoma", "Book", 400));
        put(wineFonts, "tahomabd.ttf", AgvnFontFileTest.font("Tahoma", "Bold", 700));
        for (String f : new String[]{"marlett.ttf", "symbol.ttf", "wingding.ttf", "coure.fon", "sserife.fon"})
            put(wineFonts, f, f.getBytes(StandardCharsets.UTF_8));
        File ms = new File(imageFs, AgvnDwriteFonts.MS), dejavu = new File(imageFs, AgvnDwriteFonts.DEJAVU);
        for (String f : new String[]{"simsun.ttc", "malgun.ttf", "Nirmala.ttf"}) put(ms, f, new byte[]{2});
        put(dejavu, "DejaVuSans.ttf", AgvnFontFileTest.font("DejaVu Sans", "Book", 400));
        put(dejavu, "DejaVuSans-Bold.ttf", AgvnFontFileTest.font("DejaVu Sans", "Bold", 700));
        String[][] entries = {{"Arial (TrueType)", "arial.ttf"}, {"Courier", "coure.fon"},
                {"DejaVu Sans (TrueType)", "Z:\\usr\\share\\fonts\\truetype\\dejavu\\DejaVuSans.ttf"},
                {"Game (TrueType)", "D:\\Game\\game.ttf"}, {"Malgun Gothic (TrueType)", "malgun.ttf"},
                {"Marlett (TrueType)", "marlett.ttf"}, {"Microsoft Sans Serif (TrueType)", "micross.ttf"},
                {"Microsoft YaHei (TrueType)", "msyh.ttf"}, {"MS Gothic (TrueType)", "msgothic.ttc"},
                {"MS PGothic (TrueType)", "msgothic.ttc"}, {"MS Serif", "serife.fon"},
                {"Nirmala UI (TrueType)", "nirmala.ttf"},
                {"Noto Sans CJK JP (TrueType)", "Z:\\system\\fonts\\NotoSansCJK-Regular.ttc"},
                {"SimSun (TrueType)", "simsun.ttc"}, {"Tahoma (TrueType)", "tahoma.ttf"},
                {"Tahoma Bold (TrueType)", "tahomabd.ttf"}, {"Webdings (TrueType)", "webdings.ttf"},
                {"Wingdings (TrueType)", "wingdings.ttf"}};
        StringBuilder reg = new StringBuilder("WINE REGISTRY Version 2\n\n#arch=win64\n\n" + NT + "#time=1\n");
        for (String[] e : entries) reg.append(line(e[0], e[1]));
        put(wine, "system.reg", (reg + SUBST).getBytes(StandardCharsets.ISO_8859_1));
        put(wine, "user.reg", ("WINE REGISTRY Version 2\n\n[Software\\\\Wine\\\\Fonts\\\\External Fonts] 1\n#time=1\n"
                + line("Marlett (TrueType)", "Z:\\data\\data\\com.termux\\files\\fonts\\marlett.ttf")
                + line("Noto Sans CJK JP (TrueType)", "Z:\\system\\fonts\\NotoSansCJK-Regular.ttc")
                + line("Tahoma (TrueType)", "Z:\\data\\data\\com.termux\\files\\fonts\\tahoma.ttf")
                + "\n[Software\\\\Wine\\\\Fonts\\\\Replacements] 1\n" + line("Segoe UI", "Times New Roman"))
                .getBytes(StandardCharsets.ISO_8859_1));
    }

    private Map<String, String> fontsKey() throws Exception {
        return new AgvnRegKey(AgvnFontFiles.read(new File(wine, "system.reg")), AgvnDwriteFonts.KEY).strings();
    }

    @Test
    public void everyListedFontHasItsFileAndDirectWriteHasVietnameseTahoma() throws Exception {
        proton10();
        AgvnDwriteFonts.apply(wine, wineFonts, imageFs);
        // Wine's own fonts, under the names the prefix lists; GDI keeps Wine's Tahoma
        for (String f : new String[]{"tahoma.ttf", "tahomabd.ttf", "marlett.ttf", "coure.fon"})
            assertArrayEquals(f, Files.readAllBytes(new File(wineFonts, f).toPath()),
                    Files.readAllBytes(new File(fonts, f).toPath()));
        assertArrayEquals(Files.readAllBytes(new File(wineFonts, "wingding.ttf").toPath()),
                Files.readAllBytes(new File(fonts, "wingdings.ttf").toPath()));
        Map<String, String> key = fontsKey();
        assertEquals(AgvnDwriteFonts.OURS + "tahoma.ttc", key.get("Tahoma (TrueType)"));
        assertEquals(AgvnDwriteFonts.OURS + "tahomabd.ttc", key.get("Tahoma Bold (TrueType)"));
        File ours = new File(fonts, "agvn/tahoma.ttc"), bold = new File(fonts, "agvn/tahomabd.ttc");
        assertEquals("Tahoma", AgvnFontFile.name(ours, 0, 1));
        assertEquals("Segoe UI", AgvnFontFile.name(ours, 1, 1));
        assertEquals("Tahoma Bold", AgvnFontFile.name(bold, 0, 4));
        assertEquals("Segoe UI Bold", AgvnFontFile.name(bold, 1, 4));
        assertEquals("msyh.ttc", key.get("Microsoft YaHei (TrueType)"));
        assertEquals("Z:\\usr\\share\\fonts\\truetype\\ms\\simsun.ttc", key.get("SimSun (TrueType)"));
        assertEquals("Z:\\usr\\share\\fonts\\truetype\\ms\\Nirmala.ttf", key.get("Nirmala UI (TrueType)"));
        assertEquals("wingdings.ttf", key.get("Wingdings (TrueType)"));
        for (String gone : new String[]{"MS Gothic (TrueType)", "MS PGothic (TrueType)", "MS Serif",
                "Microsoft Sans Serif (TrueType)", "Noto Sans CJK JP (TrueType)"})
            assertFalse(gone, key.containsKey(gone));
        assertTrue("a font that is there stays", key.containsKey("DejaVu Sans (TrueType)"));
        assertEquals("another drive is not checked", "D:\\Game\\game.ttf", key.get("Game (TrueType)"));
        // the unlisted fonts are listed, but not Wine's Tahoma beside the app's
        assertEquals("verdana.ttf", key.get("Verdana (TrueType)"));
        assertEquals("07yasashisa.ttf", key.get("07やさしさゴシック (TrueType)"));
        assertFalse(key.containsValue("tahoma.ttf"));
        assertFalse(key.containsValue("app932.fon"));
        String system = AgvnFontFiles.read(new File(wine, "system.reg"));
        assertTrue("other keys stay", system.endsWith(SUBST));
        assertTrue(system.contains("\"07\\x3084\\x3055\\x3057\\x3055\\x30b4\\x30b7\\x30c3\\x30af (TrueType)\""));
        String user = AgvnFontFiles.read(new File(wine, "user.reg"));
        assertFalse(user.contains("\"Tahoma (TrueType)\""));
        assertFalse(user.contains("\"Marlett (TrueType)\""));
        assertTrue(user.contains("\"Noto Sans CJK JP (TrueType)\""));
        assertTrue(user.contains("\"Segoe UI\"=\"Times New Roman\""));

        String note = AgvnDwriteFonts.takeNote();
        assertEquals("Phông DirectWrite: chép tahoma.ttf, tahomabd.ttf, coure.fon, marlett.ttf, wingdings.ttf"
                + " của Wine vào C:\\windows\\Fonts; ghi msyh.ttf → msyh.ttc; lấy malgun.ttf, nirmala.ttf,"
                + " simsun.ttc trong app; bỏ mục không có file: micross.ttf, msgothic.ttc, serife.fon,"
                + " NotoSansCJK-Regular.ttc; Tahoma, Segoe UI đủ chữ Việt cho DirectWrite (từ DejaVu Sans);"
                + " thêm 2 phông có sẵn vào danh sách", note);
        assertNull("said once", AgvnDwriteFonts.takeNote());

        byte[] before = Files.readAllBytes(new File(wine, "system.reg").toPath());
        AgvnDwriteFonts.apply(wine, wineFonts, imageFs);
        assertArrayEquals("the next launch finds everything in place", before,
                Files.readAllBytes(new File(wine, "system.reg").toPath()));
        assertNull(AgvnDwriteFonts.takeNote());
    }

    @Test
    public void aPlayersOwnTahomaStaysDirectWrites() throws Exception {
        proton10();
        AgvnDwriteFonts.apply(wine, wineFonts, imageFs);
        put(fonts, "tahoma.ttf", AgvnFontFileTest.font("Tahoma", "Regular", 400 + 1)); // Windows' own, say
        AgvnDwriteFonts.apply(wine, wineFonts, imageFs);
        Map<String, String> key = fontsKey();
        assertEquals("tahoma.ttf", key.get("Tahoma (TrueType)"));
        assertEquals(AgvnDwriteFonts.OURS + "tahomabd.ttc", key.get("Tahoma Bold (TrueType)"));
    }

    @Test
    public void withoutWinesFontsNothingBreaks() throws Exception {
        proton10();
        AgvnDwriteFonts.apply(wine, null, imageFs);
        Map<String, String> key = fontsKey();
        assertFalse("no file anywhere", key.containsKey("Tahoma (TrueType)"));
        assertEquals("arial.ttf", key.get("Arial (TrueType)"));
        assertFalse(new File(fonts, "agvn").exists());
        AgvnDwriteFonts.takeNote();
        AgvnDwriteFonts.apply(new File(tmp.getRoot(), "no-wine"), null, imageFs);
        assertNull(AgvnDwriteFonts.takeNote());
    }
}
