/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
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
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** "Gửi nhật ký" in a game's ⋮ menu: the game's recent session logs in one zip in Download, ready to send to AGVN. */
public final class AgvnLogShare {
    private static final String TAG = "AGVN";

    private AgvnLogShare() {}

    public static void share(Activity activity, Shortcut shortcut) {
        File gameLogs = new File(AgvnSessionLog.root(), AgvnLogFolders.safeName(shortcut.name));
        File[] sessions = gameLogs.listFiles(File::isDirectory);
        if (sessions == null || sessions.length == 0) {
            AgvnUpdateDialogs.message(activity, activity.getString(R.string.agvn_logs_none));
            return;
        }
        AlertDialog working = new AlertDialog.Builder(activity).setMessage(R.string.agvn_logs_working).setCancelable(false).show();
        new Thread(() -> {
            File zip = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "AGVN-nhat-ky-" + AgvnLogFolders.safeName(shortcut.name).replace(' ', '-') + "-"
                            + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()) + ".zip");
            String error = null;
            try {
                zip(sessions, zip);
            } catch (IOException e) {
                Log.w(TAG, "game logs not zipped", e);
                zip.delete();
                error = String.valueOf(e.getMessage());
            }
            String failed = error;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (working.isShowing()) working.dismiss();
                if (failed != null) {
                    AgvnUpdateDialogs.message(activity, activity.getString(R.string.agvn_logs_error, failed));
                    return;
                }
                new AlertDialog.Builder(activity)
                        .setMessage(activity.getString(R.string.agvn_logs_saved, AgvnSaveDialogs.readable(zip)))
                        .setPositiveButton(R.string.agvn_logs_share, (d, w) -> send(activity, zip))
                        .setNegativeButton(android.R.string.ok, null)
                        .show();
            });
        }, "AgvnLogShare").start();
    }

    /** Every session folder of the game, as "<session>/<file>" entries. */
    static void zip(File[] sessions, File zip) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            for (File session : sessions) {
                File[] files = session.listFiles(File::isFile);
                if (files == null) continue;
                for (File f : files) {
                    out.putNextEntry(new ZipEntry(session.getName() + "/" + f.getName()));
                    Files.copy(f.toPath(), (OutputStream) out);
                    out.closeEntry();
                }
            }
        }
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
