/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;

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
 * takes the app down. The "Chạy nhẹ" toolkit ({@link AgvnLightTools}) gives the RPG keys of the Windows layout, the
 * ⌨ ✎ 👁 ☰ bar, the menu on Back and the HUD. A game that stops on an error says why and offers "Chạy bằng Windows".
 */
public class AgvnRgssActivity extends SDLActivity {
    private static final String TAG = "AGVN";
    public static final String EXTRA_GAME_DIR = "agvn_rgss_game_dir";
    public static final String EXTRA_DRIVE_C = "agvn_rgss_drive_c";

    private AgvnRgssConfig config;
    private File errorFile;
    private AgvnLightTools tools;
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
        tools = AgvnLightTools.attach(this, AgvnLayouts.RPG, getIntent().getStringExtra("shortcut_name"), config.gameDir, new Host());
        AgvnKeepAlive.startRgss(this, getIntent().getStringExtra("shortcut_name")); // keeps running in the background
    }

    @Override
    protected void onPause() {
        if (tools != null) tools.onPause(); // no key stays down while the game is in the background
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tools != null) tools.onResume();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean mouse = (event.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE;
        if (event.getKeyCode() != KeyEvent.KEYCODE_BACK || mouse) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled() && tools != null) tools.onBack();
        return true;
    }

    /** What the toolkit asks of mkxp-z: keys through SDL, RGSS's B button for the menu, SDL's keyboard. */
    private final class Host implements AgvnLightTools.Host {
        @Override
        public void binding(String binding, boolean down) {
            int key = AgvnLightActions.forRgss(binding);
            if (key <= 0) return;
            if (down) onNativeKeyDown(key);
            else onNativeKeyUp(key);
        }

        @Override
        public void openGameMenu() {
            onNativeKeyDown(KeyEvent.KEYCODE_X); // RGSS's B button: opens the menu, or cancels
            mLayout.postDelayed(() -> onNativeKeyUp(KeyEvent.KEYCODE_X), 100); // held for a few frames, so RGSS sees it
        }

        @Override
        public void showKeyboard() {
            showTextInput(0, 0, 1, 1); // typed letters reach the game as keys, and as text for scripts that ask for it
        }

        @Override
        public void quit() {
            nativeSendQuit(); // as when a PC window is closed
        }

        @Override
        public Runnable windows() {
            return AgvnHtmlGame.hasWindowsExe(AgvnRgssActivity.this) ? AgvnRgssActivity.this::switchToWindows : null;
        }

        @Override
        public int fps() {
            return -1;
        }

        @Override
        public long gameMb() {
            return AgvnMemoryProbe.processMb();
        }
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
        boolean windows = AgvnHtmlGame.hasWindowsExe(this);
        runOnUiThread(() -> {
            AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(R.string.agvn_rgss_failed_title)
                    .setMessage(failureMessage(error))
                    .setNegativeButton(R.string.agvn_close, (d, w) -> end())
                    .setCancelable(false);
            if (windows) b.setPositiveButton(R.string.agvn_html_use_windows, (d, w) -> switchToWindows());
            b.show();
        });
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
