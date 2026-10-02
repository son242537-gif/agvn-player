/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Parsed assets/agvn/device-tiers.json plus the pure classification logic (JVM-testable). */
public final class DeviceTierRules {
    static final class Rule {
        String pattern;
        String tier;
    }

    static final class Ram {
        int flagshipMinMb = 7000;
        int trungBinhMinMb = 5000;
    }

    /** Launch preset for one tier. */
    public static final class Preset {
        public int fps;
        public String resolution;
        public int texturePool;
    }

    List<Rule> gpus = new ArrayList<>();
    List<Rule> socs = new ArrayList<>();
    Ram ram = new Ram();
    Map<String, Preset> presets = new LinkedHashMap<>();

    public static DeviceTierRules parse(String json) {
        DeviceTierRules rules = null;
        try {
            rules = new Gson().fromJson(json, DeviceTierRules.class);
        } catch (RuntimeException ignored) {}
        if (rules == null) rules = new DeviceTierRules();
        if (rules.gpus == null) rules.gpus = new ArrayList<>();
        if (rules.socs == null) rules.socs = new ArrayList<>();
        if (rules.ram == null) rules.ram = new Ram();
        if (rules.presets == null) rules.presets = new LinkedHashMap<>();
        return rules;
    }

    /** GPU rule first, then SoC rule, else TRUNG_BINH; the RAM total then caps the result. */
    public DeviceTier classify(String gpuRenderer, String socModel, long totalRamMb) {
        DeviceTier chip = match(gpus, gpuRenderer);
        if (chip == null) chip = match(socs, socModel);
        if (chip == null) chip = DeviceTier.TRUNG_BINH;
        DeviceTier byRam = totalRamMb >= ram.flagshipMinMb ? DeviceTier.FLAGSHIP
                : totalRamMb >= ram.trungBinhMinMb ? DeviceTier.TRUNG_BINH : DeviceTier.YEU;
        return DeviceTier.weaker(chip, byRam);
    }

    public Preset preset(DeviceTier tier) {
        Preset p = presets.get(tier.name());
        if (p != null) return p;
        Preset fallback = new Preset();
        fallback.fps = tier == DeviceTier.YEU ? 24 : 30;
        fallback.resolution = tier == DeviceTier.YEU ? "854x480" : tier == DeviceTier.TRUNG_BINH ? "960x544" : "1280x720";
        fallback.texturePool = tier == DeviceTier.YEU ? 512 : tier == DeviceTier.TRUNG_BINH ? 768 : 1024;
        return fallback;
    }

    private static DeviceTier match(List<Rule> rules, String value) {
        if (value == null || value.isEmpty()) return null;
        for (Rule r : rules) {
            if (r == null || r.pattern == null) continue;
            try {
                if (Pattern.compile(r.pattern, Pattern.CASE_INSENSITIVE).matcher(value).find())
                    return DeviceTier.fromName(r.tier, null);
            } catch (RuntimeException ignored) {}
        }
        return null;
    }
}
