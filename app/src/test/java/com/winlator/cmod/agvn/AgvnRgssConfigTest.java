/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

/** The mkxp.json written before an RPG Maker XP/VX/VX Ace game starts on "Chạy nhẹ". */
public class AgvnRgssConfigTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File folder(File parent, String rel) {
        File f = new File(parent, rel);
        f.mkdirs();
        return f;
    }

    @Test
    public void jsonHasTheGameItsRtpAndTheAppFiles() throws Exception {
        File root = tmp.newFolder("sdcard");
        File game = folder(root, "Game Viet hoa (v1.2)"); // accents and quotes: see quoteEscapes…; file names vary by OS
        Files.write(new File(game, "Game.ini").toPath(),
                "[Game]\nLibrary=System\\RGSS301.dll\nRTP=RPGVXAce\n".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(game, "Game.exe").toPath(), new byte[0]);
        File agvn = folder(root, "AGVN-Player");
        File rtp = folder(agvn, "RTP/RPGVXAce");
        folder(rtp, "Graphics");

        AgvnRgssConfig config = new AgvnRgssConfig(game, agvn, null);
        assertEquals(3, config.rgss);
        assertEquals(Collections.singletonList(rtp), config.rtpDirs);
        assertTrue(config.missingRtp.isEmpty());

        File sf2 = new File(root, "files/rgss/wt_210k_G.sf2");
        File wrap = new File(root, "files/rgss/win32_wrap.rb");
        JsonObject json = JsonParser.parseString(config.json(sf2, Arrays.asList(wrap), true)).getAsJsonObject();
        assertEquals(game.getAbsolutePath(), json.get("gameFolder").getAsString());
        assertEquals("Game", json.get("execName").getAsString());
        assertEquals(3, json.get("rgssVersion").getAsInt());
        assertTrue(json.get("fullscreen").getAsBoolean());
        assertTrue(json.get("frameSkip").getAsBoolean());
        assertEquals(sf2.getAbsolutePath(), json.get("midiSoundFont").getAsString());
        JsonArray rtps = json.getAsJsonArray("RTP");
        assertEquals(1, rtps.size());
        assertEquals(rtp.getAbsolutePath(), rtps.get(0).getAsString());
        assertEquals(wrap.getAbsolutePath(), json.getAsJsonArray("preloadScript").get(0).getAsString());
    }

    @Test
    public void missingRtpIsListedAndLeftOutOfTheJson() throws Exception {
        File game = tmp.newFolder("xp");
        Files.write(new File(game, "Game.ini").toPath(),
                "[Game]\nLibrary=RGSS104E.dll\nRTP1=Standard\n".getBytes(StandardCharsets.UTF_8));
        AgvnRgssConfig config = new AgvnRgssConfig(game, tmp.newFolder("agvn"), tmp.newFolder("drive_c"));
        assertEquals(1, config.rgss);
        assertEquals(Collections.singletonList("Standard"), config.missingRtp);
        JsonObject json = JsonParser.parseString(config.json(null, Collections.emptyList(), false)).getAsJsonObject();
        assertFalse(json.get("frameSkip").getAsBoolean());
        assertEquals(0, json.getAsJsonArray("RTP").size());
        assertEquals("", json.get("midiSoundFont").getAsString());
    }

    @Test
    public void quoteEscapesWhatJsonNeedsAndKeepsLetters() {
        assertEquals("\"a\\\"b\\\\c\\u000ad ạ ゲーム\"", AgvnRgssConfig.quote("a\"b\\c\nd ạ ゲーム"));
    }
}
