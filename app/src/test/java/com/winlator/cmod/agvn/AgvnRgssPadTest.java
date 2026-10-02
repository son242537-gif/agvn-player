/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The on-screen direction pad of RPG Maker XP/VX/VX Ace games: four directions, a dead zone, room for a thumb. */
public class AgvnRgssPadTest {
    private final AgvnRgssPad pad = new AgvnRgssPad(200, 500, 100);

    @Test
    public void fourDirectionsInScreenCoordinates() {
        assertEquals(0, pad.direction(290, 510)); // right
        assertEquals(1, pad.direction(210, 590)); // down: y grows downwards
        assertEquals(2, pad.direction(110, 490)); // left
        assertEquals(3, pad.direction(190, 410)); // up
    }

    @Test
    public void strongerAxisWinsOnDiagonals() {
        assertEquals(0, pad.direction(280, 440)); // more right than up
        assertEquals(3, pad.direction(240, 400)); // more up than right
    }

    @Test
    public void centreIsADeadZoneAndTheEdgeHasRoom() {
        assertEquals(-1, pad.direction(205, 505));
        assertTrue(pad.contains(320, 500));
        assertFalse(pad.contains(330, 500));
        assertEquals(0, pad.direction(400, 500)); // a finger that slid past the edge still steers
    }
}
