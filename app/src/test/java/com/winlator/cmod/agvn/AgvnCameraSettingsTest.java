/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AgvnCameraSettingsTest {
    @Test
    public void theDefaultsAreNotKept() {
        AgvnCameraSettings s = AgvnCameraSettings.parse(null);
        assertEquals(1f, s.speed, 0f);
        assertEquals(1f, s.vertical, 0f);
        assertEquals(1f, s.gyroSpeed, 0f);
        assertFalse("tilting the phone turns nothing unless the player wants it", s.gyro);
        assertEquals("", s.format());
        assertNull(s.describe());
        assertEquals("", AgvnCameraSettings.parse("").format());
    }

    @Test
    public void whatThePlayerSetIsReadBack() {
        AgvnCameraSettings s = new AgvnCameraSettings();
        s.speed = AgvnCameraSettings.speedAt(13);
        s.vertical = 0.8f;
        s.gyro = true;
        s.gyroSpeed = 2f;
        AgvnCameraSettings back = AgvnCameraSettings.parse(s.format());
        assertEquals(s.speed, back.speed, 0.001f);
        assertEquals(0.8f, back.vertical, 0f);
        assertTrue(back.gyro);
        assertEquals(2f, back.gyroSpeed, 0f);
        assertEquals("speed=1.516;vertical=0.8;gyro=1;gyroSpeed=2", s.format());
        assertTrue(back.describe().contains("con quay bật"));
    }

    @Test
    public void anythingOddGivesTheDefaultOrTheNearestLimit() {
        AgvnCameraSettings s = AgvnCameraSettings.parse("speed=abc;vertical=9;gyro=yes;gyroSpeed=0.01;x=1;=2;speed");
        assertEquals(1f, s.speed, 0f);
        assertEquals(1.5f, s.vertical, 0f);
        assertFalse(s.gyro);
        assertEquals(0.25f, s.gyroSpeed, 0f);
        assertEquals(1f, AgvnCameraSettings.parse("speed=NaN").speed, 0f);
        assertEquals(1f, AgvnCameraSettings.parse("speed=Infinity;").speed, 0f);
    }

    @Test
    public void theSlidersStepsGoFromAQuarterToFourTimes() {
        assertEquals(0.25f, AgvnCameraSettings.speedAt(0), 1e-6f);
        assertEquals(1f, AgvnCameraSettings.speedAt(AgvnCameraSettings.MIDDLE), 0f);
        assertEquals(4f, AgvnCameraSettings.speedAt(AgvnCameraSettings.STEPS), 1e-6f);
        assertEquals(2f, AgvnCameraSettings.speedAt(15), 1e-6f);
        assertEquals(4f, AgvnCameraSettings.speedAt(99), 1e-6f);
        for (int step = 0; step <= AgvnCameraSettings.STEPS; step++) {
            assertEquals(step, AgvnCameraSettings.stepOf(AgvnCameraSettings.speedAt(step)));
            assertEquals(step, AgvnCameraSettings.verticalStepOf(AgvnCameraSettings.verticalAt(step)));
            float kept = AgvnCameraSettings.parse(speedOnly(step)).speed;
            assertEquals("kept and read back", step, AgvnCameraSettings.stepOf(kept));
        }
        assertEquals(0.5f, AgvnCameraSettings.verticalAt(0), 0f);
        assertEquals(1f, AgvnCameraSettings.verticalAt(AgvnCameraSettings.MIDDLE), 0f);
        assertEquals(1.5f, AgvnCameraSettings.verticalAt(AgvnCameraSettings.STEPS), 0f);
        assertEquals(0, AgvnCameraSettings.stepOf(0.01f));
    }

    private static String speedOnly(int step) {
        AgvnCameraSettings s = new AgvnCameraSettings();
        s.speed = AgvnCameraSettings.speedAt(step);
        return s.format();
    }
}
