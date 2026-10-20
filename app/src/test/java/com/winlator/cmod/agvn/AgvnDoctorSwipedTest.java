/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

/** A game the player swiped AGVN away from is asked about only when Wine's kept lines name an error by themselves. */
public class AgvnDoctorSwipedTest {
    /** Support Pregnancy School (Unreal, Mali-G615, Proton 10), as its wine-cuoi.txt kept it on 09/10/2026. */
    private static final String BOX = "0240:trace:msgbox:MSGBOX_OnInit L\"Assertion failed!\\n\\nProgram: F:\\\\SUPPORT_PREGNANCY_SCHOOL_GAMEHUB\\\\SUPPORT_PREGNANCY_SCHOOL_GAMEHUB\\\\DecliningBirth\\\\Binaries\\\\Win64\\\\DecliningBirth-Win64-Shipping.exe\\nFile: dlls/winevulkan/loader_thunks.c\\nLine: 3520\\n\\nExpression: \\\"!status && \\\"vkCreateShaderModule\\\"\\\"\\n\\nPress OK to\"...";
    private static AgvnProblemCatalog catalog;

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @BeforeClass
    public static void load() throws IOException {
        try (Reader in = Files.newBufferedReader(new File("src/main/assets/" + AgvnProblemCatalog.ASSET).toPath(), StandardCharsets.UTF_8)) {
            catalog = AgvnProblemCatalog.parse(in);
        }
    }

    /** What AgvnDoctor.afterKill reads for a swiped-away Windows game: no Android end, no crash, Wine's kept lines. */
    private static AgvnEvidence swiped(String... wine) {
        AgvnEvidence ev = new AgvnEvidence();
        ev.engine = "UNREAL";
        ev.lines.addAll(Arrays.asList(wine));
        return ev;
    }

    @Test
    public void wineLinesKeptAtTheSwipeAreReadBack() throws IOException {
        File session = tmp.newFolder("20261009-120551");
        assertEquals(Collections.emptyList(), AgvnWineTail.saved(session));
        Files.write(new File(session, AgvnWineTail.FILE).toPath(), ("esync: up and running.\n" + BOX + "\n").getBytes(StandardCharsets.UTF_8));
        assertEquals(Arrays.asList("esync: up and running.", BOX), AgvnWineTail.saved(session));
        assertEquals(Collections.emptyList(), AgvnWineTail.saved(new File(session, "gone")));
    }

    @Test
    public void anErrorBoxLeftBeforeTheSwipeIsAskedAbout() {
        AgvnProblemCatalog.Finding f = catalog.find(swiped("_r_debug not found in ld.so", "esync: up and running.", BOX));
        assertEquals("vulkan-crash", f.id());
        assertTrue("a game on Proton 10 can go back to Proton 9", f.fixes().contains("wine-old"));
        assertTrue(f.fixes().indexOf("wine-old") < f.fixes().indexOf("wined3d"));
    }

    @Test
    public void aGameThatRanIsNotAskedAboutAfterTheSwipe() {
        AgvnEvidence ev = swiped("_r_debug not found in ld.so", "esync: up and running.", "[AGVN] ×18 dòng lặp lại");
        ev.changed = true; // its settings differ from the good ones, and it saves the most RAM: no failure line still
        ev.ramSaved = true;
        assertNull(catalog.find(ev));
    }
}
