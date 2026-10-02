/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The HUD of a "Chạy nhẹ" game, at the top left: FPS where it can be measured (HTML games), the game's memory where
 * it runs in a process of its own, the phone's free RAM, the battery and its temperature, every
 * {@value #EVERY_MS} ms. It takes no touches.
 */
final class AgvnLightHud {
    static final long EVERY_MS = 1500;

    private final Activity activity;
    private final AgvnLightTools.Host host;
    private final TextView view;
    private ScheduledExecutorService poller;

    AgvnLightHud(Activity activity, AgvnLightTools.Host host) {
        this.activity = activity;
        this.host = host;
        float dp = activity.getResources().getDisplayMetrics().density;
        view = new TextView(activity);
        view.setTextSize(11);
        view.setTextColor(0xF2FFFFFF);
        view.setPadding((int) (8 * dp), (int) (3 * dp), (int) (8 * dp), (int) (3 * dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x88000000);
        bg.setCornerRadius(6 * dp);
        view.setBackground(bg);
        view.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        lp.setMargins((int) (8 * dp), (int) (8 * dp), 0, 0);
        activity.addContentView(view, lp);
    }

    synchronized void start() {
        view.setVisibility(View.VISIBLE);
        if (poller != null) return;
        poller = Executors.newSingleThreadScheduledExecutor();
        poller.scheduleWithFixedDelay(this::poll, 0, EVERY_MS, TimeUnit.MILLISECONDS);
    }

    synchronized void stop() {
        view.setVisibility(View.GONE);
        if (poller != null) poller.shutdownNow();
        poller = null;
    }

    private void poll() {
        try {
            String line = text(host.fps(), host.gameMb(), AgvnMemoryProbe.freeMb(), battery(activity),
                    ThermalMonitor.batteryTempC(activity));
            activity.runOnUiThread(() -> view.setText(line));
        } catch (RuntimeException ignored) {
            // one reading less; the next one comes
        }
    }

    private static int battery(Context context) {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        return bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
    }

    /** "FPS 60 · Game 412 MB · RAM trống 2,3 GB · Pin 78% · 38,5°C"; parts that are unknown (negative, NaN) are left out. */
    static String text(int fps, long gameMb, long freeMb, int batteryPercent, float tempC) {
        StringBuilder sb = new StringBuilder();
        if (fps >= 0) part(sb, "FPS " + fps);
        if (gameMb >= 0) part(sb, "Game " + gameMb + " MB");
        if (freeMb >= 0) part(sb, "RAM trống " + String.format(Locale.ROOT, "%.1f", freeMb / 1024f).replace('.', ',') + " GB");
        if (batteryPercent >= 0 && batteryPercent <= 100) part(sb, "Pin " + batteryPercent + "%");
        if (!Float.isNaN(tempC)) part(sb, String.format(Locale.ROOT, "%.1f", tempC).replace('.', ',') + "°C");
        return sb.toString();
    }

    private static void part(StringBuilder sb, String text) {
        if (sb.length() > 0) sb.append(" · ");
        sb.append(text);
    }
}
