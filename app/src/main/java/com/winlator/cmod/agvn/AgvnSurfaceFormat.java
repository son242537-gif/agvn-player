/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

/**
 * The surface format ("Định dạng bề mặt") of a game that has none of its own, from what the game is.
 *
 * <p>An RPG Maker XP/VX/VX Ace game run in Wine ("Chạy bằng Windows": its Win32API scripts are too many for mkxp-z)
 * showed red and blue swapped with the container's default bgra8: the yellow AGVN splash came out blue, skin blue,
 * a blue coat yellow (Kim Dung Quần Hiệp Truyện 5, Xiaomi 2510DPC44G, 0.1.102, 25/11/2026). It showed right only once
 * the player found Cấu hình → Hình ảnh → Định dạng bề mặt = RGBA, which no player knows to do. Such a game now
 * starts with rgba8 unless its profile ({@link AgvnProfileSettings}) or the player set one; every other game keeps the
 * container's.
 * Whether the swap comes from how RGSS draws or from the phone's GPU driver is not known yet: the game's maker said
 * rgba8 is right on the phone they test on, and the setting stays the player's to change back. Pure Java
 * (JVM-testable).
 */
public final class AgvnSurfaceFormat {
    public static final String RGBA = "rgba8", BGRA = "bgra8";

    private AgvnSurfaceFormat() {}

    /**
     * The format for a game with no surfaceFormat of its own: {@link #RGBA} for an RPG Maker XP/VX/VX Ace game Wine
     * runs ({@code engine}: the shortcut's agvnEngine; {@code runner}: its agvnRunner, which is "rgss" when mkxp-z
     * runs it instead), else null for the container's.
     */
    public static String defaultFor(String engine, String runner) {
        boolean rgss = GameExeResolver.Engine.RPGMAKER.name().equals(engine);
        return rgss && !AgvnHtmlGame.RUNNER_RGSS.equals(runner) ? RGBA : null;
    }

    /** True for a value a profile may give: rgba8 or bgra8. */
    static boolean isValid(String value) {
        return RGBA.equals(value) || BGRA.equals(value);
    }
}
