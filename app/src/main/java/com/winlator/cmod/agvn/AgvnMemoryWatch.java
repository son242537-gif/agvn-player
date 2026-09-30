/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.winlator.cmod.R;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Watches memory every 5 s while a game is on screen ({@link AgvnMemoryRules} decides). On a warning it shows a bar
 * over the game with "Thoát game an toàn", so the player can save and leave before Android kills the whole app.
 * Warnings and the session's peaks go to the session log.
 */
public final class AgvnMemoryWatch {
    private static final String TAG = "AGVN";
    private static final long POLL_S = 5, PEAKS_EVERY_MS = 60_000;
    private static volatile long lastInputMs = SystemClock.uptimeMillis();

    private final Activity activity;
    private final Runnable exitGame;
    private final AgvnMemoryRules rules = new AgvnMemoryRules();
    private final long startedMs = SystemClock.uptimeMillis();
    private ScheduledExecutorService poller;
    private View bar;
    private long minFreeMb = Long.MAX_VALUE, maxRssMb, maxDmabufMb, peaksWrittenMs;

    public AgvnMemoryWatch(Activity activity, Runnable exitGame) {
        this.activity = activity;
        this.exitGame = exitGame;
    }

    /** Called on every touch, key or controller input. */
    public static void touched() {
        lastInputMs = SystemClock.uptimeMillis();
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

    /** The game ended: stop and write the peaks. */
    public void finish() {
        stop();
        writePeaks();
        activity.runOnUiThread(this::hideBar);
    }

    private void poll() {
        try {
            long now = SystemClock.uptimeMillis(), free = AgvnMemoryProbe.freeMb();
            AgvnMemoryProbe.Usage use = AgvnMemoryProbe.appUsage();
            synchronized (this) {
                if (free >= 0) minFreeMb = Math.min(minFreeMb, free);
                maxRssMb = Math.max(maxRssMb, use.rssMb);
                maxDmabufMb = Math.max(maxDmabufMb, use.dmabufMb);
            }
            if (now - peaksWrittenMs >= PEAKS_EVERY_MS) writePeaks();
            AgvnMemoryRules.Reason reason = rules.feed(now, startedMs, free, use.rssMb + use.dmabufMb, lastInputMs);
            if (reason == AgvnMemoryRules.Reason.NONE) return;
            AgvnSessionLog.event("Cảnh báo RAM (" + reason + "): còn trống " + free + " MB, app và game RSS "
                    + use.rssMb + " MB, DMA-BUF " + use.dmabufMb + " MB");
            activity.runOnUiThread(() -> showBar(reason, free, use));
        } catch (RuntimeException e) {
            Log.w(TAG, "memory watch sample failed", e);
        }
    }

    private synchronized void writePeaks() {
        peaksWrittenMs = SystemClock.uptimeMillis();
        if (minFreeMb == Long.MAX_VALUE) return;
        AgvnSessionLog.note(AgvnSessionLog.RAM, "RAM trống thấp nhất " + minFreeMb + " MB; app và game cao nhất: RSS "
                + maxRssMb + " MB, DMA-BUF " + maxDmabufMb + " MB\n");
    }

    private void showBar(AgvnMemoryRules.Reason reason, long freeMb, AgvnMemoryProbe.Usage use) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        hideBar();
        int pad = dp(12);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, pad / 2);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2332200);
        bg.setCornerRadius(dp(10));
        box.setBackground(bg);
        box.addView(text(activity.getString(R.string.agvn_ram_title), true));
        String detail = activity.getString(R.string.agvn_ram_detail, gb(freeMb), gb(use.rssMb + use.dmabufMb), gb(use.dmabufMb));
        if (reason == AgvnMemoryRules.Reason.GROWING) detail += "\n" + activity.getString(R.string.agvn_ram_growing);
        box.addView(text(detail, false));
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setGravity(Gravity.END);
        buttons.addView(button(R.string.agvn_ram_later, v -> hideBar()));
        buttons.addView(button(R.string.agvn_ram_exit, v -> {
            hideBar();
            exitGame.run();
        }));
        box.addView(buttons);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = dp(16);
        activity.addContentView(box, lp);
        bar = box;
    }

    private void hideBar() {
        if (bar != null && bar.getParent() instanceof ViewGroup) ((ViewGroup) bar.getParent()).removeView(bar);
        bar = null;
    }

    private TextView text(String s, boolean bold) {
        TextView t = new TextView(activity);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(bold ? 16 : 14);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, dp(4));
        return t;
    }

    private Button button(int label, View.OnClickListener click) {
        Button b = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        b.setText(label);
        b.setTextColor(0xFFFFD27A);
        b.setOnClickListener(click);
        return b;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    /** "1,2 GB" (Vietnamese decimal comma). */
    static String gb(long mb) {
        return String.format(new Locale("vi", "VN"), "%.1f GB", Math.max(0, mb) / 1024.0);
    }
}
