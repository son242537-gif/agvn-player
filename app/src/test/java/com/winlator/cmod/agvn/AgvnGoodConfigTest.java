/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** What "Tự sửa lỗi" keeps per game (AgvnGoodConfig): whose change a setting is, and where the file goes. */
public class AgvnGoodConfigTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static Map<String, String> settings(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    @Test
    public void theDoctorsOwnFixIsNotThePlayersChange() {
        // the game ran well, then crashed; "Đổi DXVK sang bản 1.10.3" was taken and it crashed again: that read "Game bị
        // lỗi với cấu hình mới" and offered only to go back, though the fix had changed nothing (audit of 20/11/2026)
        Properties state = new Properties();
        Map<String, String> good = settings("screenSize", "854x480", "dxwrapperConfig", "version=2.3.1", "envVars", "A=1");
        AgvnGoodConfig.setGood(state, good);
        assertTrue(AgvnGoodConfig.changedByPlayer(state, good).isEmpty());
        Map<String, String> fixed = settings("screenSize", "854x480", "dxwrapperConfig", "version=1.10.3", "envVars", "A=1");
        AgvnGoodConfig.addTried(state, "dxvk-other", good, fixed);
        assertEquals(Collections.singletonList("dxwrapperConfig"), AgvnGoodConfig.changed(AgvnGoodConfig.good(state), fixed));
        assertTrue("the fix alone: no change of the player's", AgvnGoodConfig.changedByPlayer(state, fixed).isEmpty());

        // the player then lowers the screen in "Cấu hình": that is theirs
        Map<String, String> lower = new LinkedHashMap<>(fixed);
        lower.put("screenSize", "640x360");
        assertEquals(Collections.singletonList("screenSize"), AgvnGoodConfig.changedByPlayer(state, lower));
        // and so is another DXVK than the one the fix set
        Map<String, String> other = new LinkedHashMap<>(fixed);
        other.put("dxwrapperConfig", "version=2.4.1");
        assertEquals(Collections.singletonList("dxwrapperConfig"), AgvnGoodConfig.changedByPlayer(state, other));

        // a fix that takes a setting off the game is known by the setting being gone
        Map<String, String> without = new LinkedHashMap<>(fixed);
        without.remove("envVars");
        AgvnGoodConfig.addTried(state, "render-auto", fixed, without);
        assertEquals(Arrays.asList("dxwrapperConfig", "envVars"),
                AgvnGoodConfig.changed(AgvnGoodConfig.good(state), without));
        assertTrue(AgvnGoodConfig.changedByPlayer(state, without).isEmpty());
        assertEquals(Collections.singletonList("envVars"), AgvnGoodConfig.changedByPlayer(state,
                settings("screenSize", "854x480", "dxwrapperConfig", "version=1.10.3", "envVars", "B=2")));

        // both stay tried until the game runs well again; then the fixes and what they set are forgotten
        assertEquals(new java.util.LinkedHashSet<>(Arrays.asList("dxvk-other", "render-auto")), AgvnGoodConfig.tried(state));
        AgvnGoodConfig.setGood(state, without);
        assertTrue(AgvnGoodConfig.tried(state).isEmpty());
        assertNull(state.getProperty(AgvnGoodConfig.FIXED + "dxwrapperConfig"));
        assertNull(state.getProperty(AgvnGoodConfig.FIXED + "envVars"));
        assertEquals("back at 2.3.1 by hand: a change again", Collections.singletonList("dxwrapperConfig"),
                AgvnGoodConfig.changedByPlayer(state, settings("screenSize", "854x480", "dxwrapperConfig", "version=2.3.1")));
    }

    @Test
    public void anUnrealGamesTexturePoolGoesBackWithItsGoodSettings() {
        String pool = AgvnGameImporter.EXTRA_TEXTURE_POOL;
        assertFalse("it follows the Đồ họa step: never a change by itself", AgvnGoodConfig.KEYS.contains(pool));
        // good settings kept before the pool was: the game has a pool, and that is no change
        Properties old = new Properties();
        old.setProperty(AgvnGoodConfig.HAS_GOOD, "1");
        old.setProperty(AgvnGoodConfig.GOOD + "screenSize", "854x480");
        Map<String, String> now = settings("screenSize", "854x480", pool, "1000");
        assertTrue(AgvnGoodConfig.changed(AgvnGoodConfig.good(old), now).isEmpty());
        assertTrue(AgvnGoodConfig.changedByPlayer(old, now).isEmpty());
        assertFalse("none kept: none put back, the game keeps the one it has",
                AgvnGoodConfig.restored(AgvnGoodConfig.good(old), now).containsKey(pool));

        // kept from now on, and what "Trả cấu hình chạy tốt" puts back after a lower Đồ họa step took a smaller one
        Properties state = new Properties();
        AgvnGoodConfig.setGood(state, settings("screenSize", "1280x720", pool, "2000"));
        assertEquals("2000", AgvnGoodConfig.good(state).get(pool));
        Map<String, String> lower = settings("screenSize", "854x480", pool, "1000");
        assertEquals(Collections.singletonList("screenSize"), AgvnGoodConfig.changed(AgvnGoodConfig.good(state), lower));
        assertEquals("2000", AgvnGoodConfig.restored(AgvnGoodConfig.good(state), lower).get(pool));
        AgvnGoodConfig.setGood(state, settings("screenSize", "854x480"));
        assertNull("the pool of the settings before is not kept on", AgvnGoodConfig.good(state).get(pool));
    }

    @Test
    public void theStateFileGoesWithTheGame() throws Exception {
        File dir = tmp.getRoot();
        File toeha = AgvnGoodConfig.file(dir, 3, "Toeha.desktop");
        assertEquals(new File(dir, "agvn/doctor/3-Toeha.properties"), toeha);
        assertEquals("3-Toeha_ Player.properties", AgvnGoodConfig.file(dir, 3, "Toeha: Player.desktop").getName());
        toeha.getParentFile().mkdirs();
        Files.write(toeha.toPath(), "tried=dxvk-other\n".getBytes(StandardCharsets.UTF_8));

        // renamed in "Cấu hình": the fixes tried stay the game's
        File renamed = AgvnGoodConfig.file(dir, 3, "Toeha 1.2.desktop");
        AgvnGoodConfig.carry(toeha, renamed);
        assertFalse(toeha.exists());
        assertEquals(Collections.singleton("dxvk-other"), AgvnGoodConfig.tried(AgvnPropsFile.load(renamed)));

        // moved to Proton 10's container by "Chạy bằng Wine mới" on trial: the trial is asked about there, and what a
        // game of that name left there earlier is not this game's
        File ten = AgvnGoodConfig.file(dir, 7, "Toeha 1.2.desktop");
        Files.write(ten.toPath(), "hasGood=1\n".getBytes(StandardCharsets.UTF_8));
        AgvnGoodConfig.carry(renamed, ten);
        assertFalse(renamed.exists());
        Properties moved = AgvnPropsFile.load(ten);
        assertEquals(Collections.singleton("dxvk-other"), AgvnGoodConfig.tried(moved));
        assertFalse(AgvnGoodConfig.hasGood(moved));

        // a game that kept nothing takes nothing over either
        File none = AgvnGoodConfig.file(dir, 3, "New.desktop"), stale = AgvnGoodConfig.file(dir, 7, "New.desktop");
        Files.write(stale.toPath(), "hasGood=1\n".getBytes(StandardCharsets.UTF_8));
        AgvnGoodConfig.carry(none, stale);
        assertFalse(stale.exists());
        AgvnGoodConfig.carry(ten, ten);
        assertTrue("onto itself: left alone", ten.isFile());
    }
}
