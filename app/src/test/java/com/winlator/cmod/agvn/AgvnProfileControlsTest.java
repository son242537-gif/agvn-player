/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** A game's own .icp named in agvn-profile.json "controls" (Kim Dung Quần Hiệp Truyện 5, 25/11/2026). */
public class AgvnProfileControlsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final String ICP = "{\"id\":7,\"name\":\"Phím Kim Dung\",\"cursorSpeed\":1,\"agvnLayoutVersion\":3,"
            + "\"template\":true,\"elements\":[{\"type\":\"BUTTON\",\"shape\":\"CIRCLE\",\"bindings\":[\"KEY_A\",\"NONE\","
            + "\"NONE\",\"NONE\"],\"scale\":1,\"x\":0.9,\"y\":0.8,\"toggleSwitch\":false,\"text\":\"A\",\"iconId\":0}]}";

    private File game() throws Exception {
        File dir = tmp.newFolder("Kim Dung 5");
        Files.write(new File(dir, "Game.exe").toPath(), new byte[] {'M', 'Z'});
        Files.write(new File(dir, "phim-ao.icp").toPath(), ICP.getBytes(StandardCharsets.UTF_8));
        new File(dir, "keys").mkdirs();
        Files.write(new File(dir, "keys/hanh-dong.icp").toPath(), ICP.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "notes.icp").toPath(), "{\"name\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "broken.icp").toPath(), "{elements".getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    @Test
    public void aFileIsToldApartFromALayoutName() {
        assertTrue(AgvnProfileControls.isFile("phim-ao.icp"));
        assertTrue(AgvnProfileControls.isFile(" keys/Phim.ICP "));
        assertFalse(AgvnProfileControls.isFile("action"));
        assertFalse(AgvnProfileControls.isFile(null));
    }

    @Test
    public void onlyAControlsProfileInsideTheGameFolder() throws Exception {
        File dir = game();
        assertNull(AgvnProfileControls.problem(dir, "phim-ao.icp"));
        assertNull(AgvnProfileControls.problem(dir, "keys\\hanh-dong.icp"));
        assertNotNull("not there", AgvnProfileControls.problem(dir, "khac.icp"));
        assertNotNull("outside the folder", AgvnProfileControls.problem(dir, "../phim-ao.icp"));
        assertNotNull(AgvnProfileControls.problem(dir, "/sdcard/phim-ao.icp"));
        assertNotNull(AgvnProfileControls.problem(dir, "C:/phim-ao.icp"));
        assertNotNull("no elements", AgvnProfileControls.problem(dir, "notes.icp"));
        assertNotNull("not JSON", AgvnProfileControls.problem(dir, "broken.icp"));
    }

    @Test
    public void theValidatorTakesTheGamesFileAndRefusesAnyOther() throws Exception {
        File dir = game();
        AgvnProfile p = AgvnProfile.defaultFor("Kim Dung 5");
        p.exe = "Game.exe";
        p.controls = "phim-ao.icp";
        assertEquals("Game.exe", AgvnProfileValidator.validate(p, dir));
        p.controls = "action";
        AgvnProfileValidator.validate(p, dir);
        p.controls = "../phim-ao.icp";
        try {
            AgvnProfileValidator.validate(p, dir);
            fail("a file outside the game folder");
        } catch (AgvnProfileException expected) {}
        p.controls = "letters";
        try {
            AgvnProfileValidator.validate(p, dir);
            fail("neither a layout nor a file");
        } catch (AgvnProfileException expected) {
            assertTrue(expected.getMessage().contains(".icp"));
        }
    }

    @Test
    public void theProfileTheAppKeepsIsTheGamesWithItsOwnIdNameAndSource() {
        String kept = AgvnProfileControls.prepare(ICP, 12, "Kim Dung 5", "/sdcard/Kim Dung 5/phim-ao.icp");
        JsonObject o = JsonParser.parseString(kept).getAsJsonObject();
        assertEquals(12, o.get("id").getAsInt());
        assertEquals("Kim Dung 5", o.get("name").getAsString());
        assertFalse("not one of AGVN's layouts: never written over", o.has(AgvnControls.VERSION_KEY));
        assertFalse("not a template pickers hide", o.has("template"));
        assertEquals(1, o.getAsJsonArray("elements").size());
        assertEquals("/sdcard/Kim Dung 5/phim-ao.icp", AgvnProfileControls.sourceOf(kept));
        assertNull(AgvnProfileControls.sourceOf(ICP));
        assertTrue(AgvnProfileControls.elementsOf(kept));
    }
}
