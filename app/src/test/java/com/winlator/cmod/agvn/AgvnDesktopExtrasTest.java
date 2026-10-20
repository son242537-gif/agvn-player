/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/** A "Chạy nhẹ" game, in a process of its own, keeps the key set loaded in it in its .desktop file. */
public class AgvnDesktopExtrasTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String DESKTOP = "[Desktop Entry]\r\nName=Song Tu\r\nExec=wine \"C:\\\\Game\\\\Game.exe\"\r\n\r\n"
            + "[Extra Data]\r\nagvnEngine=rgss\r\ncontrolsProfile=9002\r\nagvnControlsAuto=1\r\nagvnControlsHidden=1\r\n";

    @Test
    public void theLoadedSetReplacesAgvnsPickAndShows() throws Exception {
        File desktop = tmp.newFile("Song Tu.desktop");
        Files.write(desktop.toPath(), DESKTOP.getBytes(StandardCharsets.UTF_8));
        assertTrue(AgvnDesktopExtras.put(desktop, AgvnKeySets.pickExtras(7)));
        String text = new String(Files.readAllBytes(desktop.toPath()), StandardCharsets.UTF_8);
        assertEquals("[Desktop Entry]\nName=Song Tu\nExec=wine \"C:\\\\Game\\\\Game.exe\"\n\n[Extra Data]\n"
                + "agvnEngine=rgss\ncontrolsProfile=7\n", text); // as Shortcut.saveData writes it
        Map<String, String> extras = AgvnHtmlGame.readExtras(desktop);
        assertEquals("7", extras.get(AgvnLayouts.EXTRA_PROFILE));
        assertEquals(7, AgvnLightPick.picked(extras.get(AgvnLayouts.EXTRA_PROFILE), extras.get(AgvnLayouts.EXTRA_AUTO)));
        assertFalse(extras.containsKey(AgvnControlsFork.EXTRA_HIDDEN));
        assertFalse(new File(desktop.getPath() + ".tmp").exists());
        assertFalse(AgvnDesktopExtras.put(new File(tmp.getRoot(), "gone.desktop"), AgvnKeySets.pickExtras(7)));
    }

    @Test
    public void extrasAreAddedAndASectionAfterThemIsDroppedAsSaveDataDoes() {
        assertEquals("[Desktop Entry]\nName=A\n\n[Extra Data]\ncontrolsProfile=3\n",
                AgvnDesktopExtras.apply("[Desktop Entry]\nName=A\n", AgvnKeySets.pickExtras(3)));
        assertEquals("[Desktop Entry]\nName=A\n\n[Extra Data]\nuuid=x\ncontrolsProfile=3\n",
                AgvnDesktopExtras.apply("[Desktop Entry]\nName=A\n\n[Extra Data]\nuuid=x\n[Other]\nk=v\n", AgvnKeySets.pickExtras(3)));
        Map<String, String> none = new java.util.HashMap<>();
        none.put("uuid", null);
        assertEquals("[Desktop Entry]\nName=A\n", AgvnDesktopExtras.apply("[Desktop Entry]\nName=A\n\n[Extra Data]\nuuid=x\n", none));
    }
}
