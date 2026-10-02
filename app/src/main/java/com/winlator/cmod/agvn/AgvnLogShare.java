/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * "Gửi nhật ký": the game's recent session logs in one zip in Download, ready to send to AGVN. Also while the game
 * still runs (from its ⋮ menu or from the game's own menu): the running session as it is now, the app's logcat and
 * the "Chạy nhẹ" Ren'Py logs come along ({@link AgvnLiveLogs}), so the player need not quit the game first.
 */
public final class AgvnLogShare {
    private static final String TAG = "AGVN";

    private AgvnLogShare() {}

    public static void share(Activity activity, Shortcut shortcut) {
        String dir = shortcut.getExtra(AgvnGameImporter.EXTRA_GAME_DIR);
        share(activity, shortcut.name, dir.isEmpty() ? null : new File(dir));
    }

    /** {@code gameDir}: the imported game folder (for the "Chạy nhẹ" Ren'Py logs), or null. */
    public static void share(Activity activity, String gameName, File gameDir) {
        File[] sessions = new File(AgvnSessionLog.root(), AgvnLogFolders.safeName(gameName)).listFiles(File::isDirectory);
        Dialog working = dialog(activity, activity.getString(R.string.agvn_logs_working), null, false);
        working.setCancelable(false);
        working.show();
        new Thread(() -> {
            File zip = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "AGVN-nhat-ky-" + AgvnLogFolders.safeName(gameName).replace(' ', '-') + "-"
                            + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()) + ".zip");
            File live = new File(activity.getCacheDir(), "agvn-log-share");
            String error = null;
            int entries = 0;
            try {
                AgvnLiveLogs.delete(live);
                AgvnLiveLogs.collect(activity, sessions, gameDir, live);
                entries = zip(sessions, live, zip);
                if (entries == 0) zip.delete();
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "game logs not zipped", e);
                zip.delete();
                error = String.valueOf(e.getMessage());
            } finally {
                AgvnLiveLogs.delete(live);
            }
            String failed = error;
            boolean none = entries == 0;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (working.isShowing()) working.dismiss();
                if (failed != null) dialog(activity, activity.getString(R.string.agvn_logs_error, failed), null, true).show();
                else if (none) dialog(activity, activity.getString(R.string.agvn_logs_none), null, true).show();
                else dialog(activity, activity.getString(R.string.agvn_logs_saved, AgvnSaveDialogs.readable(zip)), zip, true).show();
            });
        }, "AgvnLogShare").start();
    }

    /**
     * Every session folder of the game as "&lt;session&gt;/&lt;file&gt;" entries, then the files under {@code live}
     * ({@link AgvnLiveLogs#collect}) by their path in it; a name already written is skipped. Returns the entry count.
     */
    static int zip(File[] sessions, File live, File zip) throws IOException {
        Set<String> names = new HashSet<>();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            if (sessions != null) {
                for (File session : sessions) {
                    File[] files = session.listFiles(File::isFile);
                    if (files != null) for (File f : files) add(out, names, session.getName() + "/" + f.getName(), f);
                }
            }
            if (live != null) addTree(out, names, live, "");
        }
        return names.size();
    }

    private static void addTree(ZipOutputStream out, Set<String> names, File dir, String prefix) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        java.util.Arrays.sort(files);
        for (File f : files) {
            if (f.isDirectory()) addTree(out, names, f, prefix + f.getName() + "/");
            else if (f.length() > 0) add(out, names, prefix + f.getName(), f);
        }
    }

    private static void add(ZipOutputStream out, Set<String> names, String name, File f) throws IOException {
        if (!names.add(name)) return;
        out.putNextEntry(new ZipEntry(name));
        Files.copy(f.toPath(), (OutputStream) out);
        out.closeEntry();
    }

    /**
     * A message, with OK when {@code ok}, and "Chia sẻ" when {@code zip} is given. The app's screens use AppCompat
     * dialogs; the "Chạy nhẹ" engines' screens are plain activities, which need the platform's.
     */
    private static Dialog dialog(Activity activity, String text, File zip, boolean ok) {
        if (activity instanceof AppCompatActivity) {
            AlertDialog.Builder b = new AlertDialog.Builder(activity).setMessage(text);
            if (ok) b.setNegativeButton(android.R.string.ok, null);
            if (zip != null) b.setPositiveButton(R.string.agvn_logs_share, (d, w) -> send(activity, zip));
            return b.create();
        }
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(activity,
                android.R.style.Theme_DeviceDefault_Dialog_Alert).setMessage(text);
        if (ok) b.setNegativeButton(android.R.string.ok, null);
        if (zip != null) b.setPositiveButton(R.string.agvn_logs_share, (d, w) -> send(activity, zip));
        return b.create();
    }

    private static void send(Activity activity, File zip) {
        try {
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".tileprovider", zip);
            Intent intent = new Intent(Intent.ACTION_SEND).setType("application/zip")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.agvn_logs_share)));
        } catch (RuntimeException e) {
            Log.w(TAG, "share sheet not opened", e);
        }
    }
}
