/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The right mouse button of an HTML game in "Chạy nhẹ". RPG Maker MV's action games (QABS and the plugins of its kind)
 * put their main attack on it ("mouse2" among their keys) and shoot while it is held, aiming at the pointer. A finger
 * gives the page touches and left clicks only, and the key set's right button clicked once at the centre of the
 * screen: such a game could not attack at all (Xiaomi, Mali-G720, 07/11/2026). The key now holds the
 * button as long as it is held, where the player last touched on an RPG Maker page ({@link AgvnHtmlKeys#rightButton}),
 * and a game whose plugins.js names "mouse2" gets a "Chuột phải" key in its default key set. Pure Java.
 */
final class AgvnHtmlRightButton {
    static final String BINDING = "MOUSE_RIGHT_BUTTON";
    /** plugins.js is read this far: RPG Maker writes the whole plugin list there, a few hundred KB at most. */
    static final int MAX_PLUGINS = 4 << 20;
    /** Where the added key goes: under Space in the RPG key set's column of keys. */
    static final float X = 0.95f, Y = 0.46f;

    private AgvnHtmlRightButton() {}

    /** True when {@code root}'s js/plugins.js gives a key to the right mouse button ("mouse2"). */
    static boolean wanted(File root) {
        return root != null && named(read(new File(root, "js/plugins.js")));
    }

    static boolean named(String pluginsJs) {
        return pluginsJs != null && pluginsJs.toLowerCase(java.util.Locale.ROOT).contains("mouse2");
    }

    /** Adds the right button to {@code layout} as {@code label}, unless a key of it gives that button already. */
    static boolean add(AgvnLightLayout layout, String label) {
        for (AgvnLightLayout.Element e : layout.elements) {
            for (String b : e.bindings) if (BINDING.equals(b)) return false;
        }
        AgvnLightLayout.Element key = AgvnLightLayout.Element.button(label, BINDING);
        key.x = X;
        key.y = Y;
        layout.elements.add(key);
        return true;
    }

    private static String read(File file) {
        if (!file.isFile()) return null;
        byte[] buf = new byte[(int) Math.min(file.length(), MAX_PLUGINS)];
        try (InputStream in = new FileInputStream(file)) {
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            return new String(buf, 0, n, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
