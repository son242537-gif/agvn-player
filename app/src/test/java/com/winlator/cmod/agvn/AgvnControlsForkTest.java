/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Per-game profile copies of the in-game controls editor (pure parts; Gson, since org.json is a stub here). */
public class AgvnControlsForkTest {
    private static Set<Integer> ids(Integer... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    @Test
    public void reservedIdsAreLegacyAndAgvnRange() {
        assertTrue(AgvnControlsFork.isReservedId(900));
        assertTrue(AgvnControlsFork.isReservedId(9000));
        assertTrue(AgvnControlsFork.isReservedId(9005));
        assertTrue(AgvnControlsFork.isReservedId(9099));
        assertFalse(AgvnControlsFork.isReservedId(899));
        assertFalse(AgvnControlsFork.isReservedId(901));
        assertFalse(AgvnControlsFork.isReservedId(9100));
        assertFalse(AgvnControlsFork.isReservedId(1));
    }

    @Test
    public void forkIdIsAboveThePlayersProfilesAndNeverReserved() {
        assertEquals(1, AgvnControlsFork.nextForkId(Collections.<Integer>emptySet()));
        assertEquals(5, AgvnControlsFork.nextForkId(ids(1, 2, 3, 4)));
        // bundled layouts 9000-9005 do not push new ids up; the legacy 900 on disk does
        assertEquals(5, AgvnControlsFork.nextForkId(ids(1, 4, 9000, 9001, 9005)));
        assertEquals(901, AgvnControlsFork.nextForkId(ids(1, 2, 900, 9000, 9005)));
        assertEquals(901, AgvnControlsFork.nextForkId(ids(899)));
        assertEquals(9100, AgvnControlsFork.nextForkId(ids(8999, 9003)));
        assertEquals(9101, AgvnControlsFork.nextForkId(ids(3, 9100)));
        int id = AgvnControlsFork.nextForkId(ids(1, 2, 900, 901, 9000, 9001, 9002, 9003, 9004, 9005));
        assertEquals(902, id);
        assertFalse(AgvnControlsFork.isReservedId(id));
    }

    @Test
    public void profileIdsComeFromFileNames() {
        assertEquals(12, AgvnControlsFork.idFromFileName("controls-12.icp"));
        assertEquals(9000, AgvnControlsFork.idFromFileName("controls-9000.icp"));
        assertEquals(-1, AgvnControlsFork.idFromFileName("controls-x.icp"));
        assertEquals(-1, AgvnControlsFork.idFromFileName("controls-12.icp.bak"));
        assertEquals(-1, AgvnControlsFork.idFromFileName("notes.txt"));
        assertEquals(-1, AgvnControlsFork.idFromFileName(null));
        assertEquals(ids(1, 9000), AgvnControlsFork.idsOf(new String[]{"controls-1.icp", "controls-9000.icp", "x.icp"}));
        assertTrue(AgvnControlsFork.idsOf(null).isEmpty());
    }

    @Test
    public void forkJsonRenamesRenumbersAndDropsTheLayoutVersion() {
        String asset = "{\"id\":9001,\"name\":\"AGVN · Visual novel\",\"cursorSpeed\":1,\"agvnLayoutVersion\":1,"
                + "\"elements\":[{\"type\":\"BUTTON\",\"text\":\"Tiếp\",\"scale\":0.7,\"x\":0.95,\"y\":0.4},"
                + "{\"type\":\"D_PAD\",\"text\":\"\",\"scale\":0.7,\"x\":0.16,\"y\":0.8}]}";
        JsonObject fork = JsonParser.parseString(AgvnControlsFork.forkJson(asset, 12, "Cô gái Hà Nội")).getAsJsonObject();
        assertEquals(12, fork.get("id").getAsInt());
        assertEquals("Cô gái Hà Nội", fork.get("name").getAsString());
        assertFalse(fork.has(AgvnControls.VERSION_KEY));
        assertEquals(2, fork.getAsJsonArray("elements").size());
        assertEquals("Tiếp", fork.getAsJsonArray("elements").get(0).getAsJsonObject().get("text").getAsString());
        assertEquals(0.7, fork.getAsJsonArray("elements").get(1).getAsJsonObject().get("scale").getAsDouble(), 1e-9);
        assertFalse(JsonParser.parseString(AgvnControlsFork.forkJson("{\"id\":2,\"name\":\"T\",\"template\":true,"
                + "\"elements\":[]}", 3, "G")).getAsJsonObject().has("template"));
    }

    @Test
    public void onlyTheGamesOwnCopyIsEditedInPlace() {
        assertFalse(AgvnControlsFork.needsFork(12, "12", true));
        assertFalse(AgvnControlsFork.needsFork(12, " 12 ", true));
        assertTrue(AgvnControlsFork.needsFork(9001, null, true));
        assertTrue(AgvnControlsFork.needsFork(900, "12", true));
        assertTrue(AgvnControlsFork.needsFork(4, null, true)); // a profile picked from the list may be shared
        assertTrue(AgvnControlsFork.needsFork(9000, null, false)); // no game: still never edit a bundled layout
        assertFalse(AgvnControlsFork.needsFork(4, null, false));
    }

    @Test
    public void storedIdsParseOrMeanNone() {
        assertEquals(12, AgvnControlsFork.parseId("12"));
        assertEquals(9001, AgvnControlsFork.parseId(" 9001 "));
        assertEquals(-1, AgvnControlsFork.parseId("0"));
        assertEquals(-1, AgvnControlsFork.parseId(""));
        assertEquals(-1, AgvnControlsFork.parseId(null));
        assertEquals(-1, AgvnControlsFork.parseId("abc"));
    }
}
