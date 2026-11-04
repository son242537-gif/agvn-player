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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Where a Unity game keeps Player.log and its saves, as Windows names the folders. */
public class AgvnUnityFolderTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void aNameEndingInADotHasItsFolderWithoutIt() {
        // Body Pay OMG (05/11/2026): company "CheeseBugerSt.", and no Player.log in "Gửi nhật ký"
        assertEquals("AppData/LocalLow/CheeseBugerSt/Body Pay OMG",
                AgvnSaveLocations.unityFolder(new String[]{"CheeseBugerSt.", "Body Pay OMG"}));
        assertEquals("AppData/LocalLow/Studio/Game",
                AgvnSaveLocations.unityFolder(new String[]{"Studio ..", "Game. "}));
        assertEquals("a name of dots only is kept", "...", AgvnSaveLocations.folderName("..."));
        assertEquals("Co. Ltd", AgvnSaveLocations.folderName("Co. Ltd"));
        assertNull(AgvnSaveLocations.unityFolder(null));
    }

    @Test
    public void playerLogAndSavesAreLookedForThere() throws Exception {
        File game = tmp.newFolder("BODY-PAY-OMG");
        File data = new File(game, "BodyPayOMG_Data");
        assertTrue(data.mkdirs());
        byte[] appInfo = "CheeseBugerSt.\nBody Pay OMG\n".getBytes(StandardCharsets.UTF_8);
        Files.write(new File(data, "app.info").toPath(), appInfo);
        File exe = new File(game, "BodyPayOMG.exe");
        Files.write(exe.toPath(), new byte[0]);
        File profile = tmp.newFolder("xuser");

        File dir = new File(profile, "AppData/LocalLow/CheeseBugerSt/Body Pay OMG");
        assertTrue(AgvnEngineLogs.candidates(exe, game, "UNITY", profile).contains(new File(dir, "Player.log")));
        List<String> saves = AgvnSaveLocations.inProfile(exe, game, "UNITY", new ArrayList<>());
        assertEquals("AppData/LocalLow/CheeseBugerSt/Body Pay OMG", saves.get(0));
    }
}
