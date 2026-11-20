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
 * stretching by itself where the SGSR pass cannot be built (VulkanRendererFilters). "Chỉnh đồ họa" has the switch for
 * one game ({@link #EXTRA_CHOICE}), on any phone: the player's own choice for that game comes before the rest.
 */
public final class AgvnSharpScreen {
    /** Renderer filter modes (VulkanXServerView.setFilterMode). */
    public static final int OFF = 0, SGSR = 2;
    /** Container extra the sidebar's "Lưu cấu hình" writes: "" = the player never saved a choice. */
    public static final String EXTRA_SAVED = "graphicsFilterMode";
    /** Shortcut extra, the player's choice for one game in "Chỉnh đồ họa": "1" on, "0" off, "" = not chosen. */
    public static final String EXTRA_CHOICE = "agvnSharp";

    private AgvnSharpScreen() {}

    /** The filter to start with: {@code configured} is the game's or the container's own setting (0 = none). */
    static int startMode(int configured, String saved, DeviceTier tier, String choice) {
        if ("1".equals(choice)) return configured != OFF ? configured : SGSR;
        if ("0".equals(choice)) return OFF;
        return startMode(configured, saved, tier);
    }

    static int startMode(int configured, String saved, DeviceTier tier) {
        if (configured != OFF) return configured;
        if (saved != null && !saved.isEmpty()) return configured; // the sidebar applies what the player saved
        return tier == DeviceTier.FLAGSHIP ? SGSR : OFF;
    }

    public static int startMode(Context ctx, Container container, int configured, String choice) {
        try {
            return startMode(configured, saved(container), DeviceTierManager.current(ctx), choice);
        } catch (RuntimeException e) {
            return configured; // never in the way of a launch
        }
    }

    /** What the switch in "Chỉnh đồ họa" shows for a game: its own choice, else what this phone starts with. */
    public static boolean on(Context ctx, Container container, int configured, String choice) {
        return startMode(ctx, container, configured, choice) != OFF
                || "".equals(choice == null ? "" : choice) && sidebarOn(container);
    }

    private static boolean sidebarOn(Container container) {
        String saved = saved(container);
        return saved.matches("[1-9][0-9]*");
    }

    /**
     * What the sidebar shows as saved: the player's own saved choice, or SGSR where {@link #startMode} turned it on,
     * so that its switch is on and switching it off works at once.
     */
    public static String sidebarSaved(Context ctx, Container container, int configured, String choice) {
        if ("0".equals(choice)) return String.valueOf(OFF);
        String saved = saved(container);
        if (!saved.isEmpty() && !"1".equals(choice)) return saved;
        if ("1".equals(choice) && saved.matches("[1-9][0-9]*")) return saved; // on, with the filter the player saved
        return startMode(ctx, container, configured, choice) == SGSR && configured == OFF ? String.valueOf(SGSR) : "";
    }

    private static String saved(Container container) {
        String saved = container != null ? container.getExtra(EXTRA_SAVED) : "";
        return saved == null ? "" : saved;
    }
}
