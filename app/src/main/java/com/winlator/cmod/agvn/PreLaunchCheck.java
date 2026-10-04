/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.xenvironment.ImageFs;

import java.io.File;

/**
 * Runs right before a library game starts: refreshes the Unreal Engine.ini overrides, asks about battery saver
 * ({@link AgvnPowerSave}), warns about game files that were copied only partly ({@link AgvnGameFilesCheck}) and
 * checks that enough RAM is free. When RAM is short the
 * player is asked to close other apps and re-check, or to play anyway.
 * AGVN never kills other apps itself (Android 14+ only lets an app kill its own processes anyway).
 */
public final class PreLaunchCheck {
    private static final String TAG = "AGVN";

    private PreLaunchCheck() {}

    public static void run(Activity activity, Shortcut shortcut, Runnable launch) {
        applyUeConfig(activity, shortcut);
        int pool = parseInt(shortcut.getExtra(AgvnGameImporter.EXTRA_TEXTURE_POOL, "0"));
        long requiredMb = RamGuard.getRequiredRamMb(pool);
        AgvnPowerSave.ask(activity, () -> AgvnGameFilesCheck.run(activity, shortcut, () -> check(activity, requiredMb, launch)));
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

    /**
     * Writes the profile's Engine.ini overrides and texture pool for imported Unreal games; safe to repeat. The pool is
     * smaller where the game's Vulkan driver unpacks BCn textures ({@link AgvnBcn#texturePool}).
     */
    public static void applyUeConfig(Context ctx, Shortcut shortcut) {
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
            int pool = AgvnBcn.texturePool(parseInt(shortcut.getExtra(AgvnGameImporter.EXTRA_TEXTURE_POOL, "0")),
                    unpacksBcn(ctx, shortcut));
            File wineUser = new File(shortcut.container.getRootDir(), ".wine/drive_c/users/" + ImageFs.USER);
            for (File ini : UeIniWriter.apply(wineUser, UeIniWriter.projectName(gameDir, relative), UeIniWriter.overrides(profile, pool)))
                Log.i(TAG, "Engine.ini updated (texture pool " + pool + " MB): " + ini);
        } catch (Exception e) {
            Log.w(TAG, "Engine.ini update failed", e);
        }
    }

    /** True when the game's Vulkan driver, as it will really start, unpacks BCn textures ({@link AgvnBcn#unpacked}). */
    static boolean unpacksBcn(Context ctx, Shortcut s) {
        String config = AgvnFixes.driverConfig(s);
        String chosen = AgvnFixEdits.configValue(config, "version", ';');
        String driver = DriverSafety.resolveUsable(ctx, chosen.isEmpty() ? AgvnFixes.SYSTEM : chosen);
        return AgvnBcn.unpacked(AgvnFixEdits.configValue(config, "bcnEmulation", ';'), driver);
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
