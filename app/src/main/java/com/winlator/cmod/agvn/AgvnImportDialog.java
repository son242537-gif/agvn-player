/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.MainActivity;
import com.winlator.cmod.R;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Thêm game": asks for storage access when needed, then opens the full-screen game list ({@link AgvnAddGameHost}).
 * Tapping a game previews the settings it will get and imports it (again, for a game already in the library).
 */
public final class AgvnImportDialog {
    /** One import at a time, in tap order. */
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private AgvnImportDialog() {}

    public static void show(MainActivity activity) {
        if (AgvnGameImporter.storageBlocked()) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.agvn_import_title)
                    .setMessage(R.string.agvn_import_no_permission)
                    .setPositiveButton(R.string.agvn_import_grant, (d, w) -> openStoragePermission(activity))
                    .setNegativeButton(R.string.agvn_close, null)
                    .show();
            return;
        }
        AgvnAddGameHost.show(activity);
    }

    /** Settings preview for one game; {@code onImported} runs after a successful import. */
    static void preview(MainActivity activity, File gameDir, DeviceTier tier, AgvnLibraryIndex.Existing existing, Runnable onImported) {
        AgvnGameImporter.Candidate candidate;
        try {
            candidate = AgvnGameImporter.load(gameDir, AgvnProfileCatalog.get(activity));
        } catch (AgvnProfileException e) {
            showError(activity, e.getMessage());
            return;
        }
        AgvnProfile p = candidate.profile;
        LaunchPresetResolver.Effective eff = LaunchPresetResolver.resolve(p, tier, DeviceTierManager.getRules(activity).preset(tier));
        StringBuilder msg = new StringBuilder();
        msg.append(activity.getString(R.string.agvn_import_preview_tier, tier.label)).append('\n');
        msg.append(activity.getString(R.string.agvn_import_preview_exe, candidate.exe)).append('\n');
        msg.append(activity.getString(R.string.agvn_import_preview_fps, eff.fps > 0 ? String.valueOf(eff.fps) : activity.getString(R.string.agvn_unlimited))).append('\n');
        msg.append(activity.getString(R.string.agvn_import_preview_resolution, eff.resolution != null ? eff.resolution : activity.getString(R.string.agvn_default_value))).append('\n');
        msg.append(activity.getString(R.string.agvn_import_preview_controls, activity.getString(AgvnLayouts.labelRes(AgvnLayouts.kindFor(p, candidate.engine))))).append('\n');
        if (AgvnHtmlGame.useHtml(p, AgvnHtmlGame.indexFor(gameDir, candidate.engine)))
            msg.append(activity.getString(R.string.agvn_import_preview_html)).append('\n');
        if (AgvnLocale.JAPANESE.equals(AgvnLocale.forGame(p, candidate.engine, gameDir.getName(), candidate.exe)))
            msg.append(activity.getString(R.string.agvn_import_preview_japanese)).append('\n');
        if (eff.texturePool > 0 && candidate.engine == GameExeResolver.Engine.UNREAL) {
            msg.append(activity.getString(R.string.agvn_import_preview_pool, eff.texturePool)).append('\n');
            if (eff.texturePool > availableRamMb(activity))
                msg.append('\n').append(activity.getString(R.string.agvn_import_pool_warning)).append('\n');
        }
        new AlertDialog.Builder(activity)
                .setTitle(p.name)
                .setMessage(msg.toString().trim())
                .setPositiveButton(existing != null ? R.string.agvn_import_update : R.string.agvn_import_confirm,
                        (d, w) -> doImport(activity, candidate, tier, existing, onImported))
                .setNeutralButton(activity.getString(R.string.agvn_import_change_tier, tier.label), (d, w) ->
                        AgvnTierDialog.choose(activity, activity.getString(R.string.agvn_tier_for_game), tier.ordinal() + 1,
                                chosen -> preview(activity, gameDir, chosen != null ? chosen : DeviceTierManager.detect(activity), existing, onImported)))
                .setNegativeButton(R.string.agvn_cancel, null)
                .show();
    }

    /** Imports into the container the game already lives in (same name, so the shortcut is updated), else the first one. */
    private static void doImport(MainActivity activity, AgvnGameImporter.Candidate candidate, DeviceTier tier,
                                 AgvnLibraryIndex.Existing existing, Runnable onImported) {
        Container target = existing != null ? existing.container : null;
        if (target == null) {
            List<Container> containers = new ContainerManager(activity).getContainers();
            if (containers.isEmpty()) {
                showError(activity, activity.getString(R.string.agvn_import_no_container));
                return;
            }
            target = containers.get(0);
        }
        if (existing != null) candidate.profile.name = existing.name;
        Container container = target;
        // writing the shortcut and reading the exe icon touch storage: keep them off the main thread
        IO.execute(() -> {
            Exception error = null;
            try {
                AgvnGameImporter.importGame(activity, container, candidate, tier);
            } catch (Exception e) {
                error = e;
            }
            Exception failure = error;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (failure != null) {
                    showError(activity, activity.getString(R.string.agvn_import_failed, String.valueOf(failure.getMessage())));
                    return;
                }
                Toast.makeText(activity, activity.getString(R.string.agvn_import_done, candidate.profile.name), Toast.LENGTH_LONG).show();
                if (onImported != null) onImported.run();
                activity.navigateToMainDestination(R.id.main_menu_shortcuts);
            });
        });
    }

    private static void openStoragePermission(MainActivity activity) {
        try {
            Intent intent = android.os.Build.VERSION.SDK_INT >= 30
                    ? new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + activity.getPackageName()))
                    : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception e) {
            activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.getPackageName())));
        }
    }

    private static void showError(Context ctx, String message) {
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.agvn_import_error_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private static long availableRamMb(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem >> 20;
    }
}
