/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Graphics memory the Vulkan driver refused though there was room: DXVK's "Memory allocation failed" with every heap
 * it lists far under its budget. An Unreal Engine 5 game (Shipping, DX11, Adreno 830, 30/10/2026) failed so at every
 * start, its 4 MB render target refused in all three memory types with 448 MB of a 3355 MB budget used: the game had
 * marked it shared (D3D11_RESOURCE_MISC_SHARED), so DXVK asked for memory it can export, and the driver refused every
 * such allocation. Plain textures were given.
 *
 * <p>Wine offers games VK_KHR_external_memory_win32 only when the driver has {@link #EXTENSION} (winevulkan,
 * Proton 9). Without it DXVK 1.10.3 and 2.3.1 make a shared texture as a plain one ("Failed to create shared
 * resource: VK_KHR_EXTERNAL_MEMORY_WIN32 not supported"), which a game that never hands the texture to another process
 * does not notice. The fix ({@link #FIX}) hides it for the one game, through the wrapper's extension list. Pure Java
 * but for {@link #fix} (JVM-testable).
 */
final class AgvnGpuAlloc {
    static final String FIX = "vk-no-shared", EXTENSION = "VK_KHR_external_memory_fd", KEY = "blacklistedExtensions";
    /** Room a heap must have left (MiB), and four times the request, for the refusal not to be running out. */
    static final long ROOM_MB = 256;
    static final int SCAN_LINES = 24;
    private static final String FAILED = "DxvkMemoryAllocator: Memory allocation failed";
    private static final Pattern SIZE = Pattern.compile("\\bSize:\\s+(\\d+)\\s*$");
    /** DXVK 2.x: "  0:     8341         448         193         207        3355" (heap, size, allocated, used, reserved, budget). */
    private static final Pattern HEAP_2 = Pattern.compile(
            "(?:^|\\berr:)\\s*(\\d+):\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*$");
    /** DXVK 1.x: "Heap 0: 448 MB allocated, 193 MB used, 450 MB allocated (driver), 3355 MB budget (driver), 8341 MB total". */
    private static final Pattern HEAP_1 = Pattern.compile("Heap \\d+: (\\d+) MB allocated, \\d+ MB used, "
            + "(?:(\\d+) MB allocated \\(driver\\), (\\d+) MB budget \\(driver\\), )?(\\d+) MB total");

    private AgvnGpuAlloc() {}

    /** True when DXVK printed a failed allocation, and every heap it listed after it had room for it. */
    static boolean roomLeft(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).contains(FAILED)) continue;
            long size = -1;
            int heaps = 0;
            boolean room = true;
            for (int j = i + 1; j < Math.min(lines.size(), i + SCAN_LINES); j++) {
                String line = lines.get(j);
                if (line.contains(FAILED)) break; // DXVK 1.x prints it again as it throws: the next one is its own
                Matcher m = SIZE.matcher(line);
                if (size < 0 && m.find()) size = Long.parseLong(m.group(1));
                long free = free(line);
                if (free == Long.MIN_VALUE) continue;
                heaps++;
                room &= free >= ROOM_MB && free * 1024 * 1024 >= 4 * Math.max(size, 0);
            }
            if (heaps > 0 && room && size >= 0) return true;
        }
        return false;
    }

    /** MiB a heap line says are left, budget (or total) less allocated; Long.MIN_VALUE for another line. */
    static long free(String line) {
        Matcher m = HEAP_2.matcher(line);
        if (m.find()) return Long.parseLong(m.group(6)) - Long.parseLong(m.group(3));
        m = HEAP_1.matcher(line);
        if (!m.find()) return Long.MIN_VALUE;
        if (m.group(3) != null) return Long.parseLong(m.group(3)) - Long.parseLong(m.group(2));
        return Long.parseLong(m.group(4)) - Long.parseLong(m.group(1));
    }

    /** The wrapper's graphics driver config with {@link #EXTENSION} among its hidden extensions. */
    static String withHidden(String config) {
        String now = AgvnFixEdits.configValue(config, KEY, ';');
        if (hidden(now)) return config;
        return AgvnFixEdits.withConfigValue(config, KEY, now.isEmpty() ? EXTENSION : now + "," + EXTENSION, ';');
    }

    /** True when the comma list {@code blacklisted} has {@link #EXTENSION}. */
    static boolean hidden(String blacklisted) {
        for (String e : blacklisted.split(",")) if (e.trim().toLowerCase(Locale.ROOT).equals(EXTENSION.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    /** "Tắt bộ nhớ đồ họa chia sẻ" for a game drawn with Vulkan that still has it; else null. */
    static AgvnFixes.Fix fix(Context a, Shortcut s) {
        if (!AgvnFixes.dxvk(s) || hidden(AgvnFixEdits.configValue(AgvnFixes.driverConfig(s), KEY, ';'))) return null;
        return new AgvnFixes.Fix(FIX, a.getString(R.string.agvn_fix_vk_no_shared), EXTENSION);
    }
}
