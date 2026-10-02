/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The on-screen keys of a "Chạy nhẹ" game: the layout AGVN gives the same game type on Windows ({@link AgvnLayouts}:
 * controls-vn.icp for visual novels, controls-rpg.icp for RPG Maker), read from the .icp JSON. Each key keeps its
 * label, its keys and its place and size; places are fractions of the screen, sizes the .icp's scale. The player's own
 * places and sizes ({@link #positions()}) are kept per game type. Pure Java (JVM-testable).
 */
final class AgvnLightLayout {
    static final float MIN_SCALE = 0.5f, MAX_SCALE = 2.5f;

    static final class Element {
        /** A four-way pad (bindings up, right, down, left), else a button. */
        final boolean pad;
        /** A round button, else a pill. */
        final boolean round;
        final String text;
        final String[] bindings;
        final float x0, y0, scale0;
        float x, y, scale;

        Element(boolean pad, boolean round, String text, String[] bindings, float x, float y, float scale) {
            this.pad = pad;
            this.round = round;
            this.text = text;
            this.bindings = bindings;
            this.x = x0 = x;
            this.y = y0 = y;
            this.scale = scale0 = scale;
        }
    }

    final String kind;
    final List<Element> elements;

    private AgvnLightLayout(String kind, List<Element> elements) {
        this.kind = kind;
        this.elements = Collections.unmodifiableList(elements);
    }

    /** Buttons and pads of an .icp profile; other controls (sticks, touch areas) have no meaning without a mouse. */
    static AgvnLightLayout parse(String kind, String json) {
        List<Element> out = new ArrayList<>();
        try {
            JsonArray elements = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("elements");
            for (JsonElement item : elements) {
                JsonObject e = item.getAsJsonObject();
                String type = text(e, "type");
                if (!type.equals("BUTTON") && !type.equals("D_PAD")) continue;
                JsonArray b = e.getAsJsonArray("bindings");
                String[] bindings = new String[b != null ? b.size() : 0];
                for (int i = 0; i < bindings.length; i++) bindings[i] = b.get(i).getAsString();
                out.add(new Element(type.equals("D_PAD"), text(e, "shape").equals("CIRCLE"), text(e, "text"), bindings,
                        clamp(number(e, "x", 0.5f), 0, 1), clamp(number(e, "y", 0.5f), 0, 1),
                        clamp(number(e, "scale", 1f), MIN_SCALE, MAX_SCALE)));
            }
        } catch (RuntimeException e) {
            // a broken layout: no keys rather than a crash
        }
        return new AgvnLightLayout(kind, out);
    }

    /** "x,y,scale;x,y,scale;…" of every key, as {@link #apply} reads it back. */
    String positions() {
        StringBuilder sb = new StringBuilder();
        for (Element e : elements) {
            if (sb.length() > 0) sb.append(';');
            sb.append(String.format(Locale.ROOT, "%.4f,%.4f,%.3f", e.x, e.y, e.scale));
        }
        return sb.toString();
    }

    /** The player's places and sizes, when they are for this layout (same number of keys); false otherwise. */
    boolean apply(String positions) {
        if (positions == null || positions.isEmpty()) return false;
        String[] keys = positions.split(";");
        if (keys.length != elements.size()) return false;
        float[][] read = new float[keys.length][];
        try {
            for (int i = 0; i < keys.length; i++) {
                String[] p = keys[i].split(",");
                read[i] = new float[]{Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2])};
            }
        } catch (RuntimeException e) {
            return false;
        }
        for (int i = 0; i < keys.length; i++) {
            Element e = elements.get(i);
            e.x = clamp(read[i][0], 0, 1);
            e.y = clamp(read[i][1], 0, 1);
            e.scale = clamp(read[i][2], MIN_SCALE, MAX_SCALE);
        }
        return true;
    }

    /** Back to the places and sizes of the .icp ("Mặc định"). */
    void reset() {
        for (Element e : elements) {
            e.x = e.x0;
            e.y = e.y0;
            e.scale = e.scale0;
        }
    }

    static float clamp(float v, float min, float max) {
        return Float.isNaN(v) ? min : Math.max(min, Math.min(max, v));
    }

    private static String text(JsonObject o, String key) {
        JsonElement v = o.get(key);
        return v != null && v.isJsonPrimitive() ? v.getAsString() : "";
    }

    private static float number(JsonObject o, String key, float fallback) {
        JsonElement v = o.get(key);
        return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() ? v.getAsFloat() : fallback;
    }
}
