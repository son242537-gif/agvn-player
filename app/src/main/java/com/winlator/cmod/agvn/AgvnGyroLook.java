/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Display;

/**
 * Tilting the phone turns a 3D game's camera, when the player turned it on for the game ("Camera 3D",
 * {@link AgvnCameraSettings#gyro}) and while the game holds the cursor ({@link AgvnLook#gyro}): turn the phone right
 * to look right, tilt its top toward you to look up. The gyroscope is read only then, while the game's screen is in
 * front, so it costs no battery to a game that does not use it. On the UI thread.
 */
final class AgvnGyroLook implements SensorEventListener, Application.ActivityLifecycleCallbacks {
    /** A gap longer than this between two readings (the screen was away) turns nothing. */
    private static final float MAX_GAP_S = 0.1f;

    private final Activity activity;
    private final AgvnLook look;
    private final SensorManager sensors;
    private final Sensor gyroscope;
    private boolean resumed = true, listening;
    private long lastNs;

    AgvnGyroLook(Activity activity, AgvnLook look) {
        this.activity = activity;
        this.look = look;
        sensors = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        gyroscope = sensors != null ? sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE) : null;
        activity.getApplication().registerActivityLifecycleCallbacks(this);
        update();
    }

    /** Whether this phone has a gyroscope. */
    static boolean available(Context context) {
        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        return sm != null && sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null;
    }

    /** Reads the gyroscope while the player wants it and the game's screen is in front; stops otherwise. */
    void update() {
        boolean want = gyroscope != null && resumed && look.settings().gyro;
        if (want == listening) return;
        listening = want;
        lastNs = 0;
        if (want) sensors.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        else sensors.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long last = lastNs;
        lastNs = event.timestamp;
        float seconds = (event.timestamp - last) / 1e9f;
        if (last == 0 || seconds <= 0 || seconds > MAX_GAP_S) return;
        float[] about = AgvnCamera.screenRates(rotation(), event.values[0], event.values[1]);
        float[] counts = AgvnCamera.gyro(about[0], about[1], seconds, look.settings().gyroSpeed);
        if (counts[0] != 0 || counts[1] != 0) look.gyro(counts[0], counts[1]);
    }

    @SuppressWarnings("deprecation") // Activity.getDisplay() needs Android 11
    private int rotation() {
        Display display = activity.getWindowManager().getDefaultDisplay();
        return display != null ? display.getRotation() : 0;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onActivityResumed(Activity a) {
        if (a != activity) return;
        resumed = true;
        update();
    }

    @Override
    public void onActivityPaused(Activity a) {
        if (a != activity) return;
        resumed = false;
        update();
    }

    @Override
    public void onActivityDestroyed(Activity a) {
        if (a != activity) return;
        resumed = false;
        update();
        activity.getApplication().unregisterActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityCreated(Activity a, Bundle state) {
    }

    @Override
    public void onActivityStarted(Activity a) {
    }

    @Override
    public void onActivityStopped(Activity a) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity a, Bundle state) {
    }
}
