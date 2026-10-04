/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;

import com.winlator.cmod.R;
import com.winlator.cmod.box64.Box64Preset;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.OpenGLDriverDefaults;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * The buttons of a problem ({@link AgvnProblemCatalog.Finding#fixes}): each only when it changes something for this
 * game, and not again once tried since the game last ran well. A fix knows its target (the DXVK version, the driver,
 * the Đồ họa step), so the label says what it does; {@link AgvnFixApply} does it.
 */
final class AgvnFixes {
    /** Fixes that change no game setting: offered every time. */
    private static final List<String> ALWAYS = Arrays.asList("send-logs", "power-save-settings", "app-settings");
    /** Fixes of Wine's settings: none of them changes a "Chạy nhẹ" game. */
    private static final List<String> WINE_ONLY = Arrays.asList("restore-good", "reset", "dxvk-other", "dxvk-arm64ec",
            "driver-other", "wined3d", "godot-renderer", "godot-angle", "godot-undo", "render-gmem", "render-auto",
            "emulator-stable", "emulator-fast", "wincomponent");
    static final String SYSTEM = "System";

    /** A button: its fix id, its words, and what it sets ({@code to}). */
    static final class Fix {
        final String id, label, to;

        Fix(String id, String label, String to) {
            this.id = id;
            this.label = label;
            this.to = to;
        }

        /** True when the game must start again for the fix to work. */
        boolean changesGame() {
            return !ALWAYS.contains(id);
        }
    }

    private AgvnFixes() {}

    static List<Fix> applicable(Activity a, Shortcut s, AgvnProblemCatalog.Finding finding, Properties state) {
        Set<String> tried = AgvnGoodConfig.tried(state);
        List<Fix> fixes = new ArrayList<>();
        for (String id : finding.fixes()) {
            if (tried.contains(id) && !ALWAYS.contains(id)) continue;
            Fix fix = make(a, s, finding, state, id);
            if (fix != null) fixes.add(fix);
        }
        return fixes;
    }

    private static Fix make(Activity a, Shortcut s, AgvnProblemCatalog.Finding finding, Properties state, String id) {
        String runner = s.getExtra(AgvnHtmlGame.EXTRA_RUNNER);
        boolean light = AgvnHtmlGame.isLight(runner);
        if (light && (WINE_ONLY.contains(id) || id.startsWith("quality-") && !AgvnHtmlGame.RUNNER_RENPY.equals(runner))) return null;
        switch (id) {
            case "run-windows":
                return light && AgvnHtmlGame.exeExists(s.file) ? new Fix(id, a.getString(R.string.agvn_html_use_windows), AgvnHtmlGame.RUNNER_WINE) : null;
            case "rgss-frameskip":
                return AgvnHtmlGame.RUNNER_RGSS.equals(runner) && !AgvnRgssFiles.frameSkip(a, s)
                        ? new Fix(id, a.getString(R.string.agvn_fix_rgss_frameskip), "1") : null;
            case "restore-good":
                return AgvnGoodConfig.hasGood(state) && !AgvnGoodConfig.changed(AgvnGoodConfig.good(state),
                        AgvnGoodConfig.snapshot(s)).isEmpty() ? new Fix(id, a.getString(R.string.agvn_fix_restore_good), null) : null;
            case "reset":
                return s.getExtra(AgvnGameImporter.EXTRA_GAME_DIR).isEmpty() ? null : new Fix(id, a.getString(R.string.agvn_fix_reset), null);
            case "quality-down":
            case "quality-up":
                return quality(a, s, state, id);
            case "dxvk-other":
                return !dxvk(s) ? null : fix(a, id, R.string.agvn_fix_dxvk, AgvnFixEdits.otherDxvk(dxvkVersion(s)));
            case "dxvk-arm64ec": {
                String to = dxvk(s) && s.container.getWineVersion().contains("arm64ec") ? AgvnFixEdits.arm64ecDxvk(dxvkVersion(s)) : null;
                return to == null ? null : fix(a, id, R.string.agvn_fix_dxvk_arm64ec, to);
            }
            case "driver-other": {
                String to = turnip(a, s) ? SYSTEM : DefaultVersion.WRAPPER_ADRENO;
                if (!DriverSafety.isUsable(a, to)) return null;
                return new Fix(id, a.getString(R.string.agvn_fix_driver, SYSTEM.equals(to) ? a.getString(R.string.agvn_fix_driver_system) : "Turnip"), to);
            }
            case "wined3d":
                return dxvk(s) ? new Fix(id, a.getString(R.string.agvn_fix_wined3d), "wined3d") : null;
            case "godot-renderer":
            case "godot-angle":
            case "godot-undo":
                return AgvnGodotGame.fix(a, s, finding, id);
            case "render-gmem":
                return turnip(a, s) && !renderFlags(s).contains("gmem") ? new Fix(id, a.getString(R.string.agvn_fix_render_gmem), "gmem") : null;
            case "render-auto": {
                List<String> flags = renderFlags(s);
                boolean forced = flags.contains("gmem") || flags.contains("sysmem") || new EnvVars(s.getExtra("envVars")).has("TU_AUTOTUNE_ALGO");
                return turnip(a, s) && forced ? new Fix(id, a.getString(R.string.agvn_fix_render_auto), "") : null;
            }
            case "emulator-stable":
                return presets(s, Box64Preset.STABILITY) ? null : new Fix(id, a.getString(R.string.agvn_fix_emulator_stable), Box64Preset.STABILITY);
            case "emulator-fast":
                return presets(s, Box64Preset.PERFORMANCE) ? null : new Fix(id, a.getString(R.string.agvn_fix_emulator_fast), Box64Preset.PERFORMANCE);
            case "wincomponent": {
                String dll = finding.params.get("1");
                String component = dll == null ? null : AgvnFixEdits.componentFor(dll, AgvnFixApply.components(a));
                if (component == null || AgvnFixEdits.withComponent(winComponents(s), component) == null) return null;
                return fix(a, id, R.string.agvn_fix_wincomponent, component);
            }
            case "power-save-settings":
                return AgvnPowerSave.on(a) ? new Fix(id, a.getString(R.string.agvn_fix_power_save), null) : null;
            case "app-settings":
                return new Fix(id, a.getString(R.string.agvn_fix_app_settings), null);
            case "send-logs":
                return new Fix(id, a.getString(R.string.agvn_fix_send_logs), null);
            default:
                return null; // a fix a newer catalog names
        }
    }

    private static Fix fix(Activity a, String id, int label, String to) {
        return new Fix(id, a.getString(label, to), to);
    }

    /**
     * The next Đồ họa step down (never to a screen the game refused) or up; null when there is none. Up from "Tự động"
     * is its own step for this phone, written out: it replaces a screen size the player typed in.
     */
    private static Fix quality(Activity a, Shortcut s, Properties state, String id) {
        AgvnQuality.Level now = AgvnQuality.current(s);
        boolean auto = now == AgvnQuality.Level.AUTO;
        int step = (auto ? AgvnQuality.recommended(a) : now).step();
        int to = id.equals("quality-down") ? step - 1 : auto ? step : step + 1;
        if (to < 0 || to > 4) return null;
        AgvnQuality.Level level = AgvnQuality.Level.atStep(to);
        if (id.equals("quality-down") && AgvnGoodConfig.refused(state, level.resolution)) return null;
        return new Fix(id, a.getString(R.string.agvn_fix_quality, AgvnQualityDialog.name(a, level),
                level.resolution.replace('x', '×')), level.name());
    }

    static boolean dxvk(Shortcut s) {
        return s.getExtra("dxwrapper", s.container.getDXWrapper()).contains("dxvk");
    }

    static String dxvkConfig(Shortcut s) {
        return s.getExtra("dxwrapperConfig", s.container.getDXWrapperConfig());
    }

    static String dxvkVersion(Shortcut s) {
        return AgvnFixEdits.configValue(dxvkConfig(s), "version", ',');
    }

    static String driverConfig(Shortcut s) {
        return s.getExtra("graphicsDriverConfig", s.container.getGraphicsDriverConfig());
    }

    /** True when the game's Vulkan driver, as it will really start, is Turnip. */
    static boolean turnip(Activity a, Shortcut s) {
        String chosen = AgvnFixEdits.configValue(driverConfig(s), "version", ';');
        return OpenGLDriverDefaults.isTurnipDriver(DriverSafety.resolveUsable(a, chosen.isEmpty() ? SYSTEM : chosen));
    }

    static String inheritedTuDebug(Shortcut s) {
        return new EnvVars(s.container.getEnvVars()).get("TU_DEBUG");
    }

    private static List<String> renderFlags(Shortcut s) {
        return AgvnFixEdits.renderFlags(s.getExtra("envVars"), inheritedTuDebug(s));
    }

    static String winComponents(Shortcut s) {
        return s.getExtra("wincomponents", s.container.getWinComponents());
    }

    /** True when both CPU emulators already use {@code preset}. */
    private static boolean presets(Shortcut s, String preset) {
        return preset.equals(s.getExtra("box64Preset", s.container.getBox64Preset()))
                && preset.equals(s.getExtra("fexcorePreset", s.container.getFEXCorePreset()));
    }
}
