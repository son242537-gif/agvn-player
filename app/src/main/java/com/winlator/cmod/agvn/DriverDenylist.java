/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Data-driven list of graphics drivers that must not be offered or used on a given GPU.
 * Source: assets/agvn/driver-denylist.json, e.g. [{"gpu": "Adreno \\(TM\\) 8\\d\\d", "hide": ["System"]}].
 * Pure Java (no Android types) so it can be unit tested on the JVM.
 */
public final class DriverDenylist {
    static final class Rule {
        String gpu;
        List<String> hide;
    }

    private final List<Rule> rules;

    private DriverDenylist(List<Rule> rules) {
        this.rules = rules;
    }

    public static DriverDenylist parse(String json) {
        try {
            List<Rule> rules = new Gson().fromJson(json, new TypeToken<List<Rule>>(){}.getType());
            return new DriverDenylist(rules != null ? rules : Collections.emptyList());
        } catch (RuntimeException e) {
            return empty();
        }
    }

    public static DriverDenylist empty() {
        return new DriverDenylist(new ArrayList<>());
    }

    /** True when {@code driverId} is hidden for the GPU whose renderer string is {@code gpuRenderer}. */
    public boolean isDenylisted(String gpuRenderer, String driverId) {
        if (gpuRenderer == null || driverId == null) return false;
        for (Rule rule : rules) {
            if (rule == null || rule.gpu == null || rule.hide == null) continue;
            boolean gpuMatches;
            try {
                gpuMatches = Pattern.compile(rule.gpu, Pattern.CASE_INSENSITIVE).matcher(gpuRenderer).find();
            } catch (RuntimeException e) {
                continue;
            }
            if (!gpuMatches) continue;
            for (String hidden : rule.hide) {
                if (driverId.equalsIgnoreCase(hidden)) return true;
            }
        }
        return false;
    }
}
