/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.widget.Toast;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Whether the game started with the fix "Tự sửa lỗi" is trying ({@link AgvnRepair}). The game's settings can change
 * after the fix is put on: the player picks another DirectX or driver in "Cấu hình", or a screen that read the game
 * before saves over it (AgvnShortcutReload). A start without the fix tells nothing about it, so the fix is neither
 * asked about nor counted as tried: Support Pregnancy School ran DXVK while "Dùng WineD3D" was asked about (Galaxy
 * M34, 09/10/2026). The fixes that set one switch are checked; the others count as on.
 */
final class AgvnRepairCheck {
    private AgvnRepairCheck() {}

    /** True when the game's settings {@code ran} have the fix on trial in {@code state}. */
    static boolean on(Shortcut s, Properties state, Map<String, String> ran) {
        return on(AgvnRepair.trying(state), state.getProperty(AgvnRepair.TO, ""), ran, s.container.getDXWrapper(),
                s.container.getDXWrapperConfig(), s.container.getGraphicsDriverConfig());
    }

    /**
     * True when settings {@code ran} have fix {@code id} set to {@code to} ("" when not known: a trial begun before
     * 0.1.34). A setting the game does not have of its own is its environment's ({@code env...}).
     */
    static boolean on(String id, String to, Map<String, String> ran, String envDx, String envDxConfig,
            String envDriver) {
        String dx = value(ran, "dxwrapper", envDx);
        switch (id) {
            case "wined3d":
                return dx.equals("wined3d");
            case "dxvk-back":
                return dx.contains("dxvk");
            case "dxvk-other":
            case "dxvk-arm64ec":
                return dx.contains("dxvk") && (to.isEmpty() || to.equals(
                        AgvnFixEdits.configValue(value(ran, "dxwrapperConfig", envDxConfig), "version", ',')));
            case "driver-other": {
                String driver = AgvnFixEdits.configValue(value(ran, "graphicsDriverConfig", envDriver), "version", ';');
                return to.isEmpty() || to.equals(driver.isEmpty() ? AgvnFixes.SYSTEM : driver);
            }
            default:
                return true;
        }
    }

    /** No fix on trial any more, and the one that was is not counted as tried; the report goes on. Its words. */
    static String letGo(Properties state) {
        String label = AgvnRepair.label(state);
        Set<String> tried = AgvnRepair.tried(state);
        tried.remove(AgvnRepair.trying(state));
        state.setProperty(AgvnRepair.TRIED, String.join(",", tried));
        for (String key : new String[]{AgvnRepair.TRYING, AgvnRepair.LABEL, AgvnRepair.AT, AgvnRepair.TO}) {
            state.remove(key);
        }
        return label;
    }

    /** The game ran without the fix on trial: it is let go ({@link #letGo}), in su-kien.txt too. */
    static void untry(Activity a, Shortcut s, Properties state) {
        String label = letGo(state);
        AgvnGoodConfig.save(a, s, state);
        AgvnSessionLog.event("Tự sửa lỗi: game chạy không có " + label
                + " (cài đặt của game đã đổi sau khi bật cách này), chưa tính là đã thử");
    }

    /** At the library: the fix on trial is not on the game now, so it is let go, with a word why. */
    static boolean offAtLibrary(Activity a, Shortcut s, Properties state) {
        if (on(s, state, AgvnRepair.snapshot(s))) return false;
        String label = AgvnRepair.label(state);
        untry(a, s, state);
        Toast.makeText(a, a.getString(R.string.agvn_repair_off_detail, label), Toast.LENGTH_LONG).show();
        return true;
    }

    private static String value(Map<String, String> ran, String key, String env) {
        String v = ran.get(key);
        return v != null ? v : env != null ? env : "";
    }
}
