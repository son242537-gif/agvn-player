/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.MainActivity;
import com.winlator.cmod.R;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * "Thêm game AGVN": scans storage for game folders (with or without agvn-profile.json; ✦ = configured automatically),
 * previews the resulting settings and imports the chosen one into the library.
 */
public final class AgvnImportDialog {
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
        AlertDialog progress = new AlertDialog.Builder(activity)
                .setMessage(R.string.agvn_import_scanning).setCancelable(false).show();
        Handler main = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            List<File> dirs = AgvnGameImporter.listGameDirs();
            main.post(() -> {
                progress.dismiss();
                if (!activity.isFinishing()) showList(activity, dirs);
            });
        });
    }

    private static void showList(MainActivity activity, List<File> dirs) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity).setTitle(R.string.agvn_import_title);
        if (dirs.isEmpty()) {
            builder.setMessage(R.string.agvn_import_empty);
        } else {
            String root = android.os.Environment.getExternalStorageDirectory().getPath();
            String[] names = new String[dirs.size()];
            for (int i = 0; i < names.length; i++) {
                File dir = dirs.get(i);
                String where = dir.getParent() != null ? dir.getParent().replace(root, activity.getString(R.string.agvn_internal_storage)) : "";
                boolean auto = !new File(dir, AgvnProfile.FILE_NAME).isFile();
                names[i] = dir.getName() + (auto ? " ✦" : "") + "\n   " + where;
            }
            builder.setItems(names, (d, which) -> preview(activity, dirs.get(which), DeviceTierManager.current(activity)));
        }
        builder.setNeutralButton(R.string.agvn_import_manual, (d, w) -> activity.navigateToMainDestination(R.id.main_menu_file_manager))
                .setNegativeButton(R.string.agvn_close, null)
                .show();
    }

    private static void preview(MainActivity activity, File gameDir, DeviceTier tier) {
        AgvnGameImporter.Candidate candidate;
        try {
            candidate = AgvnGameImporter.load(gameDir);
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
        if (eff.texturePool > 0 && candidate.engine == GameExeResolver.Engine.UNREAL) {
            msg.append(activity.getString(R.string.agvn_import_preview_pool, eff.texturePool)).append('\n');
            if (eff.texturePool > availableRamMb(activity))
                msg.append('\n').append(activity.getString(R.string.agvn_import_pool_warning)).append('\n');
        }
        new AlertDialog.Builder(activity)
                .setTitle(p.name)
                .setMessage(msg.toString().trim())
                .setPositiveButton(R.string.agvn_import_confirm, (d, w) -> doImport(activity, candidate, tier))
                .setNeutralButton(activity.getString(R.string.agvn_import_change_tier, tier.label), (d, w) ->
                        AgvnTierDialog.choose(activity, activity.getString(R.string.agvn_tier_for_game), tier.ordinal() + 1,
                                chosen -> preview(activity, gameDir, chosen != null ? chosen : DeviceTierManager.detect(activity))))
                .setNegativeButton(R.string.agvn_cancel, null)
                .show();
    }

    private static void doImport(MainActivity activity, AgvnGameImporter.Candidate candidate, DeviceTier tier) {
        List<Container> containers = new ContainerManager(activity).getContainers();
        if (containers.isEmpty()) {
            showError(activity, activity.getString(R.string.agvn_import_no_container));
            return;
        }
        try {
            AgvnGameImporter.importGame(activity, containers.get(0), candidate, tier);
        } catch (Exception e) {
            showError(activity, activity.getString(R.string.agvn_import_failed, String.valueOf(e.getMessage())));
            return;
        }
        Toast.makeText(activity, activity.getString(R.string.agvn_import_done, candidate.profile.name), Toast.LENGTH_LONG).show();
        activity.navigateToMainDestination(R.id.main_menu_shortcuts);
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
