/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * The on-screen keys of a "Chạy nhẹ" game. They start as the layout AGVN gives the same game type on Windows
 * ({@link AgvnLayouts}: controls-vn.icp for visual novels, controls-rpg.icp for RPG Maker), read from the .icp JSON.
 * The player can move, resize, re-key, add and delete keys ({@link AgvnLightEditor}); the result is the game's own key
 * set, kept in the .icp's own form ({@link #toJson}). Places are fractions of the screen, sizes the .icp's scale.
 * Pure Java (JVM-testable).
 */
final class AgvnLightLayout {
    static final float MIN_SCALE = 0.5f, MAX_SCALE = 2.5f;
    /** A key the player adds: a pill as the Windows editor's new button, an arrow pad as the RPG layout's. */
    static final float NEW_BUTTON_SCALE = 0.7f, NEW_PAD_SCALE = 0.9f;

    static final class Element {
        /** A four-way pad (bindings up, right, down, left), else a button. */
        final boolean pad;
        /** A round button, else a pill. */
        final boolean round;
        String text;
        String[] bindings;
        float x, y, scale;

        Element(boolean pad, boolean round, String text, String[] bindings, float x, float y, float scale) {
            this.pad = pad;
            this.round = round;
            this.text = text;
            this.bindings = bindings;
            this.x = x;
            this.y = y;
            this.scale = scale;
        }

        /** A new pill in the middle of the screen: {@code binding}, with {@code text} on it. */
        static Element button(String text, String binding) {
            return new Element(false, false, text, new String[]{binding}, 0.5f, 0.5f, NEW_BUTTON_SCALE);
        }

        /** A new arrow pad in the middle of the screen: {@code directions} up, right, down, left. */
        static Element pad(String[] directions) {
            return new Element(true, true, "", directions.clone(), 0.5f, 0.5f, NEW_PAD_SCALE);
        }
    }

    final String kind;
    final List<Element> elements = new ArrayList<>();
    /** The .icp, and the runner its keys go to, for {@link #reset}. */
    private final String defaults, runner;

    private AgvnLightLayout(String kind, String defaults, String runner) {
        this.kind = kind;
        this.defaults = defaults;
        this.runner = runner;
        reset();
    }

    /** The .icp's keys as they are (the Windows names); a broken .icp has none. */
    static AgvnLightLayout parse(String kind, String json) {
        return parse(kind, json, null);
    }

    /** The .icp's keys as {@code runner}'s game gets them ({@link AgvnLightActions#defaultBinding}). */
    static AgvnLightLayout parse(String kind, String json, String runner) {
        return new AgvnLightLayout(kind, json, runner);
    }

    /** Back to the .icp's keys, places and sizes ("Mặc định"). */
    void reset() {
        List<Element> read = read(defaults, runner);
        elements.clear();
        if (read != null) elements.addAll(read);
    }

    /** The game's own key set ({@link #toJson}) instead; false, the keys unchanged, when it cannot be read. */
    boolean load(String json) {
        List<Element> read = json == null || json.isEmpty() ? null : read(json, null);
        if (read == null) return false;
        elements.clear();
        elements.addAll(read);
        return true;
    }

    /** Every key in the .icp's form, {"elements": [...]}, as {@link #load} reads it back. */
    String toJson() {
        JsonArray all = new JsonArray();
        for (Element e : elements) {
            JsonObject o = new JsonObject();
            o.addProperty("type", e.pad ? "D_PAD" : "BUTTON");
            o.addProperty("shape", e.pad || e.round ? "CIRCLE" : "ROUND_RECT");
            o.addProperty("text", e.text);
            JsonArray bindings = new JsonArray();
            for (String b : e.bindings) bindings.add(b);
            o.add("bindings", bindings);
            o.addProperty("x", e.x);
            o.addProperty("y", e.y);
            o.addProperty("scale", e.scale);
            all.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("elements", all);
        return root.toString();
    }

    /**
     * The player's places and sizes of AGVN 0.1.6 to 0.1.8, kept per game type as "x,y,scale;…", for a game without a
     * key set of its own; false when they are not for this layout (another number of keys).
     */
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

    /**
     * The buttons and pads of an .icp profile, the bindings as {@code runner}'s game gets them; null when it is not
     * one. Other controls (sticks, touch areas) have no meaning without a mouse.
     */
    private static List<Element> read(String json, String runner) {
        List<Element> out = new ArrayList<>();
        try {
            for (JsonElement item : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("elements")) {
                JsonObject e = item.getAsJsonObject();
                String type = text(e, "type");
                if (!type.equals("BUTTON") && !type.equals("D_PAD")) continue;
                JsonArray b = e.getAsJsonArray("bindings");
                String[] bindings = new String[b != null ? b.size() : 0];
                for (int i = 0; i < bindings.length; i++) bindings[i] = AgvnLightActions.defaultBinding(runner, b.get(i).getAsString());
                out.add(new Element(type.equals("D_PAD"), text(e, "shape").equals("CIRCLE"), text(e, "text"), bindings,
                        clamp(number(e, "x", 0.5f), 0, 1), clamp(number(e, "y", 0.5f), 0, 1),
                        clamp(number(e, "scale", 1f), MIN_SCALE, MAX_SCALE)));
            }
        } catch (RuntimeException e) {
            return null; // not a key set: no keys rather than a crash
        }
        return out;
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
