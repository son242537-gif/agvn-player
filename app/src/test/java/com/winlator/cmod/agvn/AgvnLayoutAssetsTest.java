/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.winlator.cmod.inputcontrols.Binding;
import com.winlator.cmod.inputcontrols.ControlElement;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The six bundled layouts parse the way ControlsProfile.loadElements reads them (checked with Gson: org.json is a stub here). */
public class AgvnLayoutAssetsTest {
    private static final List<String> ELEMENT_TYPES = Arrays.asList("BUTTON", "D_PAD", "STICK", "RANGE_BUTTON", "TRACKPAD");

    private static String read(String kind) throws Exception {
        File f = new File("src/main/assets/" + AgvnLayouts.assetFor(kind));
        assertTrue("missing " + f, f.isFile());
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void everyLayoutIsValid() throws Exception {
        Set<Integer> ids = new HashSet<>();
        for (String kind : AgvnLayouts.KINDS) {
            JsonObject p = JsonParser.parseString(read(kind)).getAsJsonObject();
            int id = p.get("id").getAsInt();
            assertTrue(kind, id >= 9000 && id <= 9005);
            assertEquals(kind, AgvnLayouts.idFor(kind), id);
            assertTrue(kind, ids.add(id));
            String name = p.get("name").getAsString();
            assertTrue(name, name.startsWith("AGVN · "));
            assertFalse(name, name.toLowerCase(Locale.ROOT).contains("template")); // templates are hidden in pickers
            assertEquals(1f, p.get("cursorSpeed").getAsFloat(), 0f);
            assertTrue(p.get(AgvnControls.VERSION_KEY).getAsInt() >= 1);
            JsonArray elements = p.getAsJsonArray("elements");
            assertTrue(kind, elements.size() > 0 && elements.size() <= 14);
            for (JsonElement e : elements) checkElement(kind, e.getAsJsonObject());
        }
    }

    private static void checkElement(String kind, JsonObject e) {
        String where = kind + ": " + e;
        String type = e.get("type").getAsString();
        assertTrue(where, ELEMENT_TYPES.contains(type));
        ControlElement.Type.valueOf(type);
        ControlElement.Shape.valueOf(e.get("shape").getAsString());
        JsonArray bindings = e.getAsJsonArray("bindings");
        assertEquals(where, 4, bindings.size());
        int bound = 0;
        for (JsonElement b : bindings) {
            Binding binding = Binding.valueOf(b.getAsString()); // throws for unknown names
            assertFalse(where, binding.isGamepad()); // keyboard/mouse only: no virtual XInput pad is claimed
            if (binding != Binding.NONE) bound++;
        }
        assertEquals(where, type.equals("BUTTON") ? 1 : 4, bound);
        assertTrue(where, Binding.valueOf(bindings.get(0).getAsString()) != Binding.NONE);
        double x = e.get("x").getAsDouble(), y = e.get("y").getAsDouble(), scale = e.get("scale").getAsDouble();
        assertTrue(where, x >= 0 && x <= 1 && y >= 0 && y <= 1);
        assertTrue(where, scale >= 0.5 && scale <= 2.5);
        // read with getBoolean/getString/getInt by ControlsProfile.loadElements: must be present
        e.get("toggleSwitch").getAsBoolean();
        // the face text is shrunk to fit the button: longer labels need a bigger button to stay readable
        assertTrue(where, e.get("text").getAsString().length() <= (scale >= 0.9 ? 7 : 6));
        assertEquals(where, 0, e.get("iconId").getAsInt());
        // a camera zone is a large square over the game: drawn fainter than the keys
        assertEquals(where, type.equals("TRACKPAD") ? 0.3 : 0.6, e.get("opacity").getAsDouble(), 1e-9);
        if (e.has("agvnLook")) assertTrue(where, e.get("agvnLook").getAsBoolean()); // read with getBoolean
        assertTrue(where, !e.has("agvnLook") || type.equals("BUTTON") || type.equals("TRACKPAD"));
    }

    @Test
    public void theActionLayoutTurnsTheCameraFromItsZoneAndFromFireAndAim() throws Exception {
        JsonObject p = JsonParser.parseString(read(AgvnLayouts.ACTION)).getAsJsonObject();
        assertTrue("installed copies are refreshed", p.get(AgvnControls.VERSION_KEY).getAsInt() >= 2);
        JsonObject zone = null;
        Set<String> dragged = new HashSet<>();
        for (JsonElement el : p.getAsJsonArray("elements")) {
            JsonObject e = el.getAsJsonObject();
            boolean look = e.has("agvnLook") && e.get("agvnLook").getAsBoolean();
            if (e.get("type").getAsString().equals("TRACKPAD")) {
                assertTrue("the zone turns the camera", look);
                for (JsonElement b : e.getAsJsonArray("bindings")) assertTrue(Binding.valueOf(b.getAsString()).isMouseMove());
                zone = e;
            } else if (look) {
                dragged.add(e.get("text").getAsString());
            }
        }
        assertTrue(zone != null);
        assertEquals(new HashSet<>(Arrays.asList("Bắn", "Ngắm")), dragged);
        // on phones from 16:9 to 22:9 and a 4:3 tablet, the zone is on the screen and on no other control
        int[][] screens = {{1920, 1080}, {2400, 1080}, {2712, 1220}, {1600, 720}, {2048, 1536}};
        for (int[] screen : screens) {
            int[] z = box(zone, screen[0], screen[1]);
            assertTrue(Arrays.toString(z), z[0] >= 0 && z[1] >= 0 && z[2] <= screen[0] && z[3] <= screen[1]);
            for (JsonElement el : p.getAsJsonArray("elements")) {
                JsonObject e = el.getAsJsonObject();
                if (e == zone) continue;
                int[] b = box(e, screen[0], screen[1]);
                assertTrue(e + " under the zone", b[2] <= z[0] || b[0] >= z[2] || b[3] <= z[1] || b[1] >= z[3]);
            }
        }
    }

    /** ControlElement.computeBoundingBox on a screen {@code w} × {@code h} (snapping: a 100th of the width). */
    private static int[] box(JsonObject e, int w, int h) {
        int snap = w / 100, half, halfH;
        String type = e.get("type").getAsString(), shape = e.get("shape").getAsString();
        if (type.equals("BUTTON") && shape.equals("ROUND_RECT")) {
            half = snap * 4;
            halfH = snap * 2;
        } else if (type.equals("BUTTON")) {
            half = halfH = shape.equals("SQUARE") ? (int) (snap * 2.5f) : snap * 3;
        } else if (type.equals("D_PAD")) {
            half = halfH = snap * 8;
        } else {
            half = halfH = snap * 6;
        }
        float scale = e.get("scale").getAsFloat();
        half = (int) (half * scale);
        halfH = (int) (halfH * scale);
        int x = (int) (e.get("x").getAsDouble() * w), y = (int) (e.get("y").getAsDouble() * h);
        return new int[] {x - half, y - halfH, x + half, y + halfH};
    }

    @Test
    public void installRules() {
        String asset = "{\"id\":9001,\"name\":\"AGVN · Visual novel\",\"agvnLayoutVersion\":2,\"elements\":[]}";
        assertEquals(AgvnControls.Install.WRITE, AgvnControls.installAction(null, asset));
        assertEquals(AgvnControls.Install.WRITE, AgvnControls.installAction(" ", asset));
        assertEquals(AgvnControls.Install.WRITE, AgvnControls.installAction("{broken", asset));
        assertEquals(AgvnControls.Install.WRITE,
                AgvnControls.installAction("{\"id\":9001,\"name\":\"AGVN · Visual novel\",\"agvnLayoutVersion\":1}", asset));
        assertEquals(AgvnControls.Install.KEEP,
                AgvnControls.installAction("{\"id\":9001,\"name\":\"AGVN · Visual novel\",\"agvnLayoutVersion\":2}", asset));
        assertEquals(AgvnControls.Install.KEEP,
                AgvnControls.installAction("{\"id\":9001,\"name\":\"x\",\"agvnLayoutVersion\":3}", asset));
        // saved by the controls editor (version key dropped): the player's edits of our layout are kept
        assertEquals(AgvnControls.Install.KEEP,
                AgvnControls.installAction("{\"id\":9001,\"name\":\"AGVN · Visual novel\",\"elements\":[]}", asset));
        // renamed by the player in the profile manager: still our layout, kept and used
        assertEquals(AgvnControls.Install.KEEP,
                AgvnControls.installAction("{\"id\":9001,\"name\":\"My keys\",\"elements\":[]}", asset));
        // not a controls profile: never overwritten, and the kind is not used
        assertEquals(AgvnControls.Install.FOREIGN,
                AgvnControls.installAction("{\"id\":9001,\"name\":\"My keys\"}", asset));
    }

    @Test
    public void pcLayoutKeepsTheLegacyKeys() throws Exception {
        String pc = read(AgvnLayouts.PC);
        for (String key : Arrays.asList("KEY_ESC", "KEY_TAB", "KEY_SHIFT_L", "KEY_CTRL_L", "KEY_ALT_L", "KEY_F1",
                "KEY_INSERT", "KEY_ENTER", "KEY_SPACE", "KEY_BKSP", "KEY_UP"))
            assertTrue(key, pc.contains("\"" + key + "\""));
        assertNotEquals(read(AgvnLayouts.VN), pc);
    }
}
