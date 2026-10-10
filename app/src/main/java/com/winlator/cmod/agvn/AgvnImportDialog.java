/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Thêm game": asks for storage access when needed, then opens the full-screen game list ({@link AgvnAddGameHost}).
 * Tapping a game previews the settings it will get and imports it (again, for a game already in the library).
 */
public final class AgvnImportDialog {
    /** One import at a time, in tap order. */
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static AlertDialog preparing;

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

    /**
     * Settings preview for one game ({@code variant}: its exe when the folder holds several games or it was picked by
     * hand, else null); {@code onImported} runs after a successful import.
     */
    static void preview(MainActivity activity, File gameDir, String variant, DeviceTier tier, AgvnLibraryIndex.Existing existing,
                        Runnable onImported) {
        AgvnGameImporter.Candidate candidate;
        try {
            candidate = AgvnGameImporter.load(gameDir, AgvnProfileCatalog.get(activity), variant);
        } catch (AgvnProfileException e) {
            showError(activity, e.getMessage());
            return;
        }
        AgvnProfile p = candidate.profile;
        LaunchPresetResolver.Effective eff = LaunchPresetResolver.resolve(p, tier, DeviceTierManager.getRules(activity).preset(tier));
        StringBuilder msg = new StringBuilder();
        msg.append(activity.getString(R.string.agvn_import_preview_tier, tier.label)).append('\n');
        boolean hasExe = new File(gameDir, candidate.exe).isFile();
        msg.append(hasExe ? activity.getString(R.string.agvn_import_preview_exe, candidate.exe)
                : activity.getString(R.string.agvn_import_preview_no_exe)).append('\n');
        msg.append(activity.getString(R.string.agvn_import_preview_fps, eff.fps > 0 ? String.valueOf(eff.fps) : activity.getString(R.string.agvn_unlimited))).append('\n');
        String screen = AgvnKirikiriScreen.larger(eff.resolution, candidate.gameSize); // as the import writes it
        msg.append(screen != null && !screen.equals(eff.resolution) ? activity.getString(R.string.agvn_import_preview_game_size, screen)
                : activity.getString(R.string.agvn_import_preview_resolution, screen != null ? screen : activity.getString(R.string.agvn_default_value))).append('\n');
        msg.append(activity.getString(R.string.agvn_import_preview_controls, activity.getString(AgvnLayouts.labelRes(AgvnLayouts.kindFor(p, candidate.engine))))).append('\n');
        if (AgvnHtmlGame.useHtml(p, AgvnHtmlGame.indexFor(gameDir, candidate.engine)) || AgvnRenpyGame.useRenpy(p, candidate.engine, gameDir)
                || AgvnRgssGame.useRgss(p, candidate.engine, gameDir) || AgvnGodotLight.useGodot(p, candidate.engine, new File(gameDir, candidate.exe)))
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
                                chosen -> preview(activity, gameDir, variant, chosen != null ? chosen : DeviceTierManager.detect(activity), existing, onImported)))
                .setNegativeButton(R.string.agvn_cancel, null)
                .show();
    }

    /**
     * Imports into the container the game already lives in (same name, so the shortcut is updated), else Proton 10's
     * ({@link AgvnWine10#forImport}: made once, about a minute, said in a dialog), or Proton 9's for a .NET game.
     */
    private static void doImport(MainActivity activity, AgvnGameImporter.Candidate candidate, DeviceTier tier,
                                 AgvnLibraryIndex.Existing existing, Runnable onImported) {
        if (new ContainerManager(activity).getContainers().isEmpty()) {
            showError(activity, activity.getString(R.string.agvn_import_no_container));
            return;
        }
        if (existing != null) candidate.profile.name = existing.name;
        Container known = existing != null ? existing.container : null;
        File exe = new File(candidate.gameDir, candidate.exe);
        // setting Proton 10 up, writing the shortcut and reading the exe icon touch storage: off the main thread
        IO.execute(() -> {
            Exception error = null;
            String olderName = null;
            try {
                // AGVN: another version of a game in the library joins its container, where its saves are
                com.winlator.cmod.container.Shortcut older = known != null ? null : AgvnGameVersions.older(
                        new ContainerManager(activity).loadShortcuts(), candidate.profile.name, exe);
                Container container = known != null ? known : older != null ? older.container
                        : AgvnWine10.forImport(activity, exe, percent -> activity.runOnUiThread(() -> preparing(activity, percent)));
                if (container == null) throw new IllegalStateException(activity.getString(R.string.agvn_import_no_container));
                File made = AgvnGameImporter.importGame(activity, container, candidate, tier);
                if (older != null) {
                    AgvnGameVersions.copyFolderSaves(older, new com.winlator.cmod.container.Shortcut(container, made));
                    olderName = older.name;
                }
            } catch (Exception e) {
                error = e;
            }
            Exception failure = error;
            String sharedWith = olderName;
            activity.runOnUiThread(() -> {
                preparing(activity, -1);
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (failure != null) {
                    showError(activity, activity.getString(R.string.agvn_import_failed, String.valueOf(failure.getMessage())));
                    return;
                }
                String done = sharedWith != null
                        ? activity.getString(R.string.agvn_import_new_version, candidate.profile.name, sharedWith)
                        : activity.getString(R.string.agvn_import_done, candidate.profile.name);
                Toast.makeText(activity, done, Toast.LENGTH_LONG).show();
                if (onImported != null) onImported.run();
                activity.navigateToMainDestination(R.id.main_menu_shortcuts);
            });
        });
    }

    /** "Đang chuẩn bị Wine mới…" while Proton 10 is set up for the first game (UI thread); -1 closes it. */
    private static void preparing(MainActivity activity, int percent) {
        try {
            if (percent < 0 || activity.isFinishing() || activity.isDestroyed()) {
                if (preparing != null) preparing.dismiss();
                preparing = null;
                return;
            }
            String text = activity.getString(R.string.agvn_import_wine10, percent);
            if (preparing == null) preparing = new AlertDialog.Builder(activity).setTitle(R.string.agvn_import_title)
                    .setMessage(text).setCancelable(false).show();
            else preparing.setMessage(text);
        } catch (RuntimeException e) {
            preparing = null; // the window went away: the import goes on
        }
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
