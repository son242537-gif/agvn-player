/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.content.Intent;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;

import java.io.File;
import java.util.Map;

/**
 * "Toàn màn hình cho mọi game" (Cài đặt, off by default; anh Sơn, 11/10/2026): every game's picture stretched over the
 * whole screen, no black bars at its sides, in every runner: Wine's X screen (picture and touch, as the game's own
 * "Kéo giãn toàn màn hình"), mkxp-z (fixedAspectRatio off), RPG Maker MV/MZ pages (html-compat.js) and Ren'Py
 * (config.adjust_view_size, from the app's main.py). A game's own choice ("1" or "0") wins, then its environment's.
 */
public final class AgvnFullscreen {
    /** The setting. */
    public static final String PREF = "agvn_fullscreen_all";
    /** A game's own choice: "1" stretched, "0" kept in proportion, "" (none) as its environment and the setting say. */
    public static final String EXTRA = "fullscreenStretched";
    /** On a "Chạy nhẹ" runner's intent: true when that game is stretched ({@link #forLight}). */
    public static final String INTENT = "agvnStretch";

    private AgvnFullscreen() {}

    /** True when "Toàn màn hình cho mọi game" is on. */
    public static boolean all(Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(PREF, false);
    }

    /** The game's own choice, else its environment's, else the setting. */
    public static boolean stretch(String gameChoice, boolean environment, boolean all) {
        if ("1".equals(gameChoice)) return true;
        if ("0".equals(gameChoice)) return false;
        return environment || all;
    }

    /** What a game's editor shows for it before it has a choice of its own. */
    public static String inherited(Context ctx, Container container) {
        return stretch(null, container != null && container.isFullscreenStretched(), all(ctx)) ? "1" : "0";
    }

    /**
     * What a game editor stores when it is saved: nothing when the box shows what the game inherits and the game had
     * no choice, so that the setting still applies to it later; else its choice.
     */
    public static String toStore(boolean checked, String before, String inherited) {
        String chosen = checked ? "1" : "0";
        return (before == null || before.isEmpty()) && chosen.equals(inherited) ? null : chosen;
    }

    /**
     * For a "Chạy nhẹ" game ({@code extras}: its shortcut's): whether it is stretched, put on {@code from} as
     * {@link #INTENT}, which the runner's intent copies. Returns it.
     */
    public static boolean forLight(Context ctx, Intent from, Map<String, String> extras) {
        boolean environment = false;
        try {
            String path = from.getStringExtra("shortcut_path");
            int id = from.getIntExtra("container_id", 0);
            if (id == 0 && path != null) id = AgvnHtmlGame.containerIdIn(new File(path));
            Container c = id != 0 ? new ContainerManager(ctx).getContainerById(id) : null;
            environment = c != null && c.isFullscreenStretched();
        } catch (RuntimeException ignored) {
            // no environment to ask: the game's choice and the setting decide
        }
        boolean stretch = stretch(extras.get(EXTRA), environment, all(ctx));
        from.putExtra(INTENT, stretch);
        return stretch;
    }
}
