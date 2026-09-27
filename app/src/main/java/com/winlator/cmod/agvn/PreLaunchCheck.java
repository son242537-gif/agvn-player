/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.xenvironment.ImageFs;

import java.io.File;

/**
 * Runs right before a library game starts: refreshes the Unreal Engine.ini overrides and checks that enough RAM
 * is free. When RAM is short the player is asked to close other apps and re-check, or to play anyway.
 * AGVN never kills other apps itself (Android 14+ only lets an app kill its own processes anyway).
 */
public final class PreLaunchCheck {
    private static final String TAG = "AGVN";

    private PreLaunchCheck() {}

    public static void run(Activity activity, Shortcut shortcut, Runnable launch) {
        applyUeConfig(shortcut);
        int pool = parseInt(shortcut.getExtra(AgvnGameImporter.EXTRA_TEXTURE_POOL, "0"));
        check(activity, RamGuard.getRequiredRamMb(pool), launch);
    }

    private static void check(Activity activity, long requiredMb, Runnable launch) {
        long available = RamGuard.getAvailableRamMb(activity);
        if (!RamGuard.needsCleanup(available, requiredMb)) {
            launch.run();
            return;
        }
        Log.w(TAG, "pre-launch: " + available + " MB free, " + requiredMb + " MB wanted");
        new AlertDialog.Builder(activity)
                .setTitle(R.string.agvn_prelaunch_title)
                .setMessage(activity.getString(R.string.agvn_prelaunch_message, requiredMb, available))
                .setPositiveButton(R.string.agvn_prelaunch_recheck, (d, w) -> check(activity, requiredMb, launch))
                .setNeutralButton(R.string.agvn_prelaunch_play_anyway, (d, w) -> launch.run())
                .setNegativeButton(R.string.agvn_cancel, null)
                .show();
    }

    /** Writes the profile's Engine.ini overrides and texture pool for imported Unreal games; safe to repeat. */
    public static void applyUeConfig(Shortcut shortcut) {
        try {
            if (!GameExeResolver.Engine.UNREAL.name().equals(shortcut.getExtra(AgvnGameImporter.EXTRA_ENGINE))) return;
            String profilePath = shortcut.getExtra(AgvnGameImporter.EXTRA_PROFILE_PATH);
            String gameDirPath = shortcut.getExtra(AgvnGameImporter.EXTRA_GAME_DIR);
            if (profilePath.isEmpty() || gameDirPath.isEmpty()) return;
            String json = FileUtils.readString(new File(profilePath));
            if (json == null) return;
            AgvnProfile profile = AgvnProfile.parse(json);
            File gameDir = new File(gameDirPath);
            String exe = shortcut.path.replace("\"", "");
            String relative = exe.startsWith(gameDir.getPath() + "/") ? exe.substring(gameDir.getPath().length() + 1) : exe;
            int pool = parseInt(shortcut.getExtra(AgvnGameImporter.EXTRA_TEXTURE_POOL, "0"));
            File wineUser = new File(shortcut.container.getRootDir(), ".wine/drive_c/users/" + ImageFs.USER);
            for (File ini : UeIniWriter.apply(wineUser, UeIniWriter.projectName(gameDir, relative), UeIniWriter.overrides(profile, pool)))
                Log.i(TAG, "Engine.ini updated: " + ini);
        } catch (Exception e) {
            Log.w(TAG, "Engine.ini update failed", e);
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
