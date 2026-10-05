/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.GameSaveManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * "Nhập save" / "Xuất save" in the library's ⋮ menu. Zips go to AGVN-Player/Saves/&lt;game&gt;/, the folder of the
 * existing "Bản lưu game" backups. Import first saves the current saves there, so a wrong file can be undone.
 */
public final class AgvnSaveDialogs {
    private static final String TAG = "AGVN";

    private AgvnSaveDialogs() {}

    public static void exportSaves(Activity activity, Shortcut shortcut) {
        if (htmlRunner(activity, shortcut)) return;
        run(activity, () -> {
            File zip = newZip(shortcut, "save");
            int count = AgvnSaveTransfer.export(AgvnSaveLocations.find(shortcut), prefs(shortcut), shortcut.name, zip);
            if (count == 0) {
                zip.delete();
                return activity.getString(R.string.agvn_save_export_empty);
            }
            return activity.getString(R.string.agvn_save_exported, count, readable(zip));
        });
    }

    /** Says where the saves will go, then lets {@code pickFiles} open the file picker. */
    public static void askImport(Activity activity, Shortcut shortcut, Runnable pickFiles) {
        if (htmlRunner(activity, shortcut)) return;
        List<AgvnSaveLocations.Location> places = AgvnSaveLocations.find(shortcut);
        if (places.isEmpty()) {
            message(activity, activity.getString(R.string.agvn_save_import_unknown));
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.agvn_save_import)
                .setMessage(activity.getString(R.string.agvn_save_import_where, readable(places.get(0).dir)))
                .setPositiveButton(R.string.agvn_save_pick, (d, w) -> pickFiles.run())
                .setNegativeButton(R.string.agvn_cancel, null)
                .show();
    }

    public static void importPicked(Activity activity, Shortcut shortcut, List<Uri> uris) {
        List<AgvnSaveTransfer.Source> sources = new ArrayList<>();
        for (Uri uri : uris) sources.add(source(activity, uri));
        run(activity, () -> {
            List<AgvnSaveLocations.Location> places = AgvnSaveLocations.find(shortcut);
            if (places.isEmpty()) return activity.getString(R.string.agvn_save_import_unknown);
            File backup = newZip(shortcut, "truoc-khi-nhap");
            if (AgvnSaveTransfer.export(places, prefs(shortcut), shortcut.name, backup) == 0) backup.delete();

            int count;
            if (sources.size() == 1 && isAgvnZip(sources.get(0))) {
                List<String> prefs = new ArrayList<>();
                try (InputStream in = sources.get(0).open()) {
                    count = AgvnSaveTransfer.restore(in, places, prefs);
                }
                String key = prefsKey(shortcut);
                if (key != null) count += AgvnSavePrefs.write(userReg(shortcut), key, prefs);
            } else {
                count = AgvnSaveTransfer.copyInto(sources, places.get(0));
            }
            if (count == 0) return activity.getString(R.string.agvn_save_import_none);
            String text = activity.getString(R.string.agvn_save_imported, count, readable(places.get(0).dir));
            return backup.isFile() ? text + activity.getString(R.string.agvn_save_imported_backup, readable(backup)) : text;
        });
    }

    private interface Job {
        String run() throws IOException;
    }

    private static void run(Activity activity, Job job) {
        AlertDialog working = new AlertDialog.Builder(activity).setMessage(R.string.agvn_save_working).setCancelable(false).show();
        new Thread(() -> {
            String text;
            try {
                text = job.run();
            } catch (Exception e) {
                Log.w(TAG, "save transfer failed", e);
                text = activity.getString(R.string.agvn_save_error, String.valueOf(e.getMessage()));
            }
            String result = text;
            activity.runOnUiThread(() -> {
                working.dismiss();
                message(activity, result);
            });
        }, "AgvnSaves").start();
    }

    private static void message(Activity activity, String text) {
        new AlertDialog.Builder(activity).setMessage(text).setPositiveButton(android.R.string.ok, null).show();
    }

    private static boolean htmlRunner(Activity activity, Shortcut shortcut) {
        if (!AgvnHtmlGame.RUNNER_HTML.equals(shortcut.getExtra(AgvnHtmlGame.EXTRA_RUNNER))) return false;
        message(activity, activity.getString(R.string.agvn_save_html_note));
        return true;
    }

    private static File newZip(Shortcut shortcut, String kind) {
        File dir = GameSaveManager.getGameDir(shortcut);
        dir.mkdirs();
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        return new File(dir, dir.getName() + "_" + kind + "_" + stamp + ".zip");
    }

    private static String prefsKey(Shortcut shortcut) {
        if (!GameExeResolver.Engine.UNITY.name().equals(shortcut.getExtra(AgvnGameImporter.EXTRA_ENGINE))) return null;
        String[] names = AgvnSaveLocations.unityNames(new File(shortcut.path.replace("\"", "")));
        return names != null ? "Software\\" + names[0] + "\\" + names[1] : null;
    }

    private static List<String> prefs(Shortcut shortcut) throws IOException {
        String key = prefsKey(shortcut);
        return key != null ? AgvnSavePrefs.read(userReg(shortcut), key) : new ArrayList<>();
    }

    private static File userReg(Shortcut shortcut) {
        return new File(shortcut.container.getRootDir(), ".wine/user.reg");
    }

    private static boolean isAgvnZip(AgvnSaveTransfer.Source source) throws IOException {
        if (!source.name().toLowerCase(Locale.ROOT).endsWith(".zip")) return false;
        try (InputStream in = source.open()) {
            return AgvnSaveTransfer.isAgvnZip(in);
        }
    }

    private static AgvnSaveTransfer.Source source(Activity activity, Uri uri) {
        String name = uri.getLastPathSegment();
        try (Cursor c = activity.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && c.getString(0) != null) name = c.getString(0);
        } catch (Exception ignored) {}
        String display = name != null ? name.substring(name.lastIndexOf('/') + 1) : "save";
        return new AgvnSaveTransfer.Source() {
            @Override public String name() { return display; }
            @Override public InputStream open() throws IOException {
                InputStream in = activity.getContentResolver().openInputStream(uri);
                if (in == null) throw new IOException("cannot open " + display);
                return in;
            }
        };
    }

    /** A path as the player knows it: relative to the phone storage, or on the container's Windows C: drive. */
    static String readable(File file) {
        String path = file.getPath(), storage = Environment.getExternalStorageDirectory().getPath() + "/";
        if (path.startsWith(storage)) return path.substring(storage.length());
        int users = path.indexOf("/.wine/drive_c/");
        return users >= 0 ? "C:\\" + path.substring(users + "/.wine/drive_c/".length()).replace('/', '\\') : path;
    }
}
