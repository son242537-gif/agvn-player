/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.ActivityManager;
import android.app.GameManager;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.PerformanceHintManager;
import android.os.PowerManager;
import android.view.Display;

import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * thiet-bi.txt: what decides how games run on this phone, so that a player on any phone can send it with "Gửi nhật ký"
 * and the tiers (device-tiers.json) can be tuned for phones AGVN has not seen: chip, GPU and its Vulkan, CPU cores,
 * RAM, screen refresh rates, heat, battery saver, Android's game mode, the tier and why, the drivers tried
 * ({@link AgvnDeviceFacts}), the keyboards, mice and gamepads plugged in ({@link AgvnInputDevices}).
 */
final class AgvnDeviceReport {
    private static final String[] THERMAL = {"bình thường", "hơi ấm", "ấm", "nóng", "rất nóng", "khẩn cấp", "sắp tắt máy"};
    private static final String[] GAME_MODE = {"không hỗ trợ", "tiêu chuẩn", "hiệu năng", "tiết kiệm pin", "tuỳ chỉnh"};

    private AgvnDeviceReport() {}

    static String text(Context ctx) {
        StringBuilder sb = new StringBuilder("AGVN Player: thông tin máy\n");
        sb.append("Máy: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" · Android ")
                .append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("App: ").append(AgvnUpdater.installedName(ctx)).append(" (").append(AgvnUpdater.installedCode(ctx)).append(")\n");
        sb.append("Chip: ").append(DeviceTierManager.socModel()).append(" · ").append(Build.HARDWARE).append('\n');
        sb.append("GPU: ").append(AgvnDeviceFacts.gpu(ctx)).append('\n');
        for (String line : hardwareLines(ctx)) sb.append(line).append('\n');
        return sb.append(tierLine(ctx)).append('\n').toString();
    }

    /** Vulkan, CPU, RAM, screen, heat and power, drivers: each line on its own, a failing one left out. */
    static List<String> hardwareLines(Context ctx) {
        List<String> lines = new ArrayList<>();
        try {
            lines.add("Vulkan của máy: " + AgvnDxvkPick.name(AgvnDxvkPick.systemVulkan(ctx)));
        } catch (Throwable ignored) {}
        try {
            lines.add("CPU: " + cpu(AgvnDeviceFacts.cpuMaxKhz()));
        } catch (Throwable ignored) {}
        try {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ((ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
            lines.add("RAM: tổng " + gb(mi.totalMem >> 20) + " · trống " + gb(mi.availMem >> 20) + " · Android dọn app khi dưới "
                    + gb(mi.threshold >> 20));
        } catch (Throwable ignored) {}
        try {
            Display d = ctx.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
            Display.Mode cur = d.getMode();
            List<Float> rates = new ArrayList<>();
            for (Display.Mode m : d.getSupportedModes())
                if (m.getPhysicalWidth() == cur.getPhysicalWidth() && m.getPhysicalHeight() == cur.getPhysicalHeight()) rates.add(m.getRefreshRate());
            lines.add("Màn hình: " + screen(cur.getPhysicalWidth(), cur.getPhysicalHeight(), rates, d.getRefreshRate()));
        } catch (Throwable ignored) {}
        try {
            float headroom = ThermalMonitor.headroom(ctx);
            lines.add("Nhiệt: pin " + celsius(ThermalMonitor.batteryTempC(ctx)) + " · " + thermal(ThermalMonitor.thermalStatus(ctx))
                    + (Float.isNaN(headroom) ? "" : " · headroom " + decimal(headroom, 2) + " (1,0 = Android hạ xung mạnh)"));
        } catch (Throwable ignored) {}
        try {
            lines.add("Năng lượng: " + power(ctx));
        } catch (Throwable ignored) {}
        try {
            lines.add("Driver đã thử: " + AgvnDeviceFacts.drivers(ctx));
        } catch (Throwable ignored) {}
        try {
            lines.add(AgvnInputDevices.line());
        } catch (Throwable ignored) {}
        return lines;
    }

    /** "Mức máy: …" with how the rules got there, also when the player chose the tier by hand. */
    static String tierLine(Context ctx) {
        try {
            String gpu = AgvnDeviceFacts.gpu(ctx), soc = DeviceTierManager.socModel();
            long ram = DeviceTierManager.totalRamMb(ctx);
            DeviceTierRules rules = DeviceTierManager.getRules(ctx);
            String saved = PreferenceManager.getDefaultSharedPreferences(ctx).getString(DeviceTierManager.PREF_TIER, DeviceTierManager.AUTO);
            DeviceTier manual = DeviceTier.fromName(saved, null);
            String why = rules.why(gpu, soc, ram);
            return manual == null ? "Mức máy: " + rules.classify(gpu, soc, ram) + " (tự động: " + why + ")"
                    : "Mức máy: " + manual + " (chọn tay; tự động sẽ là: " + why + ")";
        } catch (Throwable e) {
            return "Mức máy: ? (" + e + ")";
        }
    }

    /** "8 nhân: 2×4,32 + 6×3,53 GHz", the fastest cores first; cores whose clock cannot be read count as cores only. */
    static String cpu(long[] maxKhz) {
        TreeMap<Long, Integer> byClock = new TreeMap<>(Collections.reverseOrder());
        for (long khz : maxKhz) if (khz > 0) byClock.merge(khz, 1, Integer::sum);
        StringBuilder sb = new StringBuilder(maxKhz.length + " nhân");
        String sep = ": ";
        for (Map.Entry<Long, Integer> e : byClock.entrySet()) {
            sb.append(sep).append(e.getValue()).append('×').append(decimal(e.getKey() / 1e6, 2));
            sep = " + ";
        }
        return byClock.isEmpty() ? sb.toString() : sb.append(" GHz").toString();
    }

    /** "1220×2712 · 60/90/120 Hz · đang 120 Hz". */
    static String screen(int width, int height, List<Float> rates, float current) {
        TreeSet<Integer> hz = new TreeSet<>();
        for (float r : rates) hz.add(Math.round(r));
        StringBuilder sb = new StringBuilder(width + "×" + height + " · ");
        for (int r : hz) sb.append(r).append(r == hz.last() ? "" : "/");
        return sb.append(" Hz · đang ").append(Math.round(current)).append(" Hz").toString();
    }

    static String thermal(int status) {
        return status >= 0 && status < THERMAL.length ? "trạng thái nhiệt: " + THERMAL[status] : "trạng thái nhiệt: không đọc được";
    }

    private static String power(Context ctx) {
        PowerManager pm = ctx.getSystemService(PowerManager.class);
        String text = "Tiết kiệm pin " + (pm.isPowerSaveMode() ? "BẬT (game chậm hơn)" : "tắt")
                + (ThermalMonitor.charging(ctx) ? " · đang sạc" : "")
                + " · giữ xung ổn định: " + (pm.isSustainedPerformanceModeSupported() ? "có" : "không");
        if (Build.VERSION.SDK_INT >= 31) {
            GameManager games = ctx.getSystemService(GameManager.class);
            int mode = games != null ? games.getGameMode() : -1;
            PerformanceHintManager hints = ctx.getSystemService(PerformanceHintManager.class);
            text += " · chế độ game Android: " + (mode >= 0 && mode < GAME_MODE.length ? GAME_MODE[mode] : "?")
                    + " · ADPF: " + (hints != null && hints.getPreferredUpdateRateNanos() > 0 ? "có" : "không");
        }
        return text;
    }

    private static String gb(long mb) {
        return decimal(mb / 1024.0, 1) + " GB";
    }

    private static String celsius(float c) {
        return Float.isNaN(c) ? "?" : decimal(c, 1) + "°C";
    }

    private static String decimal(double value, int places) {
        return String.format(Locale.ROOT, "%." + places + "f", value).replace('.', ',');
    }
}
