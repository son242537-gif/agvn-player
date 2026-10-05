/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** A "Chạy nhẹ" game's own key set: keys changed, added and deleted, kept and read back, and what each game gets. */
public class AgvnLightKeySetTest {
    private static String icp(String kind) throws Exception {
        File f = new File("src/main/assets/" + AgvnLayouts.assetFor(kind));
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static AgvnLightLayout layout(String kind) throws Exception {
        return AgvnLightLayout.parse(kind, icp(kind));
    }

    @Test
    public void aGamesOwnKeysRoundTripAndResetToTheLayout() throws Exception {
        AgvnLightLayout rpg = layout(AgvnLayouts.RPG);
        rpg.elements.get(1).x = 0.5f; // OK moved and enlarged
        rpg.elements.get(1).scale = 1.4f;
        rpg.elements.get(3).bindings = new String[]{"KEY_A"}; // "Chạy" (Shift) now sends A
        rpg.elements.get(3).text = "A";
        rpg.elements.remove(6); // Space deleted
        rpg.elements.add(AgvnLightLayout.Element.button("F5", "KEY_F5"));
        rpg.elements.add(AgvnLightLayout.Element.pad(AgvnLightKeyPicker.WASD));
        AgvnLightLayout again = layout(AgvnLayouts.RPG);
        assertTrue(again.load(rpg.toJson()));
        assertEquals(8, again.elements.size());
        assertEquals(0.5f, again.elements.get(1).x, 1e-4);
        assertEquals(1.4f, again.elements.get(1).scale, 1e-3);
        assertTrue(again.elements.get(1).round);
        assertArrayEquals(new String[]{"KEY_A"}, again.elements.get(3).bindings);
        assertEquals("A", again.elements.get(3).text);
        AgvnLightLayout.Element f5 = again.elements.get(6), wasd = again.elements.get(7);
        assertEquals("F5", f5.text);
        assertFalse(f5.pad || f5.round); // a pill, as the Windows editor's new button
        assertEquals(0.5f, f5.x, 1e-6);
        assertEquals(AgvnLightLayout.NEW_BUTTON_SCALE, f5.scale, 1e-6);
        assertTrue(wasd.pad);
        assertArrayEquals(new String[]{"KEY_W", "KEY_D", "KEY_S", "KEY_A"}, wasd.bindings);
        assertTrue(again.load("{\"elements\":[]}")); // every key deleted: kept so
        assertTrue(again.elements.isEmpty());
        assertFalse(again.load("not json"));
        assertFalse(again.load(null));
        again.reset();
        assertEquals(7, again.elements.size());
        assertEquals(0.865f, again.elements.get(1).x, 1e-6);
        assertEquals(0.9f, again.elements.get(1).scale, 1e-6);
        assertArrayEquals(new String[]{"KEY_SPACE", "NONE", "NONE", "NONE"}, again.elements.get(6).bindings);
    }

    @Test
    public void placesOfEarlierVersionsStillApply() throws Exception {
        AgvnLightLayout vn = layout(AgvnLayouts.VN); // 0.1.6-0.1.8 kept places per game type, "x,y,scale;…"
        String places = "0.1,0.2,1.5;0.9,0.3,0.9;0.9,0.4,0.7;0.9,0.5,0.7;0.9,0.6,0.7;0.9,0.8,0.9";
        assertTrue(vn.apply(places));
        assertEquals(0.1f, vn.elements.get(0).x, 1e-6);
        assertEquals(1.5f, vn.elements.get(0).scale, 1e-6);
        assertFalse(vn.apply("0.1,0.1,1")); // places of another layout (other key count)
        assertFalse(vn.apply("a,b,c;" + places.substring(places.indexOf(';') + 1)));
    }

    @Test
    public void thePickerOffersWhatEachGameGets() throws Exception {
        AgvnLightLayout rgss = AgvnLightLayout.parse(AgvnLayouts.RPG, icp(AgvnLayouts.RPG), AgvnHtmlGame.RUNNER_RGSS);
        assertArrayEquals(new String[]{"KEY_ENTER", "NONE", "NONE", "NONE"}, rgss.elements.get(1).bindings); // OK
        rgss.elements.add(AgvnLightLayout.Element.button("Z", "KEY_Z")); // a Z the player picks stays Z
        AgvnLightLayout again = AgvnLightLayout.parse(AgvnLayouts.RPG, icp(AgvnLayouts.RPG), AgvnHtmlGame.RUNNER_RGSS);
        assertTrue(again.load(rgss.toJson()));
        assertArrayEquals(new String[]{"KEY_Z"}, again.elements.get(7).bindings);
        for (String[] row : AgvnKeyboardRows.ROWS) {
            for (String cell : row) {
                String name = cell.split("\\|", -1)[0];
                if (name.isEmpty()) continue;
                assertNotNull(name, AgvnKeyboardRows.binding(name));
                assertTrue(name, AgvnLightActions.sendable(AgvnHtmlGame.RUNNER_RENPY, name)); // SDL gets every drawn key
                assertTrue(name, AgvnLightActions.sendable(AgvnHtmlGame.RUNNER_RGSS, name));
            }
        }
        for (String cell : AgvnKeyboardRows.MOUSE) {
            String name = cell.split("\\|", -1)[0];
            assertTrue(name, AgvnLightActions.sendable(AgvnHtmlGame.RUNNER_RENPY, name));
            assertTrue(name, AgvnLightActions.sendable(AgvnHtmlGame.RUNNER_HTML, name));
            assertFalse(name, AgvnLightActions.sendable(AgvnHtmlGame.RUNNER_RGSS, name)); // mkxp-z takes no mouse
        }
        assertTrue(AgvnLightActions.sendable(AgvnHtmlGame.RUNNER_HTML, "KEY_F5"));
        assertTrue(AgvnLightActions.sendable(AgvnHtmlGame.RUNNER_HTML, "KEY_SHIFT_L"));
        assertFalse(AgvnLightActions.sendable(AgvnHtmlGame.RUNNER_HTML, "KEY_COMMA")); // no DOM key for it yet
        assertFalse(AgvnLightActions.sendable(AgvnHtmlGame.RUNNER_RENPY, "GAMEPAD_BUTTON_A"));
    }
}
