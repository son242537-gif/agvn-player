/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.winlator.cmod.R;

/**
 * The menu Back (or [☰]) opens on a "Chạy nhẹ" game: a panel on the left, dark as the Windows games' sidebar, with
 * the game's own menu, the keyboard, the on-screen keys (show, opacity, move), the HUD, "Gửi nhật ký", "Chạy bằng
 * Windows" and quitting. The game keeps running behind it; Back or a tap beside the panel closes it.
 */
final class AgvnLightMenu {
    private static final int TEXT = 0xFFDDF6FF, MUTED = 0xFF9FA5B1, ROW = 0xFF1A1A20, PANEL = 0xF2121216;
    private static final int OUTLINE = 0xFF454850, DANGER = 0xFFFF8A80;

    private final AgvnLightTools tools;
    /** The switch and the slider in today's look: the Ren'Py and RPG Maker screens keep Android's oldest theme. */
    private final Context widgets;
    private final float dp;
    private final int width;
    private final FrameLayout root;
    private final ScrollView scroll;
    private final LinearLayout panel;
    private final Switch keys, hud;
    private final SeekBar opacity;
    /** True while {@link #open} sets the switches, so their listeners do nothing. */
    private boolean filling, open;

    AgvnLightMenu(AgvnLightTools tools, String gameName) {
        this.tools = tools;
        Activity a = tools.activity;
        widgets = new ContextThemeWrapper(a, android.R.style.Theme_DeviceDefault);
        dp = a.getResources().getDisplayMetrics().density;
        root = new FrameLayout(a);
        root.setBackgroundColor(0x66000000);
        root.setVisibility(View.GONE);
        root.setOnClickListener(v -> close()); // a tap beside the panel
        panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(px(14), px(14), px(14), px(14));
        panel.setClickable(true); // taps on the panel stay on it
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PANEL);
        bg.setStroke(Math.max(1, px(1)), OUTLINE);
        bg.setCornerRadius(px(22));
        panel.setBackground(bg);
        text(gameName, 16, TEXT, true, px(4), 0);
        text(a.getString(R.string.agvn_light_subtitle), 12, MUTED, false, 0, px(8));
        row("▶", R.string.agvn_html_keep_playing, TEXT, this::close);
        row("☰", R.string.agvn_html_menu, TEXT, () -> after(tools.host::openGameMenu));
        row("⌨", R.string.agvn_bar_keyboard, TEXT, () -> after(tools.host::showKeyboard));
        text(a.getString(R.string.agvn_light_keys_section), 12, MUTED, true, px(12), 0);
        keys = switchRow("👁", R.string.agvn_rgss_show_keys, (b, on) -> { if (!filling) tools.setKeysShown(on); });
        opacity = seekRow();
        row("✎", R.string.agvn_light_edit, TEXT, tools::edit);
        text(a.getString(R.string.agvn_light_other_section), 12, MUTED, true, px(12), 0);
        hud = switchRow("📊", R.string.agvn_light_hud, (b, on) -> { if (!filling) tools.setHud(on); });
        row("📋", R.string.agvn_logs_send, TEXT, () -> after(tools::sendLogs));
        Runnable windows = tools.host.windows();
        if (windows != null) row("🖥", R.string.agvn_html_use_windows, TEXT, () -> after(windows));
        row("✕", R.string.agvn_html_exit, DANGER, () -> after(tools.host::quit));
        scroll = new ScrollView(a);
        scroll.addView(panel);
        width = Math.min(px(330), (int) (a.getResources().getDisplayMetrics().widthPixels * 0.48f));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        lp.setMargins(px(10), px(10), 0, px(10));
        root.addView(scroll, lp);
        a.addContentView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    boolean isOpen() {
        return open;
    }

    void open(boolean keysShown, float keyOpacity, boolean hudOn) {
        filling = true;
        keys.setChecked(keysShown);
        hud.setChecked(hudOn);
        opacity.setProgress(Math.round(keyOpacity * 100) - Math.round(AgvnLightPrefs.MIN_OPACITY * 100));
        filling = false;
        open = true;
        root.setVisibility(View.VISIBLE);
        scroll.setTranslationX(-width - px(10));
        scroll.animate().translationX(0).setDuration(160).start();
    }

    void close() {
        if (!open) return;
        open = false;
        scroll.animate().translationX(-width - px(10)).setDuration(140)
                .withEndAction(() -> root.setVisibility(View.GONE)).start();
        tools.menuClosed();
    }

    /** Closes the menu, then does {@code action} (the game gets its key once the panel is gone). */
    private void after(Runnable action) {
        close();
        root.postDelayed(action, 150);
    }

    private void text(String value, int sp, int color, boolean bold, int top, int bottom) {
        TextView t = new TextView(tools.activity);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(px(4), top, px(4), bottom);
        panel.addView(t);
    }

    private LinearLayout rowLayout(String glyph, int label, int color) {
        LinearLayout row = new LinearLayout(tools.activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(12), 0, px(12), 0);
        row.setMinimumHeight(px(48));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ROW);
        bg.setCornerRadius(px(14));
        row.setBackground(bg);
        TextView icon = new TextView(tools.activity);
        icon.setText(glyph);
        icon.setTextSize(16);
        icon.setTextColor(color);
        icon.setMinWidth(px(30));
        row.addView(icon);
        TextView name = new TextView(tools.activity);
        name.setText(label);
        name.setTextSize(14);
        name.setTextColor(color);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(6);
        panel.addView(row, lp);
        return row;
    }

    private void row(String glyph, int label, int color, Runnable action) {
        rowLayout(glyph, label, color).setOnClickListener(v -> action.run());
    }

    private Switch switchRow(String glyph, int label, CompoundButton.OnCheckedChangeListener onChange) {
        LinearLayout row = rowLayout(glyph, label, TEXT);
        Switch toggle = new Switch(widgets);
        toggle.setOnCheckedChangeListener(onChange);
        row.addView(toggle);
        row.setOnClickListener(v -> toggle.toggle());
        return toggle;
    }

    /** "Độ mờ phím": from {@link AgvnLightPrefs#MIN_OPACITY} to fully opaque. */
    private SeekBar seekRow() {
        LinearLayout row = rowLayout("◐", R.string.agvn_light_opacity, TEXT);
        row.setOrientation(LinearLayout.HORIZONTAL);
        SeekBar bar = new SeekBar(widgets);
        int min = Math.round(AgvnLightPrefs.MIN_OPACITY * 100);
        bar.setMax(100 - min);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser && !filling) tools.setOpacity((progress + min) / 100f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        row.addView(bar, new LinearLayout.LayoutParams(px(120), ViewGroup.LayoutParams.WRAP_CONTENT));
        return bar;
    }

    private int px(int value) {
        return Math.round(value * dp);
    }
}
