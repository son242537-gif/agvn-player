/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A DirectX game that froze right after DXVK made its swap chain again. A Unity 2022.3 game (D3D11, Adreno 830, app
 * 0.1.74, 03/11/2026) showed its Unity logo, then asked for its menu's resolution and vsync: DXVK made the swap chain
 * again in FIFO, printed its properties, and nothing more. The game's main thread waited at 0% CPU, black, at every
 * start, and its Player.log stopped there. It waits on the frame it presented: DXVK 2.x waits for each present
 * through VK_KHR_present_wait, which the wrapper may never answer for a swap chain made again
 * ({@link AgvnPresentSync}). The sign in Wine's tail: DXVK made a swap chain twice or more, and printed nothing after
 * the last one but its properties. With "froze" it is game-problems.json's "swapchain-frozen". Pure Java.
 */
final class AgvnSwapchainStuck {
    static final String MADE = "Presenter: Actual swap chain properties";
    /** A line of DXVK's own log: "info:  ", "warn:  ", "err:   "; Wine's are "err:module:...". */
    private static final Pattern DXVK = Pattern.compile("^(?:info|warn|err):\\s{2,}\\S");
    /** One of the properties under {@link #MADE}: "info:    Present mode: ...". */
    private static final Pattern PROPERTY = Pattern.compile("^info:\\s{4,}\\S");

    private AgvnSwapchainStuck() {}

    /** True when DXVK made a swap chain at least twice and printed nothing after the last one but its properties. */
    static boolean lastWords(List<String> lines) {
        int made = 0;
        boolean last = false;
        for (String line : lines) {
            if (line == null || !DXVK.matcher(line).find()) continue;
            if (line.contains(MADE)) {
                made++;
                last = true;
            } else if (!PROPERTY.matcher(line).find()) {
                last = false;
            }
        }
        return made >= 2 && last;
    }
}
