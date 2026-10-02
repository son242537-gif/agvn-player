/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.winlator.cmod.R;
import com.winlator.cmod.SettingsFragment;
import com.winlator.cmod.agvn.sdl.SDLActivity;
import com.winlator.cmod.core.FileUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Plays an RPG Maker XP/VX/VX Ace game on "Chạy nhẹ" ({@link AgvnRgssGame}) with mkxp-z (libmkxp-z.so, built by
 * scripts/agvn/mkxp-z), in its own process (":rgss"): SDL cannot start twice in one process, and a crash there never
 * takes the app down. On-screen keys ({@link AgvnRgssKeys}) play the game by touch; Back opens the same menu as the
 * other "Chạy nhẹ" runners. A game that stops on an error says why and offers "Chạy bằng Windows".
 */
public class AgvnRgssActivity extends SDLActivity {
    private static final String TAG = "AGVN";
    public static final String EXTRA_GAME_DIR = "agvn_rgss_game_dir";
    public static final String EXTRA_DRIVE_C = "agvn_rgss_drive_c";
    private static final String PREFS = "agvn_rgss";
    private static final String PREF_KEYS_HIDDEN = "keys_hidden";

    private AgvnRgssConfig config;
    private File errorFile;
    private AgvnRgssKeys keys;
    private boolean failed;

    @Override
    protected String[] getLibraries() {
        return new String[]{"mkxp-z"};
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String dir = getIntent().getStringExtra(EXTRA_GAME_DIR);
        String driveC = getIntent().getStringExtra(EXTRA_DRIVE_C);
        File runDir = new File(getFilesDir(), "rgss");
        errorFile = new File(runDir, "error.txt");
        errorFile.delete();
        if (dir != null) config = AgvnRgssFiles.prepare(this, new File(dir), driveC != null ? new File(driveC) : null, runDir);
        super.onCreate(savedInstanceState);
        if (mBrokenLibraries) return; // SDL shows its own error
        if (config == null) {
            end();
            return;
        }
        nativeSetenv("SRCDIR", runDir.getPath()); // mkxp-z reads mkxp.json there, then switches into the game folder
        nativeSetenv("AGVN_MKXPZ_ERROR_FILE", errorFile.getPath());
        keys = new AgvnRgssKeys(this);
        keys.setVisibility(getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_KEYS_HIDDEN, false) ? View.GONE : View.VISIBLE);
        mLayout.addView(keys, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        AgvnKeepAlive.startRgss(this, getIntent().getStringExtra("shortcut_name")); // keeps running in the background
    }

    @Override
    protected void onPause() {
        if (keys != null) keys.releaseAll(); // no key stays down while the game is in the background
        super.onPause();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean mouse = (event.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE;
        if (event.getKeyCode() != KeyEvent.KEYCODE_BACK || mouse) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) showMenu();
        return true;
    }

    private void showMenu() {
        boolean hidden = keys != null && keys.getVisibility() != View.VISIBLE;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.agvn_html_back_title)
                .setItems(new CharSequence[]{getString(R.string.agvn_html_menu),
                        getString(hidden ? R.string.agvn_rgss_show_keys : R.string.agvn_rgss_hide_keys),
                        getString(R.string.agvn_html_exit), getString(R.string.agvn_html_use_windows)}, (d, which) -> {
                    if (which == 0) pressAfterDialog(KeyEvent.KEYCODE_X); // RGSS's B button: opens the menu, or cancels
                    else if (which == 1) setKeysHidden(!hidden);
                    else if (which == 2) nativeSendQuit(); // as when a PC window is closed
                    else switchToWindows();
                })
                .setNegativeButton(R.string.agvn_html_keep_playing, null)
                .show();
    }

    private void setKeysHidden(boolean hidden) {
        if (keys == null) return;
        if (hidden) keys.releaseAll();
        keys.setVisibility(hidden ? View.GONE : View.VISIBLE);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_KEYS_HIDDEN, hidden).apply();
    }

    private void pressAfterDialog(int keyCode) {
        mLayout.postDelayed(() -> {
            onNativeKeyDown(keyCode);
            mLayout.postDelayed(() -> onNativeKeyUp(keyCode), 100); // held for a few frames, so RGSS sees it
        }, 200);
    }

    /** "Chạy bằng Windows": the shortcut remembers Wine and the game starts there; this process ends. */
    private void switchToWindows() {
        Intent intent = AgvnHtmlGame.toWindows(this, EXTRA_GAME_DIR);
        if (intent != null) startActivity(intent);
        end();
    }

    /** mkxp-z ended: a normal quit closes; a game that stopped on an error (mkxp-z wrote error.txt) says why first. */
    @Override
    public void finish() {
        String error = errorFile != null && errorFile.isFile() ? FileUtils.readString(errorFile) : null;
        if (failed || error == null || error.isEmpty()) {
            super.finish();
            return;
        }
        failed = true;
        Log.w(TAG, "mkxp-z stopped on an error: " + error);
        saveErrorLog(error);
        runOnUiThread(() -> new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.agvn_rgss_failed_title)
                .setMessage(failureMessage(error))
                .setPositiveButton(R.string.agvn_html_use_windows, (d, w) -> switchToWindows())
                .setNegativeButton(R.string.agvn_close, (d, w) -> end())
                .setCancelable(false)
                .show());
    }

    /** A missing file while an RTP the game asks for is not on the phone: how to add that RTP. Else the error itself. */
    private String failureMessage(String error) {
        String first = error.length() > 600 ? error.substring(0, 600) + "…" : error;
        boolean missingFile = error.contains("ENOENT") || error.contains("No such file") || error.contains("Unable to find");
        if (missingFile && config != null && !config.missingRtp.isEmpty()) {
            String name = config.missingRtp.get(0);
            File folder = new File(new File(SettingsFragment.DEFAULT_WINLATOR_PATH, AgvnRgssConfig.RTP_FOLDER), name);
            return getString(R.string.agvn_rgss_missing_rtp, name, folder.getPath()) + "\n\n" + first;
        }
        return getString(R.string.agvn_rgss_failed_message) + "\n\n" + first;
    }

    /** Keeps the error where "Gửi nhật ký" in the game's ⋮ menu finds it: a session folder of AGVN-Player/logs/<game>. */
    private void saveErrorLog(String error) {
        File dir = new File(new File(AgvnSessionLog.root(), AgvnLogFolders.safeName(getIntent().getStringExtra("shortcut_name"))),
                new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()));
        if (config == null || !dir.mkdirs()) return;
        FileUtils.writeString(new File(dir, AgvnSessionLog.SUMMARY), "runner=rgss (mkxp-z)\nrgss=" + config.rgss + "\ngame="
                + config.gameDir + "\nrtp=" + config.rtpDirs + "\nmissingRtp=" + config.missingRtp + "\nend=error\n");
        FileUtils.writeString(new File(dir, "loi-game.txt"), error);
    }

    /** Ends this process: SDL cannot start twice in one process, and the app's screens live elsewhere. */
    private void end() {
        Process.killProcess(Process.myPid());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) end();
    }
}
