/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.util.Properties;

/**
 * The "Chạy nhẹ" toolkit's settings, in files/agvn-light.properties: keys hidden per game, the keys' opacity, the
 * HUD, and each game's own key set. A file rather than SharedPreferences, because the Ren'Py and RPG Maker games run
 * in processes of their own and SharedPreferences keeps each process's first read. Pure Java.
 */
final class AgvnLightPrefs {
    /** As the Windows controls start (InputControlsView.DEFAULT_OVERLAY_OPACITY). */
    static final float DEFAULT_OPACITY = 0.85f, MIN_OPACITY = 0.15f;
    static final String FILE_NAME = "agvn-light.properties";

    private final File file;
    private final Properties props;

    AgvnLightPrefs(File dir) {
        file = new File(dir, FILE_NAME);
        props = AgvnPropsFile.load(file);
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

    /** The game's own key set ({@link AgvnLightLayout#toJson}), or null: the game type's keys then. */
    String layout(String game) {
        return props.getProperty("layout." + game);
    }

    void setLayout(String game, String json) {
        props.setProperty("layout." + game, json);
        save();
    }

    /** The places and sizes AGVN 0.1.6 to 0.1.8 kept per game type ({@link AgvnLightLayout#apply}), or null. */
    String positions(String kind) {
        return props.getProperty("keys." + kind);
    }

    /** Written whole, then moved over the old file ({@link AgvnPropsFile}): a crash never leaves half a file. */
    private void save() {
        AgvnPropsFile.store(props, file, "AGVN Player: Chạy nhẹ toolkit");
    }
}
