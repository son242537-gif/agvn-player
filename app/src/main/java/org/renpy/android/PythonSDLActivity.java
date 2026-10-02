/*
 * Ren'Py's Android activity, adapted for AGVN Player from Ren'Py 8.5.3 (rapt/prototype/renpyandroid,
 * org/renpy/android/PythonSDLActivity.java). Ren'Py: MIT License, Copyright 2004-2026 Tom Rothamel and contributors.
 * AGVN changes (no Play asset packs or in-app store, no web pages, no files unpacked here, a window flag in place of a
 * wake lock, quitting closes only this activity): Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE).
 *
 * The class keeps Ren'Py's name and members: librenpython.so calls nativeSetEnv and preparePython, and Ren'Py's Python
 * code (renpy/, lib/python3.12/android) reaches mActivity, hidePresplash, armOnStop, finishOnStop, vibrate, getDPI,
 * openUrl, setWakeLock and finishAndRemoveTask through pyjnius.
 */
package org.renpy.android;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import org.libsdl.app.SDLActivity;

public abstract class PythonSDLActivity extends SDLActivity {
    private static final String TAG = "python";

    /** The running activity, for Ren'Py's Python code. */
    public static PythonSDLActivity mActivity = null;

    /** False while Ren'Py saves on its way to the background; onStop waits for it (at most 8 s). */
    public boolean mStopDone = true;

    @Override
    protected String[] getLibraries() {
        return new String[] {"renpython"};
    }

    /** Keeps the orientation the manifest sets, as Ren'Py does. */
    @Override
    public void setOrientationBis(int w, int h, boolean resizable, String hint) {
    }

    public native void nativeSetEnv(String variable, String value);

    /** Called by librenpython.so on the SDL thread, before Python starts. */
    public void preparePython() {
        Log.v(TAG, "Starting preparePython.");
        mActivity = this;
        prepare();
        Log.v(TAG, "Finished preparePython.");
    }

    /** Makes the engine files ready and sets the environment (ANDROID_PRIVATE and the rest) with nativeSetEnv. */
    protected abstract void prepare();

    /** Called by Ren'Py when its first screen is ready. */
    public void hidePresplash() {
        runOnUiThread(this::onPresplashHidden);
    }

    protected void onPresplashHidden() {
    }

    @Override
    public void onStop() {
        super.onStop();
        long start = System.currentTimeMillis();
        synchronized (this) {
            while (!mStopDone && System.currentTimeMillis() < start + 8000) {
                try {
                    wait(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    public void armOnStop() {
        mStopDone = false;
    }

    public void finishOnStop() {
        synchronized (this) {
            mStopDone = true;
            notifyAll();
        }
    }

    /** Ren'Py ends with this, then System.exit: only this activity closes; the app's own screens stay. */
    @Override
    public void finishAndRemoveTask() {
        finish();
    }

    /** A game's link to a web page (Patreon, Discord...): AGVN Player opens no web pages. */
    public void openUrl(String url) {
        Log.i(TAG, "web page not opened: " + url);
        runOnUiThread(this::onWebPageRefused);
    }

    protected void onWebPageRefused() {
    }

    public void vibrate(double seconds) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot((long) (1000 * seconds), VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate((long) (1000 * seconds));
        }
    }

    public int getDPI() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return metrics.densityDpi;
    }

    /** Ren'Py keeps the screen on while a game is shown. */
    public void setWakeLock(boolean active) {
        runOnUiThread(() -> {
            if (active) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }
}
