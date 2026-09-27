/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** Extra "key: value" lines for thiet-bi.txt contributed by other AGVN features. */
final class AgvnDiagnosticsExtras {
    private AgvnDiagnosticsExtras() {}

    static List<String> lines(Context ctx) {
        List<String> lines = new ArrayList<>();
        try {
            DeviceTier detected = DeviceTierManager.detect(ctx);
            lines.add("Mức máy: " + DeviceTierManager.current(ctx).name() + " (tự nhận: " + detected.name()
                    + ", SoC: " + DeviceTierManager.socModel() + ")");
        } catch (Throwable ignored) {}
        try {
            lines.add("Driver denylist GPU match System: " + DriverSafety.isDenylisted(ctx, "System"));
        } catch (Throwable ignored) {}
        return lines;
    }
}
