/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.winlator.cmod.R;
import com.winlator.cmod.SettingsFragment;

import org.renpy.android.PythonSDLActivity;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Plays a Ren'Py 8 game on "Chạy nhẹ" ({@link AgvnRenpyGame}) with Ren'Py 8.5.3 for Android, in its own process
 * (":renpy"): Ren'Py ends that process when the game quits, and a crash there never takes the app down. Back opens
 * the same menu as the HTML runner: the game's menu, quit, or "Chạy bằng Windows".
 */
public class AgvnRenpyActivity extends PythonSDLActivity {
    private static final String TAG = "AGVN";
    public static final String EXTRA_GAME_DIR = "agvn_renpy_game_dir";

    private File gameDir;
    /** Ren'Py's log.txt and traceback.txt (ANDROID_PUBLIC); also its saves when game/saves cannot be written. */
    private File publicDir;
    private File quitFile;
    private TextView splash;
    /** True once Ren'Py shows its first screen; before that Back just leaves (a dialog then would stop Ren'Py). */
    private volatile boolean started;
    private volatile boolean failed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String dir = getIntent().getStringExtra(EXTRA_GAME_DIR);
        gameDir = dir != null ? new File(dir) : null;
        publicDir = new File(SettingsFragment.DEFAULT_WINLATOR_PATH, "renpy/" + (gameDir != null ? gameDir.getName() : "_"));
        quitFile = new File(getCacheDir(), "agvn-renpy-quit");
        super.onCreate(savedInstanceState);
        if (mBrokenLibraries) return; // SDL shows its own error, then finish() offers Windows
        if (gameDir == null) {
            end();
            return;
        }
        showSplash();
    }

    private void showSplash() {
        String name = getIntent().getStringExtra("shortcut_name");
        String text = name != null ? name + "\n\n" : "";
        text += getString(AgvnRenpyFiles.needsUnpack(this) ? R.string.agvn_renpy_first_start : R.string.agvn_renpy_starting);
        splash = new TextView(this);
        splash.setText(text);
        splash.setTextColor(Color.parseColor("#E6E6EA"));
        splash.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        splash.setGravity(Gravity.CENTER);
        splash.setBackgroundColor(Color.BLACK);
        mLayout.addView(splash, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** On the SDL thread, before Python: the engine files, the saves folder and the environment. */
    @Override
    protected void prepare() {
        File engine;
        try {
            engine = AgvnRenpyFiles.prepare(this);
        } catch (IOException e) {
            Log.e(TAG, "Ren'Py engine could not be unpacked", e);
            runOnUiThread(() -> Toast.makeText(this, R.string.agvn_renpy_unpack_failed, Toast.LENGTH_LONG).show());
            try {
                Thread.sleep(3500);
            } catch (InterruptedException ignored) {
                // closing anyway
            }
            end(); // without the engine, Python cannot start
            return;
        }
        File game = new File(gameDir, "game");
        File saves = new File(game, "saves");
        if (game.isDirectory() && !saves.isDirectory() && !saves.mkdirs()) Log.w(TAG, "cannot create " + saves);
        if (!publicDir.isDirectory() && !publicDir.mkdirs()) Log.w(TAG, "cannot create " + publicDir);
        quitFile.delete();
        Map<String, String> env = AgvnRenpyGame.environment(engine, gameDir, publicDir, getApplicationInfo().sourceDir, quitFile);
        for (Map.Entry<String, String> e : env.entrySet()) nativeSetEnv(e.getKey(), e.getValue());
        Log.i(TAG, "Ren'Py game " + gameDir + ", logs in " + publicDir);
    }

    @Override
    protected void onPresplashHidden() {
        started = true;
        if (splash != null) {
            mLayout.removeView(splash);
            splash = null;
        }
    }

    @Override
    protected void onWebPageRefused() {
        Toast.makeText(this, R.string.agvn_renpy_no_web, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean mouse = (event.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE;
        if (event.getKeyCode() != KeyEvent.KEYCODE_BACK || mouse) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            if (started) showMenu();
            else end();
        }
        return true;
    }

    private void showMenu() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.agvn_html_back_title)
                .setItems(new CharSequence[]{getString(R.string.agvn_html_menu), getString(R.string.agvn_html_exit),
                        getString(R.string.agvn_html_use_windows)}, (d, which) -> {
                    if (which == 0) pressAfterDialog(KeyEvent.KEYCODE_ESCAPE); // Ren'Py's game menu key
                    else if (which == 1) askRenpyToQuit();
                    else switchToWindows();
                })
                .setNegativeButton(R.string.agvn_html_keep_playing, null)
                .show();
    }

    private void pressAfterDialog(int keyCode) {
        mLayout.postDelayed(() -> {
            onNativeKeyDown(keyCode);
            onNativeKeyUp(keyCode);
        }, 200);
    }

    /** As when a PC window is closed: the game asks, saves its persistent data, then quits (main.py watches the file). */
    private void askRenpyToQuit() {
        try {
            if (!quitFile.createNewFile()) Log.i(TAG, "quit already asked");
        } catch (IOException e) {
            Log.w(TAG, "quit request failed", e);
            end();
        }
    }

    /** "Chạy bằng Windows": the shortcut remembers Wine and the game starts there; this process ends. */
    private void switchToWindows() {
        Intent intent = AgvnHtmlGame.toWindows(this, EXTRA_GAME_DIR);
        if (intent != null) startActivity(intent);
        end();
    }

    /**
     * Ren'Py stopped before its first screen (an error in the game's scripts, or a game this Ren'Py cannot run): say so
     * and offer Windows, instead of a silent return to the library. A normal quit comes after the first screen.
     */
    @Override
    public void finish() {
        if (started || failed) {
            super.finish();
            return;
        }
        failed = true;
        runOnUiThread(() -> new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.agvn_renpy_failed_title)
                .setMessage(getString(R.string.agvn_renpy_failed_message, new File(publicDir, "traceback.txt").getPath()))
                .setPositiveButton(R.string.agvn_html_use_windows, (d, w) -> switchToWindows())
                .setNegativeButton(R.string.agvn_close, (d, w) -> end())
                .setCancelable(false)
                .show());
    }

    /** Ends this process: SDL and Python cannot start twice in one process, and the app's screens live elsewhere. */
    private void end() {
        Process.killProcess(Process.myPid());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) end();
    }
}
