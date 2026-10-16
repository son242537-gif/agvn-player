/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** The "Chạy nhẹ" toolkit: the Windows layouts as keys, what each key sends, where keys sit, settings and the HUD. */
public class AgvnLightToolsTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static AgvnLightLayout layout(String kind) throws Exception {
        File f = new File("src/main/assets/" + AgvnLayouts.assetFor(kind));
        return AgvnLightLayout.parse(kind, new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void readsTheWindowsLayouts() throws Exception {
        AgvnLightLayout rpg = layout(AgvnLayouts.RPG);
        assertEquals(7, rpg.elements.size());
        AgvnLightLayout.Element pad = rpg.elements.get(0);
        assertTrue(pad.pad);
        assertArrayEquals(new String[]{"KEY_UP", "KEY_RIGHT", "KEY_DOWN", "KEY_LEFT"}, pad.bindings);
        assertEquals(0.13f, pad.x, 1e-6);
        assertEquals("OK", rpg.elements.get(1).text);
        assertTrue(rpg.elements.get(1).round);

        AgvnLightLayout vn = layout(AgvnLayouts.VN);
        assertEquals(6, vn.elements.size());
        assertEquals("Tiếp", vn.elements.get(5).text);
        assertFalse(vn.elements.get(5).round); // a pill
        assertEquals("MOUSE_SCROLL_UP", vn.elements.get(1).bindings[0]);
        assertTrue(AgvnLightLayout.parse("vn", "not json").elements.isEmpty());
    }

    @Test
    public void keysSendTheWindowsBindings() {
        assertEquals(KeyEvent.KEYCODE_CTRL_LEFT, AgvnLightActions.of("KEY_CTRL_L")); // "Tua": skip while held
        assertEquals(KeyEvent.KEYCODE_ESCAPE, AgvnLightActions.of("KEY_ESC"));
        assertEquals(KeyEvent.KEYCODE_Z, AgvnLightActions.of("KEY_Z"));
        assertEquals("KEY_ENTER", AgvnLightActions.defaultBinding(AgvnHtmlGame.RUNNER_RGSS, "KEY_Z")); // OK confirms in every RGSS version
        assertEquals("KEY_Z", AgvnLightActions.defaultBinding(AgvnHtmlGame.RUNNER_HTML, "KEY_Z")); // MV: Z is OK itself
        assertEquals(KeyEvent.KEYCODE_F5, AgvnLightActions.of("KEY_F5"));
        assertEquals(KeyEvent.KEYCODE_PAGE_UP, AgvnLightActions.of("KEY_PG_UP"));
        assertEquals(AgvnLightActions.RIGHT_CLICK, AgvnLightActions.of("MOUSE_RIGHT_BUTTON"));
        assertEquals(AgvnLightActions.SCROLL_UP, AgvnLightActions.of("MOUSE_SCROLL_UP"));
        assertEquals(AgvnLightActions.NONE, AgvnLightActions.of("GAMEPAD_BUTTON_A"));
        assertEquals(AgvnLightActions.NONE, AgvnLightActions.of(null));
        assertArrayEquals(new String[]{"z", "KeyZ", "90"}, AgvnLightActions.dom(KeyEvent.KEYCODE_Z));
        assertArrayEquals(new String[]{"ArrowLeft", "ArrowLeft", "37"}, AgvnLightActions.dom(KeyEvent.KEYCODE_DPAD_LEFT));
        assertArrayEquals(new String[]{" ", "Space", "32"}, AgvnLightActions.dom(KeyEvent.KEYCODE_SPACE));
        assertNull(AgvnLightActions.dom(KeyEvent.KEYCODE_VOLUME_UP));
    }

    @Test
    public void keysSitAndAreHitAsOnWindows() throws Exception {
        AgvnLightLayout rpg = layout(AgvnLayouts.RPG);
        int w = 2400, h = 1080; // unit 24 px: the OK circle (scale 0.9) is 64.8 px across the radius
        AgvnLightLayout.Element ok = rpg.elements.get(1);
        assertEquals(64.8f, AgvnLightGeometry.halfWidth(ok, AgvnLightGeometry.unit(w)), 1e-3);
        assertEquals(1, AgvnLightGeometry.hit(rpg.elements, 0.865f * w + 60, 0.86f * h, w, h));
        assertEquals(-1, AgvnLightGeometry.hit(rpg.elements, w / 2f, h / 2f, w, h));
        AgvnLightLayout.Element pad = rpg.elements.get(0);
        float cx = pad.x * w, cy = pad.y * h;
        assertEquals(1, AgvnLightGeometry.padPart(pad, cx + 90, cy + 10, w, h)); // right
        assertEquals(2, AgvnLightGeometry.padPart(pad, cx + 10, cy + 90, w, h)); // down: y grows downwards
        assertEquals(3, AgvnLightGeometry.padPart(pad, cx - 90, cy - 10, w, h)); // left
        assertEquals(0, AgvnLightGeometry.padPart(pad, cx - 10, cy - 90, w, h)); // up
        assertEquals(1, AgvnLightGeometry.padPart(pad, cx + 80, cy - 60, w, h)); // the stronger axis wins
        assertEquals(-1, AgvnLightGeometry.padPart(pad, cx + 5, cy + 5, w, h)); // dead zone
        assertEquals(1, AgvnLightGeometry.padPart(pad, cx + 600, cy, w, h)); // slid past the edge, still steering
        assertArrayEquals(new float[]{0.5f, 1f}, AgvnLightGeometry.moved(1300, 5000, 100, 0, w, h), 1e-6f);
    }

    @Test
    public void settingsLiveInAFileEveryProcessReads() throws Exception {
        File dir = tmp.newFolder("files");
        AgvnLightPrefs prefs = new AgvnLightPrefs(dir);
        assertEquals(AgvnLightPrefs.DEFAULT_OPACITY, prefs.opacity(), 1e-6);
        assertFalse(prefs.hud());
        prefs.setKeysHidden("/sdcard/Games/A", true);
        prefs.setOpacity(0.05f); // too faint: kept at the minimum
        prefs.setHud(true);
        prefs.setLayout("/sdcard/Games/A", "{\"elements\":[]}", 0);
        AgvnLightPrefs other = new AgvnLightPrefs(dir); // another process, later
        assertTrue(other.keysHidden("/sdcard/Games/A"));
        assertFalse(other.keysHidden("/sdcard/Games/B"));
        assertEquals(AgvnLightPrefs.MIN_OPACITY, other.opacity(), 1e-6);
        assertTrue(other.hud());
        assertEquals("{\"elements\":[]}", other.layout("/sdcard/Games/A"));
        assertNull(other.layout("/sdcard/Games/B")); // a game with no key set of its own: its type's keys
        assertNull(other.positions("rpg"));
        java.util.Properties old = AgvnPropsFile.load(new File(dir, AgvnLightPrefs.FILE_NAME));
        old.setProperty("keys.rpg", "0.1,0.2,1.0"); // as AGVN 0.1.6-0.1.8 wrote it
        assertTrue(AgvnPropsFile.store(old, new File(dir, AgvnLightPrefs.FILE_NAME), null));
        assertEquals("0.1,0.2,1.0", new AgvnLightPrefs(dir).positions("rpg"));
        other.setKeysHidden("/sdcard/Games/A", false);
        assertFalse(new AgvnLightPrefs(dir).keysHidden("/sdcard/Games/A"));
    }

    /** The settings file is replaced whole, also over one that exists (File.renameTo refuses that on Windows). */
    @Test
    public void settingsFileIsReplacedWhole() throws Exception {
        File dir = tmp.newFolder("props");
        File file = new File(dir, "a.properties");
        java.util.Properties props = new java.util.Properties();
        for (int i = 1; i <= 3; i++) {
            props.setProperty("n", String.valueOf(i));
            assertTrue(AgvnPropsFile.store(props, file, null));
            assertEquals(String.valueOf(i), AgvnPropsFile.load(file).getProperty("n"));
        }
        assertArrayEquals(new String[]{"a.properties"}, dir.list()); // no .tmp left behind
        File busy = new File(dir, "busy.properties");
        assertTrue(new File(busy, "inside").mkdirs()); // a folder in the way: the move fails
        assertFalse(AgvnPropsFile.store(props, busy, null));
        assertTrue(new File(busy, "inside").isDirectory());
        assertFalse(new File(dir, "busy.properties.tmp").exists());
        assertTrue(AgvnPropsFile.load(new File(dir, "missing")).isEmpty());
    }

    @Test
    public void hudLeavesOutWhatItCannotMeasure() {
        assertEquals("FPS 60 · Game 412 MB · RAM trống 2,3 GB · Pin 78% · 38,5°C", AgvnLightHud.text(60, 412, 2355, 78, 38.5f));
        assertEquals("RAM trống 1,0 GB · Pin 5%", AgvnLightHud.text(-1, -1, 1024, 5, Float.NaN));
    }

    @Test
    public void htmlKeysForceTheKeyCode() {
        String js = AgvnHtmlKeys.press(AgvnLightActions.dom(KeyEvent.KEYCODE_ESCAPE), true);
        assertTrue(js.contains("'keydown'") && js.contains("key:'Escape'") && js.contains("n=27;"));
        assertEquals("'it\\'s \\u003c/script>'", AgvnHtmlKeys.js("it's </script>"));
        assertTrue(AgvnHtmlKeys.mouse(AgvnLightActions.RIGHT_CLICK).contains("m('mousedown',2,2)"));
        assertTrue(AgvnHtmlKeys.mouse(AgvnLightActions.SCROLL_UP).contains("deltaY:-120"));
        assertEquals("", AgvnHtmlKeys.mouse(KeyEvent.KEYCODE_A));
        String typed = AgvnHtmlKeys.typed("a", "KeyA", 65);
        assertTrue(typed.contains("'keypress'") && typed.contains("n=97;") && typed.contains("n=65;"));
    }
}
