/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.ArrayList;
import java.util.List;

/**
 * WINEDEBUG from "Bật debug Wine" and "Kênh debug Wine". Upstream wrote "+name" for every picked name. For the message
 * classes err, warn and fixme (the default pick), "+warn" names a channel that does not exist, so Wine logged only its
 * default errors and fixmes, never a warning such as Media Foundation's "Transform failed to process output". A class
 * is written "warn+all" instead, which turns it on for every channel.
 */
public final class AgvnWineDebug {
    private static final String[] CLASSES = {"err", "warn", "fixme"};

    private AgvnWineDebug() {}

    public static String spec(boolean enabled, String channels) {
        List<String> out = new ArrayList<>();
        if (enabled && channels != null) {
            for (String name : channels.split(",")) {
                name = name.trim();
                if (!name.isEmpty()) out.add(isClass(name) ? name + "+all" : "+" + name);
            }
        }
        return out.isEmpty() ? "-all" : String.join(",", out);
    }

    private static boolean isClass(String name) {
        for (String c : CLASSES) if (c.equals(name)) return true;
        return false;
    }
}
