/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;

import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.inputcontrols.ControlsProfile;

import java.io.File;
import java.util.Map;

/**
 * The virtual key profile the player picked for a "Chạy nhẹ" game in its settings ("Cấu hình phím ảo",
 * controlsProfile): the game's keys start from it instead of AGVN's keys of its type. Before, a Chạy nhẹ game kept
 * its type's keys whatever was picked, so keys made in "Điều khiển" never reached it (07/10/2026). "Tắt" ("0")
 * starts the game with its keys hidden. Pure Java except {@link #of}.
 */
final class AgvnLightPick {
    static final int OFF = -1, NONE = 0;
    /** The picked profile's id; {@link #OFF} for "Tắt"; {@link #NONE} for AGVN's keys (none picked, or unreadable). */
    final int id;
    /** The picked profile's .icp; null unless {@link #id} is a profile's. */
    final String json;
    /** When the picked profile was last saved (ms); 0 for none or AGVN's layout as installed (updates rewrite it). */
    final long editedMs;

    AgvnLightPick(int id, String json) {
        this(id, json, 0);
    }

    AgvnLightPick(int id, String json, long editedMs) {
        this.id = id;
        this.json = json;
        this.editedMs = editedMs;
    }

    /** The pick of the game {@code activity} runs, from its shortcut (shortcut_path). */
    static AgvnLightPick of(Activity activity) {
        String path = activity.getIntent() != null ? activity.getIntent().getStringExtra("shortcut_path") : null;
        if (path == null || path.isEmpty()) return new AgvnLightPick(NONE, null);
        Map<String, String> extras = AgvnHtmlGame.readExtras(new File(path));
        int id = picked(extras.get(AgvnLayouts.EXTRA_PROFILE), extras.get(AgvnLayouts.EXTRA_AUTO));
        if (id <= 0) return new AgvnLightPick(id, null);
        File file = ControlsProfile.getProfileFile(activity, id);
        String json = file.isFile() ? FileUtils.readString(file) : null;
        if (json == null || json.trim().isEmpty()) return new AgvnLightPick(NONE, null);
        return new AgvnLightPick(id, json, json.contains("\"" + AgvnControls.VERSION_KEY + "\"") ? 0 : file.lastModified());
    }

    /**
     * Whether the game's own key set, kept at {@code ownAtMs} and made from the picked profile, is still the newest: not
     * when the player saved the profile in "Điều khiển" after it, wanting those keys in the game (09/10/2026).
     */
    static boolean ownIsNewest(long ownAtMs, long profileEditedMs) {
        return profileEditedMs <= 0 || ownAtMs >= profileEditedMs;
    }

    /**
     * What the game's extras say: the id of the profile the player picked; {@link #OFF} for "0"; {@link #NONE} when
     * nothing is picked, or the layout is one AGVN picked (agvnControlsAuto = "1", or the old 900).
     */
    static int picked(String profile, String auto) {
        String id = profile != null ? profile.trim() : "";
        if (id.equals("0")) return OFF;
        if (id.isEmpty() || "1".equals(auto) || id.equals(String.valueOf(AgvnLayouts.LEGACY_ID))) return NONE;
        try {
            int value = Integer.parseInt(id);
            return value > 0 ? value : NONE;
        } catch (NumberFormatException e) {
            return NONE;
        }
    }
}
