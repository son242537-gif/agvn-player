/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.core.EnvVars;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** How each fix of {@link AgvnFixes} changes a game's settings text. Pure Java (JVM-testable). */
final class AgvnFixEdits {
    static final String DXVK_OLD = AgvnDxvkPick.OLD, DXVK_NEW = AgvnDxvkPick.NEW;
    static final String ARM64EC_OLD = DXVK_OLD + "-arm64ec-async", ARM64EC_NEW = DXVK_NEW + "-arm64ec-gplasync";
    static final String GODOT4_ARGS = "--rendering-method mobile --rendering-driver vulkan", GODOT3_ARGS = "--video-driver GLES2";
    /** Godot 4's Compatibility renderer on ANGLE: OpenGL ES drawn with Direct3D 11, so through DXVK instead of Zink. */
    static final String GODOT_ANGLE_ARGS = "--rendering-method gl_compatibility --rendering-driver opengl3_angle";
    private static final String[] GODOT_RENDERERS = {GODOT4_ARGS, GODOT_ANGLE_ARGS, GODOT3_ARGS};
    /**
     * Unreal 4 stops itself ("GameThread timed out waiting for RenderThread") once its render thread has been busy for
     * 120 s (g.TimeoutForBlockOnRenderFence); with this argument it waits instead.
     */
    static final String UE_NO_TIMEOUT = "-nothreadtimeout";

    private AgvnFixEdits() {}

    /** The other DXVK generation, 2.x and 1.10.3, keeping the arm64ec build when {@code version} is one. */
    static String otherDxvk(String version) {
        boolean arm = version != null && version.contains("arm64ec");
        boolean old = version != null && version.startsWith("1.");
        return old ? (arm ? ARM64EC_NEW : DXVK_NEW) : (arm ? ARM64EC_OLD : DXVK_OLD);
    }

    /**
     * The arm64ec build of {@code version}'s generation: DXVK's own code then runs as ARM code in the arm64ec Wine
     * instead of through the x86-64 emulator. Null when it already is one.
     */
    static String arm64ecDxvk(String version) {
        if (version != null && version.contains("arm64ec")) return null;
        return version != null && version.startsWith("1.") ? ARM64EC_OLD : ARM64EC_NEW;
    }

    /** The value of {@code key} in "a=1,b=2" ({@code separator} ',') or "a=1;b=2" (';'), "" when absent. */
    static String configValue(String config, String key, char separator) {
        if (config == null) return "";
        for (String item : config.split(String.valueOf(separator == ';' ? ";" : ","), -1)) {
            if (item.startsWith(key + "=")) return item.substring(key.length() + 1);
        }
        return "";
    }

    /** {@code config} with {@code key} set to {@code value}, added at the end when absent. */
    static String withConfigValue(String config, String key, String value, char separator) {
        String sep = String.valueOf(separator);
        List<String> items = new ArrayList<>();
        boolean found = false;
        for (String item : (config == null ? "" : config).split(separator == ';' ? ";" : ",", -1)) {
            if (item.isEmpty()) continue;
            if (item.startsWith(key + "=")) {
                items.add(key + "=" + value);
                found = true;
            } else {
                items.add(item);
            }
        }
        if (!found) items.add(key + "=" + value);
        return String.join(sep, items);
    }

    /** Exec arguments with {@code add} once at the end. */
    static String withArgs(String args, String add) {
        String now = args == null ? "" : args.trim();
        return now.contains(add) ? now : (now + " " + add).trim();
    }

    /** Exec arguments without {@code remove}. */
    static String withoutArgs(String args, String remove) {
        return (args == null ? "" : args.replace(remove, "")).trim().replaceAll("\\s{2,}", " ");
    }

    /** Godot 3 drops to GLES2 (OpenGL 2.1 is enough); Godot 4 renders with Vulkan's Mobile renderer. */
    static String godotArgs(String major) {
        return "3".equals(major) ? GODOT3_ARGS : GODOT4_ARGS;
    }

    /** True when the game starts with a Godot renderer a fix picked. */
    static boolean godotSwitched(String args) {
        for (String renderer : GODOT_RENDERERS) if (args != null && args.contains(renderer)) return true;
        return false;
    }

    /** Exec arguments with {@code renderer} in place of a Godot renderer a fix picked before; null: back to the game's own. */
    static String withGodotRenderer(String args, String renderer) {
        String out = args == null ? "" : args;
        for (String old : GODOT_RENDERERS) out = withoutArgs(out, old);
        return renderer == null ? out : withArgs(out, renderer);
    }

    /**
     * True from Godot 4.4: it knows ANGLE, and when ANGLE cannot start it goes back to native OpenGL by itself
     * (rendering/gl_compatibility/fallback_to_native), so trying ANGLE costs nothing where it fails.
     */
    static boolean godotAngle(String major, String minor) {
        try {
            int ma = Integer.parseInt(major), mi = Integer.parseInt(minor);
            return ma > 4 || ma == 4 && mi >= 4;
        } catch (NumberFormatException e) {
            return false; // a version the game did not tell
        }
    }

    /**
     * The game's environment with Turnip's rendering mode: "gmem" draws in the GPU's tile memory, "" lets Turnip choose
     * per render pass. {@code inherited}: TU_DEBUG the game gets from its container ("" when none), which the game's own
     * value replaces; its other flags (noconform) stay.
     */
    static String withRenderMode(String env, String inherited, String mode) {
        EnvVars vars = new EnvVars(env == null ? "" : env);
        String base = vars.has("TU_DEBUG") ? vars.get("TU_DEBUG") : inherited;
        List<String> flags = new ArrayList<>();
        for (String flag : base.split(",")) {
            if (!flag.isEmpty() && !flag.equals("sysmem") && !flag.equals("gmem")) flags.add(flag);
        }
        if (!mode.isEmpty()) flags.add(mode);
        vars.remove("TU_AUTOTUNE_ALGO");
        if (flags.isEmpty() && inherited.isEmpty()) vars.remove("TU_DEBUG");
        else vars.put("TU_DEBUG", String.join(",", flags));
        return vars.toString();
    }

    /** Turnip's flags the game runs with: its own TU_DEBUG, else its container's. */
    static List<String> renderFlags(String env, String inherited) {
        EnvVars vars = new EnvVars(env == null ? "" : env);
        List<String> flags = new ArrayList<>();
        for (String flag : (vars.has("TU_DEBUG") ? vars.get("TU_DEBUG") : inherited).split(",")) if (!flag.isEmpty()) flags.add(flag);
        return flags;
    }

    /** The Windows component (wincomponents.json) that brings {@code dll}, or null. */
    static String componentFor(String dll, Map<String, List<String>> components) {
        String name = dll.toLowerCase(Locale.ROOT).replaceAll("\\.dll$", "");
        for (Map.Entry<String, List<String>> e : components.entrySet()) {
            for (String file : e.getValue()) if (file.equalsIgnoreCase(name)) return e.getKey();
        }
        return null;
    }

    /** "direct3d=1,xaudio=0" with {@code component} on, or null when it is on already or not listed. */
    static String withComponent(String wincomponents, String component) {
        String value = configValue(wincomponents, component, ',');
        if (value.isEmpty() || value.equals("1")) return null;
        return withConfigValue(wincomponents, component, "1", ',');
    }
}
