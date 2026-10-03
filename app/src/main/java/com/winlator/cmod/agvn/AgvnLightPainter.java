/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Draws the keys of {@link AgvnLightKeys} as the Windows on-screen controls look: dark see-through shapes with a light
 * edge and a white label, the held ones brighter, the key being edited outlined in yellow.
 */
final class AgvnLightPainter {
    /** The bindings key {@code element} presses now (empty when it is not held). */
    interface Pressed {
        String[] of(int element);
    }

    private static final int SELECTED = 0xFFFFD54F;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final RectF rect = new RectF();

    AgvnLightPainter() {
        line.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        text.setColor(Color.argb(225, 255, 255, 255));
    }

    /** {@code selected}: the key being edited, or -1. */
    void draw(Canvas canvas, AgvnLightLayout layout, int w, int h, Pressed pressed, int selected) {
        float unit = AgvnLightGeometry.unit(w);
        line.setStrokeWidth(Math.max(1.5f, unit * 0.18f));
        for (int i = 0; i < layout.elements.size(); i++) {
            AgvnLightLayout.Element e = layout.elements.get(i);
            float cx = e.x * w, cy = e.y * h, hw = AgvnLightGeometry.halfWidth(e, unit), hh = AgvnLightGeometry.halfHeight(e, unit);
            String[] down = pressed.of(i);
            boolean active = down.length > 0;
            fill.setColor(Color.argb(active ? 175 : 120, 20, 20, 24)); // about the Windows controls' dark surface
            line.setColor(i == selected ? SELECTED : Color.argb(active ? 230 : 120, 255, 255, 255));
            if (e.pad || e.round) {
                canvas.drawCircle(cx, cy, hw, fill);
                canvas.drawCircle(cx, cy, hw, line);
            } else {
                rect.set(cx - hw, cy - hh, cx + hw, cy + hh);
                canvas.drawRoundRect(rect, hh * 0.9f, hh * 0.9f, fill);
                canvas.drawRoundRect(rect, hh * 0.9f, hh * 0.9f, line);
            }
            if (e.pad) arrows(canvas, e, cx, cy, hw, down);
            else label(canvas, e.text, cx, cy, hw * 1.6f, unit * 2.1f * e.scale);
        }
    }

    /** Four arrows, up/right/down/left as the pad's bindings; the held one bright. */
    private void arrows(Canvas canvas, AgvnLightLayout.Element pad, float cx, float cy, float r, String[] down) {
        for (int b = 0; b < 4; b++) {
            double a = Math.PI / 2 * (b - 1); // up, right, down, left
            float ax = cx + (float) Math.cos(a) * r * 0.62f, ay = cy + (float) Math.sin(a) * r * 0.62f, s = r * 0.2f;
            arrow.reset();
            arrow.moveTo(ax + (float) Math.cos(a) * s, ay + (float) Math.sin(a) * s);
            arrow.lineTo(ax + (float) Math.cos(a + 2.2) * s, ay + (float) Math.sin(a + 2.2) * s);
            arrow.lineTo(ax + (float) Math.cos(a - 2.2) * s, ay + (float) Math.sin(a - 2.2) * s);
            arrow.close();
            boolean on = down.length > 0 && b < pad.bindings.length && pad.bindings[b].equals(down[0]);
            fill.setColor(Color.argb(on ? 230 : 140, 255, 255, 255));
            canvas.drawPath(arrow, fill);
        }
    }

    /** The label, shrunk to fit {@code maxWidth}. */
    private void label(Canvas canvas, String label, float cx, float cy, float maxWidth, float size) {
        text.setTextSize(size);
        float width = text.measureText(label);
        if (width > maxWidth && width > 0) text.setTextSize(size * maxWidth / width);
        canvas.drawText(label, cx, cy - (text.descent() + text.ascent()) / 2, text);
    }
}
