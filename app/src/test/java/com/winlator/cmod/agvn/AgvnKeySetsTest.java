/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** "Bộ phím ảo": key sets saved from a game, listed and loaded in every game, Windows or "Chạy nhẹ". */
public class AgvnKeySetsTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static String asset(String kind) throws Exception {
        File f = new File("src/main/assets/" + AgvnLayouts.assetFor(kind));
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static void write(File dir, String name, String text) throws Exception {
        Files.write(new File(dir, name).toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject read(File dir, int id) throws Exception {
        byte[] bytes = Files.readAllBytes(new File(dir, "controls-" + id + ".icp").toPath());
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private File profiles() throws Exception {
        File dir = new File(tmp.newFolder("files"), "profiles");
        assertTrue(dir.mkdir());
        write(dir, "controls-1.icp", "{\"id\":1,\"name\":\"Virtual Gamepad\",\"cursorSpeed\":1,\"elements\":[]}");
        write(dir, "controls-2.icp", "{\"id\":2,\"name\":\"Template: Gamepad\",\"elements\":[]}");
        write(dir, "controls-9001.icp", asset(AgvnLayouts.VN));
        write(dir, "controls-5.icp", "not json");
        write(dir, "notes.txt", "{}");
        return dir;
    }

    private static List<Integer> ids(List<AgvnKeySets.Entry> sets) {
        List<Integer> ids = new ArrayList<>();
        for (AgvnKeySets.Entry set : sets) ids.add(set.id);
        return ids;
    }

    @Test
    public void listsEverySetButTemplatesThePlayersFirst() throws Exception {
        File dir = profiles();
        List<AgvnKeySets.Entry> sets = AgvnKeySets.list(dir);
        assertEquals(java.util.Arrays.asList(1, 9001), ids(sets));
        assertEquals("AGVN · Visual novel", sets.get(1).name);
        assertEquals(1, AgvnKeySets.named(dir, "Virtual Gamepad"));
        assertEquals("AGVN's layouts are never written over", -1, AgvnKeySets.named(dir, "AGVN · Visual novel"));
        assertEquals(-1, AgvnKeySets.named(dir, "Song Tu"));
    }

    /** A "Chạy nhẹ" game's keys become a profile the Windows controls load (toggleSwitch, iconId) and read back. */
    @Test
    public void keysOfAChayNheGameSaveAsASetEveryGameLoads() throws Exception {
        File dir = profiles();
        AgvnLightLayout rpg = AgvnLightLayout.parse(AgvnLayouts.RPG, asset(AgvnLayouts.RPG), AgvnHtmlGame.RUNNER_RGSS);
        rpg.elements.add(AgvnLightLayout.Element.button("F5", "KEY_F5"));
        String own = rpg.toJson();
        assertEquals(-1, AgvnKeySets.same(dir, own));
        int id = AgvnKeySets.save(dir, own, "Song Tu", -1);
        assertEquals(6, id); // above the player's ids, the broken file's included, outside 9000-9099
        JsonObject saved = read(dir, id);
        List<String> order = new ArrayList<>(saved.keySet());
        assertEquals(java.util.Arrays.asList("id", "name", "cursorSpeed", "elements"), order); // as the profile list reads it
        assertEquals("Song Tu", saved.get("name").getAsString());
        JsonArray keys = saved.getAsJsonArray("elements");
        assertEquals(8, keys.size());
        for (JsonElement key : keys) {
            JsonObject k = key.getAsJsonObject();
            for (String field : new String[]{"type", "shape", "bindings", "x", "y", "scale", "text"}) assertTrue(field, k.has(field));
            assertFalse(k.get("toggleSwitch").getAsBoolean());
            assertEquals(0, k.get("iconId").getAsInt());
        }
        assertEquals("KEY_F5", keys.get(7).getAsJsonObject().getAsJsonArray("bindings").get(0).getAsString());
        assertEquals("kept once, found again", id, AgvnKeySets.same(dir, own));
        assertEquals(-1, AgvnKeySets.same(dir, asset(AgvnLayouts.RPG))); // other keys
        AgvnLightLayout again = AgvnLightLayout.parse(AgvnLayouts.RPG, saved.toString(), AgvnHtmlGame.RUNNER_RGSS);
        assertEquals(8, again.elements.size());
        assertEquals("F5", again.elements.get(7).text);
        assertEquals(java.util.Arrays.asList(1, 6, 9001), ids(AgvnKeySets.list(dir)));
    }

    @Test
    public void aNameTakenGetsANumberUnlessThePlayerWritesOverIt() throws Exception {
        File dir = profiles();
        int first = AgvnKeySets.save(dir, asset(AgvnLayouts.VN), "Virtual Gamepad", -1);
        assertEquals("Virtual Gamepad (1)", read(dir, first).get("name").getAsString());
        JsonObject copy = read(dir, first);
        assertFalse("a copy is the player's: AGVN updates no longer replace it", copy.has(AgvnControls.VERSION_KEY));
        assertEquals(asset(AgvnLayouts.VN).contains("\"controllers\""), copy.has("controllers"));
        int over = AgvnKeySets.save(dir, "{\"elements\":[]}", "Virtual Gamepad", 1);
        assertEquals(1, over);
        assertEquals("Virtual Gamepad", read(dir, 1).get("name").getAsString());
        assertEquals(0, read(dir, 1).getAsJsonArray("elements").size());
        int notOverAgvn = AgvnKeySets.save(dir, "{\"elements\":[]}", "AGVN · Visual novel", 9001);
        assertTrue(notOverAgvn != 9001 && notOverAgvn > 0);
        assertEquals("AGVN · Visual novel (1)", read(dir, notOverAgvn).get("name").getAsString());
        assertTrue(read(dir, 9001).has(AgvnControls.VERSION_KEY)); // untouched
        assertEquals(-1, AgvnKeySets.save(dir, "not json", "X", -1));
        assertEquals(-1, AgvnKeySets.save(dir, "{\"name\":\"no keys\"}", "X", -1));
        assertFalse("nothing left half-written beside the profiles", new File(dir.getParentFile(), "controls-1.icp.tmp").exists());
        assertNull(AgvnKeySets.json(dir, 5));
    }

    @Test
    public void loadingASetMakesItThePlayersChoice() {
        Map<String, String> extras = AgvnKeySets.pickExtras(12);
        assertEquals("12", extras.get(AgvnLayouts.EXTRA_PROFILE));
        assertTrue(extras.containsKey(AgvnLayouts.EXTRA_AUTO)); // removed: AGVN does not pick another at launch
        assertNull(extras.get(AgvnLayouts.EXTRA_AUTO));
        assertTrue(extras.containsKey(AgvnControlsFork.EXTRA_HIDDEN)); // removed: the loaded keys show
        assertFalse(AgvnLayouts.shouldAssign(extras.get(AgvnLayouts.EXTRA_PROFILE), extras.get(AgvnLayouts.EXTRA_AUTO)));
        assertFalse(AgvnLayouts.shouldAssign("9002", null)); // an AGVN layout loaded so stays too
        assertEquals(9002, AgvnLightPick.picked("9002", null));
    }
}
