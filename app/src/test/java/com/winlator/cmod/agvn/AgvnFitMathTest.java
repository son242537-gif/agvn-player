/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.winlator.cmod.math.XForm;
import com.winlator.cmod.renderer.ViewTransformation;

import org.junit.Test;

/** "Vừa màn hình": which windows are a small frame or cut off, and how they are drawn and touched over the view. */
public class AgvnFitMathTest {
    private static final int W = 2400, H = 1080; // a phone in landscape

    @Test
    public void smallFramesAndCutOffWindows() {
        assertEquals(AgvnFitMath.SMALL, AgvnFitMath.verdict(0, 0, 640, 480, 1280, 720)); // an old game on "Cao"
        assertEquals(AgvnFitMath.OVERFLOW, AgvnFitMath.verdict(0, 0, 1280, 720, 854, 480)); // a fixed 720p window on "Thấp"
        assertEquals(AgvnFitMath.OVERFLOW, AgvnFitMath.verdict(0, 0, 800, 600, 854, 480)); // only the height is cut
        assertEquals(AgvnFitMath.OVERFLOW, AgvnFitMath.verdict(-213, -120, 1280, 720, 854, 480)); // centred, cut all round
        assertNull(AgvnFitMath.verdict(0, 0, 1280, 720, 1280, 720));
        assertNull("4:3 at full height: already as big as it gets", AgvnFitMath.verdict(160, 0, 960, 720, 1280, 720));
        assertNull("within the slack", AgvnFitMath.verdict(0, 0, 1282, 720, 1280, 720));
        assertEquals(AgvnFitMath.OVERFLOW, AgvnFitMath.verdict(0, 0, 1300, 720, 1280, 720));
        assertNull(AgvnFitMath.verdict(0, 0, 0, 0, 1280, 720));
    }

    @Test
    public void aMaximizedWindowsFrameIsNotACut() {
        // Party Me on a Mali-G615: Wine's maximized window hangs 6 px past each edge, the game inside it whole. Each
        // "Đổi màn hình" made the screen 12 px larger and the window with it: 960x544 became 1056x640 in 8 starts.
        for (int grown = 0; grown <= 8; grown++) {
            int sw = 960 + 12 * grown, sh = 544 + 12 * grown;
            assertNull(sw + "x" + sh, AgvnFitMath.verdict(-6, -6, sw + 12, sh + 12, sw, sh));
        }
        assertNull("its frame on one side only", AgvnFitMath.verdict(0, 0, 1068, 652, 1056, 640));
        assertEquals("more than a frame", AgvnFitMath.OVERFLOW, AgvnFitMath.verdict(-6, -6, 1100, 652, 1056, 640));
        assertEquals("a frame plus a cut", AgvnFitMath.OVERFLOW, AgvnFitMath.verdict(-20, -6, 1096, 652, 1056, 640));
    }

    @Test
    public void theWholeScreenDrawsAsBefore() {
        ViewTransformation vt = new ViewTransformation();
        vt.update(W, H, 1280, 720);
        float[] t = AgvnFitMath.render(new float[]{0, 0, 1280, 720}, 1280, 720, W, H, false);
        assertEquals(vt.sceneOffsetX, t[0], 1e-3);
        assertEquals(vt.sceneOffsetY, t[1], 1e-3);
        assertEquals(vt.sceneScaleX, t[2], 1e-5);
        assertEquals(vt.sceneScaleY, t[3], 1e-5);
        assertArrayEquals(new float[]{vt.viewOffsetX, vt.viewOffsetY, vt.viewWidth, vt.viewHeight},
                new float[]{t[4], t[5], t[6], t[7]}, 1);
    }

    @Test
    public void aSmallFrameFillsTheViewInProportion() {
        // 640x480 in the corner of a 1280x720 screen: drawn 1440x1080, centred, so 480 px on either side
        float[] t = AgvnFitMath.render(new float[]{0, 0, 640, 480}, 1280, 720, W, H, false);
        assertArrayEquals(new float[]{256, 0, 1.2f, 1.5f, 480, 0, 1440, 1080}, t, 1e-3f);
        float[] xform = XForm.getInstance();
        AgvnFitMath.touch(xform, new float[]{0, 0, 640, 480}, W, H, false);
        assertArrayEquals(new float[]{0, 0}, XForm.transformPoint(xform, 480, 0), 1e-3f);
        assertArrayEquals(new float[]{640, 480}, XForm.transformPoint(xform, 1920, 1080), 1e-3f);
    }

    @Test
    public void touchesLandWhereTheWindowIsDrawn() {
        for (boolean stretch : new boolean[]{false, true}) {
            float[] rect = {100, 50, 640, 480};
            float[] t = AgvnFitMath.render(rect, 1280, 720, W, H, stretch);
            float[] xform = XForm.getInstance();
            AgvnFitMath.touch(xform, rect, W, H, stretch);
            for (float[] p : new float[][]{{100, 50}, {740, 530}, {420, 290}}) {
                float viewX = (p[0] * t[2] + t[0]) * W / 1280, viewY = (p[1] * t[3] + t[1]) * H / 720;
                assertArrayEquals("stretch " + stretch, p, XForm.transformPoint(xform, viewX, viewY), 1e-2f);
            }
        }
    }

    @Test
    public void aScreenThatHoldsTheWindow() {
        assertEquals("1280x720", AgvnFitMath.screenFor(1280, 720));
        assertEquals("1284x722", AgvnFitMath.screenFor(1283, 721));
        assertEquals("4096x3000", AgvnFitMath.screenFor(5000, 3000));
    }
}
