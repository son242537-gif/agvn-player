/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.MainActivity;
import com.winlator.cmod.OnboardingActivity;
import com.winlator.cmod.R;
import com.winlator.cmod.xenvironment.ImageFs;
import com.winlator.cmod.xenvironment.ImageFsInstaller;

import java.io.File;

/**
 * "Mở bằng AGVN Player": the player taps a downloaded archive in a browser or a file manager (or shares it) and picks
 * the app. This screen shows nothing of its own: it finds the file ({@link AgvnOpenArchivePath}), hands it to the
 * library, which unpacks it as "Thêm từ tệp nén" does ({@link AgvnUnpackFlow#run}), and closes. It says why when it
 * cannot: the app is not set up yet, a game is open, or the other app gave no file the app can read in place (nothing
 * is copied; the player picks the file in "Thêm từ tệp nén"). Uses no network.
 */
public final class AgvnOpenArchiveActivity extends AppCompatActivity {
    private static final String TAG = "AGVN";
    /** An archive handed over waits this long at most for the library to come up. */
    private static final long PENDING_MS = 2 * 60 * 1000L;
    private static volatile File pending;
    private static volatile long pendingAt;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AgvnLanguage.wrap(base)); // the app's language
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) { // its dialog is gone with the old screen
            finish();
            return;
        }
        Uri uri = address(getIntent());
        if (uri == null) {
            finish();
        } else if (!ready(this)) {
            say(getString(R.string.agvn_open_not_ready), true);
        } else if (AgvnGameImporter.storageBlocked()) {
            say(getString(R.string.agvn_import_no_permission), true);
        } else if (busy()) {
            say(getString(R.string.agvn_open_busy), false);
        } else {
            new Thread(() -> find(uri), "AgvnOpenArchive").start();
        }
    }

    /** The file's address: the data of "open with", the stream of "share". */
    private static Uri address(Intent intent) {
        try {
            if (intent == null) return null;
            return Intent.ACTION_SEND.equals(intent.getAction()) ? intent.<Uri>getParcelableExtra(Intent.EXTRA_STREAM) : intent.getData();
        } catch (RuntimeException e) { // what another app put in the intent cannot be read
            Log.w(TAG, "shared file not read", e);
            return null;
        }
    }

    private void find(Uri uri) {
        AgvnOpenArchivePath.Named named = new AgvnOpenArchivePath.Named();
        File found = null;
        try {
            found = AgvnOpenArchivePath.resolve(getApplicationContext(), uri, named);
        } catch (RuntimeException e) {
            Log.w(TAG, "opened file not found: " + uri.getAuthority(), e);
        }
        File file = found;
        boolean archive = file != null && (AgvnArchiveKind.isArchiveName(file.getName()) || AgvnArchiveKind.sniff(file) != AgvnArchiveKind.Type.UNKNOWN);
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (file == null) {
                say(getString(R.string.agvn_open_no_path, named.name != null ? named.name : uri.getLastPathSegment()), false);
            } else if (!archive) {
                say(getString(R.string.agvn_unpack_err_not_archive), false);
            } else {
                pending = file;
                pendingAt = SystemClock.elapsedRealtime();
                openApp();
                finish();
            }
        });
    }

    /** The first start is over: language chosen, setup done, the Windows environment installed. */
    static boolean ready(Context ctx) {
        if (!AgvnLanguage.chosen(ctx)) return false;
        if (!PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(OnboardingActivity.PREF_ONBOARDING_COMPLETE, false)) return false;
        ImageFs imageFs = ImageFs.find(ctx);
        return imageFs.isValid() && imageFs.getVersion() >= ImageFsInstaller.LATEST_VERSION;
    }

    /** A game or another screen of the app is open above the library: it is not covered or closed for this. */
    private boolean busy() {
        try {
            ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            for (ActivityManager.AppTask task : manager.getAppTasks()) {
                ComponentName top = task.getTaskInfo().topActivity;
                String name = top != null ? top.getClassName() : null;
                if (name != null && !name.equals(getClass().getName()) && !name.equals(MainActivity.class.getName())
                        && !name.equals(SplashActivity.class.getName())) return true;
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "app tasks not read", e);
        }
        return false;
    }

    private void say(String message, boolean offerApp) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.agvn_open_title)
                .setMessage(message)
                .setNegativeButton(R.string.agvn_close, null)
                .setOnDismissListener(d -> finish());
        if (offerApp) dialog.setPositiveButton(R.string.agvn_open_app, (d, w) -> openApp());
        dialog.show();
    }

    /** The app as the player left it, or started. */
    private void openApp() {
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (open == null) open = new Intent(this, SplashActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(open);
    }

    /** The library is on screen: the archive handed over just now is unpacked. */
    public static void onResume(MainActivity a) {
        File archive = pending;
        if (archive == null || a.isFinishing() || a.getIntent().getBooleanExtra("edit_input_controls", false)) return;
        pending = null;
        if (SystemClock.elapsedRealtime() - pendingAt > PENDING_MS || !ready(a) || !archive.isFile()) return;
        AgvnUnpackFlow.run(a, archive, null, games -> {
            if (games > 1) AgvnAddGameHost.show(a); // several games: the list to pick from
        });
    }
}
