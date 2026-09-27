/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * agvn-profile.json, schema v1. Declarative launch settings for one game folder; never holds runtime state.
 * Example:
 * <pre>
 * {"schemaVersion": 1, "name": "My Game", "exe": "MyGame/Binaries/Win64/MyGame-Win64-Shipping.exe",
 *  "args": ["-dx11"], "env": {"DXVK_HUD": "0"}, "resolution": "1280x720", "fpsLimit": 30,
 *  "texturePool": 1024, "simulatedTouchscreen": true,
 *  "weakDevice": {"resolution": "854x480", "fpsLimit": 24, "texturePool": 512},
 *  "ueEngineIni": {"/Script/Engine.RendererSettings": {"r.TextureStreamingPoolSize": "512"}}}
 * </pre>
 * Fields are public for Gson; treat instances as read-only after {@link #parse}.
 */
public final class AgvnProfile {
    public static final String FILE_NAME = "agvn-profile.json";
    public static final int SCHEMA_VERSION = 1;

    /** Optional overrides for weak phones (applied by device tiering). */
    public static final class Preset {
        public String resolution;
        public Integer fpsLimit;
        public Integer texturePool;
    }

    public int schemaVersion;
    public String name;
    /** Relative to the game folder; empty means "let GameExeResolver pick". */
    public String exe;
    public List<String> args = new ArrayList<>();
    public Map<String, String> env = new LinkedHashMap<>();
    public String resolution;
    /** 0 = unlimited. */
    public Integer fpsLimit;
    /** UE texture streaming pool in MB; 0 or missing = leave the game default. */
    public Integer texturePool;
    public Boolean simulatedTouchscreen;
    public Preset weakDevice;
    /** UE Engine.ini overrides: section -> key -> value. */
    public Map<String, Map<String, String>> ueEngineIni = new LinkedHashMap<>();

    public static AgvnProfile parse(String json) throws AgvnProfileException {
        AgvnProfile profile;
        try {
            profile = new Gson().fromJson(json, AgvnProfile.class);
        } catch (JsonParseException e) {
            throw new AgvnProfileException("File agvn-profile.json bị lỗi định dạng JSON.");
        }
        if (profile == null) throw new AgvnProfileException("File agvn-profile.json trống.");
        if (profile.args == null) profile.args = new ArrayList<>();
        if (profile.env == null) profile.env = new LinkedHashMap<>();
        if (profile.ueEngineIni == null) profile.ueEngineIni = new LinkedHashMap<>();
        return profile;
    }

    public boolean isSimulatedTouchscreen() {
        return simulatedTouchscreen == null || simulatedTouchscreen;
    }

    public int getFpsLimit() {
        return fpsLimit != null ? fpsLimit : 0;
    }

    public int getTexturePool() {
        return texturePool != null ? texturePool : 0;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
