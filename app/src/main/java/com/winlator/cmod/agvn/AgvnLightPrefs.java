/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * The "Chạy nhẹ" toolkit's settings, in files/agvn-light.properties: keys hidden per game, the keys' opacity, the
 * HUD, and the player's key places per game type. A file rather than SharedPreferences, because the Ren'Py and RPG
 * Maker games run in processes of their own and SharedPreferences keeps each process's first read. Pure Java.
 */
final class AgvnLightPrefs {
    /** As the Windows controls start (InputControlsView.DEFAULT_OVERLAY_OPACITY). */
    static final float DEFAULT_OPACITY = 0.85f, MIN_OPACITY = 0.15f;
    static final String FILE_NAME = "agvn-light.properties";

    private final File file;
    private final Properties props = new Properties();

    AgvnLightPrefs(File dir) {
        file = new File(dir, FILE_NAME);
        try (InputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            // first use: the defaults
        }
    }

    boolean keysHidden(String game) {
        return "1".equals(props.getProperty("hidden." + game));
    }

    void setKeysHidden(String game, boolean hidden) {
        if (hidden) props.setProperty("hidden." + game, "1");
        else props.remove("hidden." + game);
        save();
    }

    float opacity() {
        try {
            return AgvnLightLayout.clamp(Float.parseFloat(props.getProperty("opacity", "")), MIN_OPACITY, 1f);
        } catch (NumberFormatException e) {
            return DEFAULT_OPACITY;
        }
    }

    void setOpacity(float opacity) {
        props.setProperty("opacity", String.valueOf(AgvnLightLayout.clamp(opacity, MIN_OPACITY, 1f)));
        save();
    }

    boolean hud() {
        return "1".equals(props.getProperty("hud"));
    }

    void setHud(boolean on) {
        props.setProperty("hud", on ? "1" : "0");
        save();
    }

    /** The player's places and sizes for game type {@code kind} ({@link AgvnLightLayout#positions()}), or null. */
    String positions(String kind) {
        return props.getProperty("keys." + kind);
    }

    void setPositions(String kind, String positions) {
        if (positions == null) props.remove("keys." + kind);
        else props.setProperty("keys." + kind, positions);
        save();
    }

    /** Written whole to a temporary file, then renamed over the old one: a crash never leaves half a file. */
    private void save() {
        File tmp = new File(file.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            props.store(out, "AGVN Player: Chạy nhẹ toolkit");
        } catch (IOException e) {
            return;
        }
        if (!tmp.renameTo(file)) tmp.delete();
    }
}
