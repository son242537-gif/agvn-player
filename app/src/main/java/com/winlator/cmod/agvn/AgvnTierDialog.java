/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;

/** "Chế độ thiết bị": automatic detection or a manual tier; also used for a per-game choice at import. */
public final class AgvnTierDialog {
    public interface Listener {
        /** {@code tier == null} means automatic. */
        void onChosen(DeviceTier tier);
    }

    private AgvnTierDialog() {}

    /** Device-wide setting stored in preferences. */
    public static void showDeviceSetting(Context ctx, Runnable onChanged) {
        int checked = DeviceTierManager.isAuto(ctx) ? 0 : DeviceTierManager.current(ctx).ordinal() + 1;
        choose(ctx, ctx.getString(R.string.agvn_tier_title), checked, tier -> {
            DeviceTierManager.setOverride(ctx, tier);
            DeviceTier now = DeviceTierManager.current(ctx);
            Toast.makeText(ctx, now.label + " – " + DeviceTierManager.presetSummary(ctx, now), Toast.LENGTH_LONG).show();
            if (onChanged != null) onChanged.run();
        });
    }

    public static void choose(Context ctx, String title, int checked, Listener listener) {
        DeviceTier[] tiers = DeviceTier.values();
        String[] labels = new String[tiers.length + 1];
        labels[0] = ctx.getString(R.string.agvn_tier_auto, DeviceTierManager.detect(ctx).label);
        for (int i = 0; i < tiers.length; i++) labels[i + 1] = tiers[i].label;
        new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    listener.onChosen(which == 0 ? null : tiers[which - 1]);
                })
                .setNegativeButton(R.string.agvn_cancel, null)
                .show();
    }

    /** Settings row subtitle, e.g. "Tự động (Yếu) · Giới hạn FPS: 24, ...". */
    public static String summary(Context ctx) {
        DeviceTier tier = DeviceTierManager.current(ctx);
        String mode = DeviceTierManager.isAuto(ctx) ? ctx.getString(R.string.agvn_tier_auto, tier.label) : tier.label;
        return mode + " · " + DeviceTierManager.presetSummary(ctx, tier);
    }
}
