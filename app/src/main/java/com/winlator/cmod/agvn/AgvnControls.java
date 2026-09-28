/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.inputcontrols.ControlsProfile;

import org.json.JSONObject;

import java.io.File;

/**
 * "AGVN Bàn phím": on-screen keys (Esc, Tab, Shift, Ctrl, Alt, arrows, F1, Ins, Enter, Space, Backspace) that every
 * game shows unless its shortcut picked another controls profile. Installed from assets/agvn/controls-agvn.icp as
 * profile id 900; refreshed when the bundled layout version grows, unless the player edited it in the controls editor.
 */
public final class AgvnControls {
    public static final int PROFILE_ID = 900;
    private static final String ASSET = "agvn/controls-agvn.icp";
    private static final String VERSION_KEY = "agvnLayoutVersion";

    private AgvnControls() {}

    /** Writes the AGVN profile into the controls folder when missing or outdated. Safe to call often. */
    public static void ensureProfile(Context ctx) {
        try {
            String asset = FileUtils.readString(ctx, ASSET);
            if (asset == null || asset.isEmpty()) return;
            File target = ControlsProfile.getProfileFile(ctx, PROFILE_ID);
            if (target.isFile()) {
                JSONObject current = new JSONObject(FileUtils.readString(target));
                if (!current.has(VERSION_KEY)) return; // edited by the player: keep it
                if (current.optInt(VERSION_KEY) >= new JSONObject(asset).optInt(VERSION_KEY)) return;
            }
            FileUtils.writeString(target, asset);
        } catch (Exception e) {
            Log.w("AGVN", "cannot install AGVN controls profile", e);
        }
    }

    /** Uses the AGVN keys for this launch when the shortcut never chose a controls profile (not saved to disk). */
    public static void applyDefault(Context ctx, Shortcut shortcut) {
        if (shortcut == null) return;
        ensureProfile(ctx);
        if (shortcut.getExtra("controlsProfile", null) == null && ControlsProfile.getProfileFile(ctx, PROFILE_ID).isFile())
            shortcut.putExtra("controlsProfile", String.valueOf(PROFILE_ID));
    }
}
