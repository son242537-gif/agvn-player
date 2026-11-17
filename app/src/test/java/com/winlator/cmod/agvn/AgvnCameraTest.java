/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AgvnCameraTest {
    @Test
    public void aSlowDragIsNotLost() {
        AgvnCamera camera = new AgvnCamera();
        int x = 0, y = 0;
        for (int i = 0; i < 10; i++) {
            int[] d = camera.take(0.3f, -0.25f);
            x += d[0];
            y += d[1];
        }
        assertEquals("ten moves of 0.3 counts", 3, x);
        assertEquals(-2, y);
    }

    @Test
    public void aSwipeAcrossTheScreenTurnsAsMuchOnEveryPhone() {
        assertEquals(AgvnCamera.COUNTS_PER_SCREEN, AgvnCamera.drag(2400, 2400, 1f), 0.01f);
        assertEquals(AgvnCamera.COUNTS_PER_SCREEN, AgvnCamera.drag(1600, 1600, 1f), 0.01f);
        assertEquals("a quarter of the screen at twice the speed", 1000f, AgvnCamera.drag(600, 2400, 2f), 0.01f);
        assertEquals(0f, AgvnCamera.drag(100, 0, 1f), 0f);
        // the X screen's width before: 854 counts for the whole screen of a weak phone
        assertTrue(AgvnCamera.COUNTS_PER_SCREEN > 2 * 854);
    }

    @Test
    public void theStickIsFineNearItsCentreAndFastAtItsRim() {
        assertEquals(0f, AgvnCamera.curve(0f), 0f);
        assertEquals("its dead zone", 0f, AgvnCamera.curve(AgvnCamera.STICK_DEAD_ZONE), 0f);
        assertEquals(0f, AgvnCamera.curve(Float.NaN), 0f);
        assertEquals(1f, AgvnCamera.curve(1f), 1e-6f);
        assertEquals(1f, AgvnCamera.curve(1.4f), 1e-6f);
        assertTrue("just past the dead zone: slow, for aiming", AgvnCamera.curve(0.25f) < 0.05f);
        assertTrue("half way: well under half", AgvnCamera.curve(0.575f) < 0.35f);
        float before = 0;
        for (float m = 0.16f; m <= 1f; m += 0.01f) {
            float share = AgvnCamera.curve(m);
            assertTrue(m + "", share > before);
            before = share;
        }
    }

    @Test
    public void thePhonesTurnIsReadAsTheScreenShowsIt() {
        // values[0], values[1]: about the phone's own x (right in portrait) and y (up in portrait)
        assertArrayEquals(new float[] {1f, 2f}, AgvnCamera.screenRates(0, 1f, 2f), 0f);
        // ROTATION_90: the phone turned left; the screen's right is the phone's -y, its up the phone's x
        assertArrayEquals(new float[] {-2f, 1f}, AgvnCamera.screenRates(1, 1f, 2f), 0f);
        assertArrayEquals(new float[] {-1f, -2f}, AgvnCamera.screenRates(2, 1f, 2f), 0f);
        assertArrayEquals(new float[] {2f, -1f}, AgvnCamera.screenRates(3, 1f, 2f), 0f);
    }

    @Test
    public void turningThePhoneRightLooksRightAndTiltingItsTopTowardYouLooksUp() {
        // turning right: the screen's right edge comes toward you, a negative turn about the screen's up axis
        float[] right = AgvnCamera.gyro(0f, -1f, 0.01f, 1f);
        assertEquals(AgvnCamera.GYRO_COUNTS_PER_RAD * 0.01f, right[0], 1e-4f);
        assertEquals(0f, right[1], 0f);
        // the top toward you: a positive turn about the screen's right axis; the mouse goes up (negative y)
        float[] up = AgvnCamera.gyro(1f, 0f, 0.01f, 2f);
        assertEquals(0f, up[0], 0f);
        assertEquals(-2 * AgvnCamera.GYRO_COUNTS_PER_RAD * 0.01f, up[1], 1e-4f);
        assertArrayEquals("a hand that holds still", new float[] {0f, 0f},
                AgvnCamera.gyro(0.01f, -0.015f, 0.01f, 1f), 0f);
    }

    @Test
    public void inLandscapeTurningRightLooksRight() {
        // held in landscape (ROTATION_90), turned right about the screen's up axis, which is the phone's own x
        float[] about = AgvnCamera.screenRates(1, -1f, 0f);
        assertTrue(AgvnCamera.gyro(about[0], about[1], 0.01f, 1f)[0] > 0);
        // ROTATION_270: the screen's up axis is the phone's -x
        about = AgvnCamera.screenRates(3, 1f, 0f);
        assertTrue(AgvnCamera.gyro(about[0], about[1], 0.01f, 1f)[0] > 0);
    }
}
