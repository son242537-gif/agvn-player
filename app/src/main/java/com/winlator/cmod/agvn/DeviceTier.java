/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

/** Device strength buckets; ordinal order goes from strongest to weakest. */
public enum DeviceTier {
    FLAGSHIP("Mạnh"),
    TRUNG_BINH("Trung bình"),
    YEU("Yếu");

    public final String label;

    DeviceTier(String label) {
        this.label = label;
    }

    public static DeviceTier weaker(DeviceTier a, DeviceTier b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    public static DeviceTier fromName(String name, DeviceTier fallback) {
        if (name == null) return fallback;
        for (DeviceTier t : values()) if (t.name().equalsIgnoreCase(name)) return t;
        return fallback;
    }
}
