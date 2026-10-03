/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * How busy the GPU is, from every load file the kernels of Adreno (kgsl), Mali (utilisation, gpuinfo), MediaTek (ged,
 * mtk_mali), PowerVR and Exynos are known to have, as the Winlator HUD reads them, plus any devfreq node that looks like
 * a GPU. Phones that hide all of them from apps read -1. The parsing is pure Java (JVM-testable).
 */
final class AgvnGpuLoad {
    static final String[] PATHS = {
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/gpubusy",
            "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
            "/sys/class/misc/mali0/device/utilisation",
            "/sys/class/misc/mali0/device/utilization",
            "/sys/class/misc/mali0/device/gpuinfo",
            "/sys/devices/platform/mali/utilization",
            "/sys/kernel/gpu/gpu_busy",
            "/sys/devices/platform/gpusysfs/gpu_busy",
            "/sys/class/misc/pvrsrvkm/device/utilisation",
            "/sys/class/pvr/utilisation",
            "/sys/class/pvr/gpu_utilisation",
            "/sys/class/drm/card0/device/gpu_busy_percent",
            "/sys/class/devfreq/gpu/load",
            "/sys/kernel/ged/hal/gpu_utilization",
            "/sys/module/ged/parameters/gpu_loading",
            "/proc/mtk_mali/utilization"};
    private static final String[] USAGE_FILES = {"gpu_busy_percentage", "gpu_busy_percent", "gpu_load", "utilisation",
            "utilization", "load", "gpu_busy", "gpuinfo"};
    private static final String[] NODE_TOKENS = {"gpu", "mali", "g3d", "kgsl", "panfrost", "pvr", "powervr", "xclipse", "sgpu"};

    private List<String> files;
    private String working;
    private long lastGpuMs = -1, lastWallMs;

    /** GPU busy percent now, or -1 when this phone tells apps nothing. {@code nowMs}: a monotonic clock. */
    synchronized int percent(long nowMs) {
        if (files == null) files = discover();
        if (working != null) return sample(working, read(working), nowMs);
        for (String f : files) {
            int value = sample(f, read(f), nowMs);
            if (value >= 0) {
                working = f;
                return value;
            }
        }
        return -1;
    }

    /** The files to try: the known ones, then GPU-looking devfreq and platform nodes. */
    private static List<String> discover() {
        Set<String> found = new LinkedHashSet<>();
        for (String p : PATHS) if (new File(p).canRead()) found.add(p);
        scan(new File("/sys/class/devfreq"), found, false);
        scan(new File("/sys/devices/virtual/devfreq"), found, false);
        scan(new File("/sys/devices/platform"), found, true);
        return new ArrayList<>(found);
    }

    private static void scan(File root, Set<String> found, boolean byName) {
        File[] nodes = root.listFiles(File::isDirectory);
        if (nodes == null) return;
        for (File node : nodes) {
            if (!looksLikeGpu(byName ? node.getName() : node.getPath())) continue;
            for (String name : USAGE_FILES) {
                File f = new File(node, name);
                if (f.canRead()) found.add(f.getPath());
            }
        }
    }

    static boolean looksLikeGpu(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        for (String token : NODE_TOKENS) if (lower.contains(token)) return true;
        return false;
    }

    /** One reading of {@code path}'s {@code text}; Mali's gpuinfo counts GPU milliseconds, so it needs two readings. */
    int sample(String path, String text, long nowMs) {
        if (text == null) return -1;
        if (path.endsWith("/gpuinfo")) {
            String[] parts = text.trim().split("\\s+");
            long gpuMs;
            try {
                gpuMs = Long.parseLong(parts[parts.length - 1].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                return -1;
            }
            long was = lastGpuMs, wasWall = lastWallMs;
            lastGpuMs = gpuMs;
            lastWallMs = nowMs;
            return was < 0 || nowMs <= wasWall ? -1 : clamp((Math.max(0, gpuMs - was) * 100) / (nowMs - wasWall));
        }
        return parse(path, text);
    }

    /** "45 %", "45", "37@585000000Hz" → the first number; kgsl's gpubusy "busy total"; mtk_mali's "ACTIVE=45". */
    static int parse(String path, String text) {
        if (text == null) return -1;
        String t = text.trim();
        try {
            if (path.endsWith("/gpubusy")) {
                String[] parts = t.split("\\s+");
                long busy = Long.parseLong(parts[0]), total = parts.length > 1 ? Long.parseLong(parts[1]) : 0;
                return total > 0 ? clamp(busy * 100 / total) : -1;
            }
            int active = t.indexOf("ACTIVE=");
            if (active >= 0) t = t.substring(active + 7);
            for (String token : t.split("\\s+")) {
                String digits = token.split("@")[0].replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) return clamp(Long.parseLong(digits));
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return -1;
        }
        return -1;
    }

    private static int clamp(long value) {
        return (int) Math.max(0, Math.min(100, value));
    }

    private static String read(String path) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new FileReader(path))) {
            for (String line; (line = in.readLine()) != null && sb.length() < 512; ) sb.append(line).append(' ');
            return sb.toString();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
