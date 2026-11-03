/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.container.Shortcut;

/**
 * "Đồng bộ khung hình" and "Tắt Present Wait", a game's own graphics driver settings: syncFrame (the wrapper's X11
 * present waits for each frame, MESA_VK_WSI_DEBUG=forcesync) and disablePresentWait (the wrapper hides
 * VK_KHR_present_wait, which DXVK 2.x waits on; 1.10.3 never uses it). A Mali-G615 player's Support Pregnancy School
 * (Unreal) stayed black on the defaults and started with both on. The black-screen bar ({@link AgvnBlackScreen}) offers
 * them to a DirectX game that stays black, on any phone, and the game keeps them as its own settings. 0.1.18 turned
 * them on for every DirectX game on a phone short of RAM without Turnip: games that show anyway paid for another
 * game's fix. "Tắt Present Wait" alone ({@link #noWaitOfferable}) is the first try for a game that froze right after
 * DXVK made its swap chain again ({@link AgvnSwapchainStuck}): it changes less than "Đồng bộ khung hình", which makes
 * each present wait. Pure Java except {@link #turnOn}.
 */
final class AgvnPresentSync {
    static final String SYNC = "syncFrame", NO_WAIT = "disablePresentWait", FIX_NO_WAIT = "present-wait-off";

    private AgvnPresentSync() {}

    /** True when the bar can offer them: a DirectX (DXVK) game that does not have both on yet. */
    static boolean offerable(Shortcut s) {
        return s != null && AgvnFixes.dxvk(s) && !on(AgvnFixes.driverConfig(s));
    }

    /** True when "Tắt Present Wait" alone can be offered: a game on DXVK 2.x (1.x never waits) that has it off. */
    static boolean noWaitOfferable(Shortcut s) {
        return s != null && AgvnFixes.dxvk(s) && !AgvnFixes.dxvkVersion(s).startsWith("1.")
                && !"1".equals(AgvnFixEdits.configValue(AgvnFixes.driverConfig(s), NO_WAIT, ';'));
    }

    static String withNoWait(String driverConfig) {
        return AgvnFixEdits.withConfigValue(driverConfig, NO_WAIT, "1", ';');
    }

    /** True when the graphics driver config ({@link AgvnFixes#driverConfig}) has both on. */
    static boolean on(String driverConfig) {
        return "1".equals(AgvnFixEdits.configValue(driverConfig, SYNC, ';'))
                && "1".equals(AgvnFixEdits.configValue(driverConfig, NO_WAIT, ';'));
    }

    static String withBoth(String driverConfig) {
        String synced = AgvnFixEdits.withConfigValue(driverConfig, SYNC, "1", ';');
        return AgvnFixEdits.withConfigValue(synced, NO_WAIT, "1", ';');
    }

    /** The game starts with both from its next start; the player can turn them off in its settings. */
    static void turnOn(Shortcut s) {
        s.putExtra("graphicsDriverConfig", withBoth(AgvnFixes.driverConfig(s)));
        s.saveData();
        AgvnSessionLog.event("Người chơi bật \"Đồng bộ khung hình\" và \"Tắt Present Wait\" cho game này");
    }
}
