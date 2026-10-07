/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.util.Properties;

/**
 * The "Chạy nhẹ" toolkit's settings, in files/agvn-light.properties: keys hidden per game, the keys' opacity, the
 * HUD, each game's own key set and the key profile it was made from, and the profile picked for each game when it
 * last ran ({@link AgvnLightPick}). A file rather than SharedPreferences, because the Ren'Py and RPG Maker games run
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

    /** Keeps {@code json} as the game's own key set, made from the key profile {@code base} (0: AGVN's keys). */
    void setLayout(String game, String json, int base) {
        props.setProperty("layout." + game, json);
        props.setProperty("layoutBase." + game, String.valueOf(base));
        save();
    }

    /** The key profile the game's own key set was made from; 0 for AGVN's keys, as every set before 0.1.29. */
    int layoutBase(String game) {
        return number(props.getProperty("layoutBase." + game));
    }

    /** The key profile picked for the game when it last ran ({@link AgvnLightPick#id}). */
    int lastPick(String game) {
        return number(props.getProperty("pick." + game));
    }

    /** The game now runs with {@code pick}, newly picked: its keys show even if hidden, unless "Tắt" was picked. */
    void picked(String game, int pick) {
        props.setProperty("pick." + game, String.valueOf(pick));
        if (pick != AgvnLightPick.OFF) props.remove("hidden." + game);
        save();
    }

    /** The places and sizes AGVN 0.1.6 to 0.1.8 kept per game type ({@link AgvnLightLayout#apply}), or null. */
    String positions(String kind) {
        return props.getProperty("keys." + kind);
    }

    private static int number(String value) {
        try {
            return value != null ? Integer.parseInt(value.trim()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Written whole, then moved over the old file ({@link AgvnPropsFile}): a crash never leaves half a file. */
    private void save() {
        AgvnPropsFile.store(props, file, "AGVN Player: Chạy nhẹ toolkit");
    }
}
