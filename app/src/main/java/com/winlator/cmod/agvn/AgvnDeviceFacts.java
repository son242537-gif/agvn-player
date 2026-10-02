/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Facts for {@link AgvnDeviceReport} that need reading somewhere: the GPU's name, remembered in files/{@value #FILE}
 * so the "Chạy nhẹ" games' processes need not load a Vulkan driver to tell it, the CPU cores' top clocks and the
 * graphics drivers {@link DriverSafety} tried.
 */
final class AgvnDeviceFacts {
    static final String FILE = "agvn-device.properties";

    private AgvnDeviceFacts() {}

    /** The GPU's name: the one remembered on this phone, else asked of the phone's Vulkan driver now. */
    static String gpu(Context ctx) {
        Properties props = load(ctx);
        String remembered = phone().equals(props.getProperty("phone")) ? props.getProperty("gpu") : null;
        return remembered != null ? remembered : DriverSafety.getGpuRenderer(ctx);
    }

    /** Called once a process has the GPU's name; a failed probe ("", "Unknown") is not kept. */
    static void rememberGpu(Context ctx, String gpu) {
        if (ctx == null || gpu == null || gpu.trim().isEmpty() || gpu.toLowerCase(Locale.ROOT).contains("unknown")) return;
        Properties props = load(ctx);
        if (gpu.equals(props.getProperty("gpu")) && phone().equals(props.getProperty("phone"))) return;
        props.setProperty("gpu", gpu);
        props.setProperty("phone", phone());
        File file = new File(ctx.getFilesDir(), FILE), tmp = new File(ctx.getFilesDir(), FILE + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            props.store(out, "AGVN Player: device facts other processes read");
        } catch (IOException e) {
            return;
        }
        if (!tmp.renameTo(file)) tmp.delete();
    }

    /** The phone the facts were read on: app data restored onto another phone must not bring its GPU along. */
    private static String phone() {
        return Build.MANUFACTURER + "/" + Build.DEVICE;
    }

    private static Properties load(Context ctx) {
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(new File(ctx.getFilesDir(), FILE))) {
            props.load(in);
        } catch (IOException | RuntimeException ignored) {
            // not yet known
        }
        return props;
    }

    /** Each core's top clock in kHz (cpuinfo_max_freq); -1 for a core that is offline or cannot be read. */
    static long[] cpuMaxKhz() {
        List<Long> out = new ArrayList<>();
        for (int i = 0; new File("/sys/devices/system/cpu/cpu" + i).isDirectory(); i++) {
            try {
                File f = new File("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
                out.add(Long.parseLong(new String(Files.readAllBytes(f.toPath()), StandardCharsets.US_ASCII).trim()));
            } catch (IOException | RuntimeException e) {
                out.add(-1L); // offline or not readable
            }
        }
        long[] khz = new long[out.size()];
        for (int i = 0; i < khz.length; i++) khz[i] = out.get(i);
        return khz;
    }

    /** "System ok · turnip26.2.0 lỗi" for this app version's probes ({@link DriverSafety}). */
    static String drivers(Context ctx) {
        String suffix = "@" + DriverSafety.appVersionCode(ctx);
        TreeMap<String, String> tried = new TreeMap<>();
        for (Map.Entry<String, ?> e : ctx.getSharedPreferences(DriverSafety.PREFS, Context.MODE_PRIVATE).getAll().entrySet()) {
            String value = String.valueOf(e.getValue());
            if (e.getKey().endsWith(suffix))
                tried.put(e.getKey().substring(0, e.getKey().length() - suffix.length()),
                        "ok".equals(value) ? "ok" : "bad".equals(value) ? "lỗi" : "đang thử");
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : tried.entrySet()) sb.append(sb.length() > 0 ? " · " : "").append(e.getKey()).append(' ').append(e.getValue());
        return sb.length() > 0 ? sb.toString() : "chưa thử";
    }
}
