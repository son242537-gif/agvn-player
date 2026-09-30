/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.ui.FpsLimiterControl;
import com.winlator.cmod.widget.XServerRendererView;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Checks heat every 5 s while a game is on screen ({@link AgvnHeatRules} decides). When the phone slows the CPU down
 * for heat, a bar says so and offers "Hạ FPS 20", and tells the player to unplug the charger if charging. The session
 * log gets each warning and the session's worst readings. {@link GameSessionGuard} still warns about real danger
 * (battery at 45 °C, severe thermal status).
 */
public final class AgvnHeatWatch {
    private static final String TAG = "AGVN";
    private static final long POLL_S = 5, PEAKS_EVERY_MS = 60_000;
    private static final int LOWER_FPS = 20;
    private static final File CPUFREQ = new File("/sys/devices/system/cpu/cpufreq");

    private final XServerDisplayActivity activity;
    private final AgvnHeatRules rules = new AgvnHeatRules();
    private ScheduledExecutorService poller;
    private float minCap = Float.NaN, maxHeadroom = Float.NaN, maxBatteryC = Float.NaN;
    private boolean charged;
    private long peaksWrittenMs;

    public AgvnHeatWatch(XServerDisplayActivity activity) {
        this.activity = activity;
    }

    public synchronized void start() {
        if (poller != null) return;
        poller = Executors.newSingleThreadScheduledExecutor();
        poller.scheduleWithFixedDelay(this::poll, POLL_S, POLL_S, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (poller != null) poller.shutdownNow();
        poller = null;
    }

    /** The game ended: stop and write the worst readings. */
    public void finish() {
        stop();
        writePeaks();
    }

    private void poll() {
        try {
            float cap = AgvnHeatRules.fastestCap(CPUFREQ);
            float headroom = ThermalMonitor.headroom(activity), battery = ThermalMonitor.batteryTempC(activity);
            boolean charging = ThermalMonitor.charging(activity);
            synchronized (this) {
                minCap = lower(minCap, cap);
                maxHeadroom = -lower(-maxHeadroom, -headroom);
                maxBatteryC = -lower(-maxBatteryC, -battery);
                charged |= charging;
            }
            long now = SystemClock.uptimeMillis();
            if (now - peaksWrittenMs >= PEAKS_EVERY_MS) writePeaks();
            AgvnHeatRules.Reason reason = rules.feed(now, cap, headroom, battery);
            if (reason == AgvnHeatRules.Reason.NONE) return;
            AgvnSessionLog.event("Cảnh báo nóng (" + reason + "): CPU mạnh nhất tối đa " + percent(cap) + " tốc độ, headroom "
                    + headroom + ", pin " + battery + " °C, " + (charging ? "đang sạc" : "không sạc"));
            activity.runOnUiThread(() -> showBar(reason, cap, charging));
        } catch (RuntimeException e) {
            Log.w(TAG, "heat watch sample failed", e);
        }
    }

    private void showBar(AgvnHeatRules.Reason reason, float cap, boolean charging) {
        XServerRendererView view = activity.getXServerView();
        int fps = view != null ? view.getFpsLimit() : 0;
        boolean canLower = fps == 0 || fps > LOWER_FPS;
        boolean capped = reason == AgvnHeatRules.Reason.CPU_CAPPED;
        String title = activity.getString(capped ? R.string.agvn_heat_title_capped : R.string.agvn_heat_title_hot);
        StringBuilder detail = new StringBuilder(capped
                ? activity.getString(R.string.agvn_heat_capped, Math.round(cap * 100)) : activity.getString(R.string.agvn_heat_hot));
        if (charging) detail.append('\n').append(activity.getString(R.string.agvn_heat_charging));
        if (canLower) detail.append('\n').append(activity.getString(R.string.agvn_heat_fps));
        AgvnWarningBar.Choice later = new AgvnWarningBar.Choice(R.string.agvn_heat_later, null);
        if (canLower) AgvnWarningBar.show(activity, title, detail.toString(), later,
                new AgvnWarningBar.Choice(R.string.agvn_heat_fps20, this::lowerFps));
        else AgvnWarningBar.show(activity, title, detail.toString(), later);
    }

    /** Through the in-game "Giới hạn FPS" when it exists, so it shows and keeps the new limit. */
    private void lowerFps() {
        FpsLimiterControl control = find(activity.getWindow().getDecorView());
        if (control != null) control.setLimit(LOWER_FPS);
        else if (activity.getXServerView() != null) activity.getXServerView().setFpsLimit(LOWER_FPS);
        AgvnSessionLog.event("Hạ giới hạn FPS xuống " + LOWER_FPS + " từ cảnh báo nóng");
    }

    private static FpsLimiterControl find(View view) {
        if (view instanceof FpsLimiterControl) return (FpsLimiterControl) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            FpsLimiterControl found = find(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private synchronized void writePeaks() {
        peaksWrittenMs = SystemClock.uptimeMillis();
        if (Float.isNaN(minCap) && Float.isNaN(maxHeadroom) && Float.isNaN(maxBatteryC)) return;
        AgvnSessionLog.note(AgvnSessionLog.HEAT, "CPU mạnh nhất thấp nhất " + percent(minCap) + " tốc độ; thermal headroom cao nhất "
                + (Float.isNaN(maxHeadroom) ? "không đọc được" : String.format(Locale.ROOT, "%.2f", maxHeadroom))
                + "; pin nóng nhất " + (Float.isNaN(maxBatteryC) ? "?" : String.format(Locale.ROOT, "%.1f", maxBatteryC)) + " °C; "
                + (charged ? "có lúc đang sạc" : "không sạc") + "\n");
    }

    /** The smaller of two readings, NaN meaning "none yet". */
    private static float lower(float a, float b) {
        return Float.isNaN(a) ? b : Float.isNaN(b) ? a : Math.min(a, b);
    }

    private static String percent(float cap) {
        return Float.isNaN(cap) ? "không đọc được" : Math.round(cap * 100) + "%";
    }
}
