/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.drawerlayout.widget.DrawerLayout;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.container.Shortcut;

import java.util.Locale;

/**
 * "Camera 3D" in a Windows game's sidebar, under "Rung": how fast the camera turns, how much up and down, and
 * whether tilting the phone turns it, kept for that game ({@link AgvnCameraSettings}) and felt at once in the game.
 */
public final class AgvnCameraDialog {
    private AgvnCameraDialog() {}

    /** Once the sidebar is set up: the game's camera settings take effect, the gyroscope follows, the row shows. */
    public static void attach(XServerDisplayActivity activity) {
        AgvnLook look = activity.getXServer().agvnLook;
        Shortcut shortcut = activity.agvnShortcut();
        String kept = shortcut != null ? shortcut.getExtra(AgvnCameraSettings.EXTRA) : null;
        AgvnCameraSettings settings = AgvnCameraSettings.parse(kept);
        look.setSettings(settings);
        String said = settings.describe();
        if (said != null) AgvnSessionLog.event(said);
        AgvnGyroLook gyro = new AgvnGyroLook(activity, look);
        View after = activity.findViewById(R.id.BTSubVibration);
        if (after == null || !(after.getParent() instanceof ViewGroup)) return;
        float dp = activity.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.sidebar_card);
        row.setPadding(Math.round(14 * dp), 0, Math.round(14 * dp), 0);
        row.setClickable(true);
        row.setFocusable(true);
        TextView text = new TextView(activity);
        text.setText(R.string.agvn_camera_title);
        text.setTextColor(0xFFFFFFFF);
        text.setTextSize(15);
        row.addView(text);
        row.setOnClickListener(v -> {
            View drawer = activity.findViewById(R.id.DrawerLayout);
            if (drawer instanceof DrawerLayout) ((DrawerLayout) drawer).closeDrawers();
            show(activity, shortcut, look, gyro);
        });
        ViewGroup parent = (ViewGroup) after.getParent();
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(56 * dp));
        lp.topMargin = Math.round(10 * dp);
        parent.addView(row, parent.indexOfChild(after) + 1, lp);
    }

    static void show(Activity a, Shortcut shortcut, AgvnLook look, AgvnGyroLook gyro) {
        AgvnCameraSettings s = AgvnCameraSettings.parse(look.settings().format());
        look.setSettings(s); // changed in place from here on, felt at once
        Runnable save = () -> {
            gyro.update();
            if (shortcut == null) return;
            String value = s.format();
            shortcut.putExtra(AgvnCameraSettings.EXTRA, value.isEmpty() ? null : value);
            shortcut.saveData();
        };
        int pad = dp(a, 20);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, dp(a, 8), pad, dp(a, 8));
        TextView note = new TextView(a);
        note.setText(R.string.agvn_camera_note);
        note.setAlpha(0.75f);
        box.addView(note);
        SeekBar speed = slider(a, box, R.string.agvn_camera_speed, AgvnCameraSettings.stepOf(s.speed), step -> {
            s.speed = AgvnCameraSettings.speedAt(step);
            return s.speed;
        }, save);
        int verticalStep = AgvnCameraSettings.verticalStepOf(s.vertical);
        SeekBar vertical = slider(a, box, R.string.agvn_camera_vertical, verticalStep, step -> {
            s.vertical = AgvnCameraSettings.verticalAt(step);
            return s.vertical;
        }, save);
        boolean hasGyro = AgvnGyroLook.available(a);
        Switch tilt = new Switch(a);
        tilt.setText(hasGyro ? R.string.agvn_camera_gyro : R.string.agvn_camera_no_gyro);
        tilt.setEnabled(hasGyro);
        tilt.setChecked(hasGyro && s.gyro);
        tilt.setPadding(0, dp(a, 14), 0, dp(a, 4));
        box.addView(tilt);
        int gyroStep = AgvnCameraSettings.stepOf(s.gyroSpeed);
        SeekBar gyroSpeed = slider(a, box, R.string.agvn_camera_gyro_speed, gyroStep, step -> {
            s.gyroSpeed = AgvnCameraSettings.speedAt(step);
            return s.gyroSpeed;
        }, save);
        gyroSpeed.setEnabled(hasGyro && s.gyro);
        tilt.setOnCheckedChangeListener((b, on) -> {
            s.gyro = on;
            gyroSpeed.setEnabled(on);
            save.run();
        });
        Button reset = new Button(a);
        reset.setAllCaps(false);
        reset.setText(R.string.agvn_camera_default);
        reset.setOnClickListener(v -> {
            speed.setProgress(AgvnCameraSettings.MIDDLE);
            vertical.setProgress(AgvnCameraSettings.MIDDLE);
            gyroSpeed.setProgress(AgvnCameraSettings.MIDDLE);
            s.speed = s.vertical = s.gyroSpeed = 1f;
            tilt.setChecked(false);
            save.run();
        });
        box.addView(reset);
        ScrollView scroll = new ScrollView(a);
        scroll.addView(box);
        new AlertDialog.Builder(a)
                .setTitle(R.string.agvn_camera_title)
                .setView(scroll)
                .setPositiveButton(R.string.agvn_camera_done, null)
                .show();
    }

    /** What a slider's step sets: the value it shows. */
    private interface Step {
        float set(int step);
    }

    /** A label with its value ("×1.15") and a slider of {@link AgvnCameraSettings#STEPS} steps, saved when let go. */
    private static SeekBar slider(Activity a, LinearLayout box, int label, int step, Step set, Runnable save) {
        TextView title = new TextView(a);
        title.setPadding(0, dp(a, 14), 0, 0);
        box.addView(title);
        SeekBar bar = new SeekBar(a);
        bar.setMax(AgvnCameraSettings.STEPS);
        bar.setProgress(step);
        String name = a.getString(label);
        title.setText(String.format(Locale.ROOT, "%s: ×%.2f", name, set.set(step)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                title.setText(String.format(Locale.ROOT, "%s: ×%.2f", name, set.set(progress)));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                save.run();
            }
        });
        box.addView(bar);
        return bar;
    }

    private static int dp(Activity a, int value) {
        return Math.round(value * a.getResources().getDisplayMetrics().density);
    }
}
