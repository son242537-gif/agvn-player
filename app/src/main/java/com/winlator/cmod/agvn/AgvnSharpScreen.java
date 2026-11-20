/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;

import com.winlator.cmod.container.Container;

/**
 * The filter the app's Vulkan renderer starts with when it stretches the game's picture to the phone's screen. A game
 * drawn at 1280×720 and stretched plainly to a 1220p screen looks soft, which players noticed next to other apps
 * (20/11/2026). On a flagship-tier phone the renderer therefore starts with SGSR (Snapdragon Game Super Resolution: one
 * light pass that keeps edges sharp), unless the player or the game's settings already chose: a filter set for the
 * game or its container, or anything saved from the in-game sidebar ("Lưu cấu hình", off included), is kept as it is.
 * The other tiers start as before: the pass costs GPU time they do not have to spare. The renderer falls back to plain
 * stretching by itself where the SGSR pass cannot be built (VulkanRendererFilters).
 */
public final class AgvnSharpScreen {
    /** Renderer filter modes (VulkanXServerView.setFilterMode). */
    public static final int OFF = 0, SGSR = 2;
    /** Container extra the sidebar's "Lưu cấu hình" writes: "" = the player never saved a choice. */
    public static final String EXTRA_SAVED = "graphicsFilterMode";

    private AgvnSharpScreen() {}

    /** The filter to start with: {@code configured} is the game's or the container's own setting (0 = none). */
    static int startMode(int configured, String saved, DeviceTier tier) {
        if (configured != OFF) return configured;
        if (saved != null && !saved.isEmpty()) return configured; // the sidebar applies what the player saved
        return tier == DeviceTier.FLAGSHIP ? SGSR : OFF;
    }

    public static int startMode(Context ctx, Container container, int configured) {
        try {
            return startMode(configured, saved(container), DeviceTierManager.current(ctx));
        } catch (RuntimeException e) {
            return configured; // never in the way of a launch
        }
    }

    /**
     * What the sidebar shows as saved: the player's own saved choice, or SGSR where {@link #startMode} turned it on,
     * so that its switch is on and switching it off works at once.
     */
    public static String sidebarSaved(Context ctx, Container container, int configured) {
        String saved = saved(container);
        if (!saved.isEmpty()) return saved;
        return startMode(ctx, container, configured) == SGSR && configured == OFF ? String.valueOf(SGSR) : "";
    }

    private static String saved(Container container) {
        String saved = container != null ? container.getExtra(EXTRA_SAVED) : "";
        return saved == null ? "" : saved;
    }
}
