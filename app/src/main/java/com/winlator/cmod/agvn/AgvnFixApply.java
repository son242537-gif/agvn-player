/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Does a fix of {@link AgvnFixes} on the game's own settings (its shortcut), or opens the Android setting it names. */
final class AgvnFixApply {
    private static final String TAG = "AGVN";
    private static Map<String, List<String>> components;

    private AgvnFixApply() {}

    /** True when the game's settings changed, so it should start again. */
    static boolean apply(Activity a, Shortcut s, AgvnFixes.Fix fix, Properties state) {
        switch (fix.id) {
            case "send-logs":
                AgvnLogShare.share(a, s);
                return false;
            case "power-save-settings":
                open(a, new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS));
                return false;
            case "app-settings":
                open(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", a.getPackageName(), null)));
                return false;
            case "restore-good":
                AgvnGoodConfig.restore(s, AgvnGoodConfig.good(state));
                break;
            case "reset":
                reset(a, s);
                break;
            case "quality-down":
            case "quality-up":
                AgvnQuality.apply(a, s, AgvnQuality.Level.valueOf(fix.to));
                break;
            case "dxvk-other":
            case "dxvk-arm64ec":
                s.putExtra("dxwrapperConfig", AgvnFixEdits.withConfigValue(AgvnFixes.dxvkConfig(s), "version", fix.to, ','));
                break;
            case "driver-other":
                s.putExtra("graphicsDriverConfig", AgvnFixEdits.withConfigValue(AgvnFixes.driverConfig(s), "version", fix.to, ';'));
                break;
            case "wined3d":
                s.putExtra("dxwrapper", fix.to);
                break;
            case "godot-renderer":
            case "godot-angle":
            case "godot-undo":
                AgvnGodotGame.apply(s, fix);
                break;
            case "wrapper-constants":
            case "wrapper-clip":
                s.putExtra("envVars", AgvnWrapperPasses.off(s.getExtra("envVars"), fix.to));
                break;
            case "render-gmem":
            case "render-auto":
                s.putExtra("envVars", AgvnFixEdits.withRenderMode(s.getExtra("envVars"), AgvnFixes.inheritedTuDebug(s), fix.to));
                break;
            case "emulator-stable":
            case "emulator-fast":
                s.putExtra("box64Preset", fix.to);
                s.putExtra("fexcorePreset", fix.to);
                break;
            case "wincomponent":
                s.putExtra("wincomponents", AgvnFixEdits.withComponent(AgvnFixes.winComponents(s), fix.to));
                break;
            case "wine-mono":
                s.putExtra(AgvnWineMono.EXTRA, fix.to); // the next start unpacks it (AgvnWineMono.prepare)
                break;
            case "run-windows":
                s.putExtra(AgvnHtmlGame.EXTRA_RUNNER, fix.to); // the next start is Wine's
                break;
            case "rgss-frameskip":
                s.putExtra(AgvnRgssFiles.EXTRA_FRAME_SKIP, fix.to);
                break;
            default:
                return false;
        }
        s.saveData();
        if (fix.id.equals("restore-good") || fix.id.equals("reset")) state.remove(AgvnGoodConfig.TRIED);
        else AgvnGoodConfig.addTried(state, fix.id);
        AgvnGoodConfig.save(a, s, state);
        Log.i(TAG, "fix " + fix.id + (fix.to != null ? " -> " + fix.to : "") + " for " + s.name);
        return true;
    }

    /**
     * Back to what the import gave the game: no setting of its own besides the profile's arguments, environment and
     * locale, and "Tự động" for Đồ họa. Its controls, cover and saves stay.
     */
    static void reset(Context ctx, Shortcut s) {
        for (String key : AgvnGoodConfig.KEYS) s.putExtra(key, null);
        AgvnProfile p = AgvnQuality.profileOf(s);
        File gameDir = new File(s.getExtra(AgvnGameImporter.EXTRA_GAME_DIR));
        File exe = new File(s.path.replace("\"", ""));
        String dlls = GameDllOverrides.build(GameDllOverrides.detect(exe.getParentFile()), p.dllOverrides);
        s.putExtra("execArgs", String.join(" ", p.args));
        s.putExtra("envVars", AgvnGameImporter.buildEnvVars(p.env, dlls));
        String prefix = gameDir.getPath() + "/", path = exe.getPath();
        String relative = path.startsWith(prefix) ? path.substring(prefix.length()) : exe.getName();
        s.putExtra("lc_all", AgvnLocale.forGame(p, engine(s), gameDir.getName(), relative));
        AgvnQuality.apply(ctx, s, AgvnQuality.Level.AUTO); // screen size and FPS for this phone; saves the shortcut
    }

    private static GameExeResolver.Engine engine(Shortcut s) {
        try {
            return GameExeResolver.Engine.valueOf(s.getExtra(AgvnGameImporter.EXTRA_ENGINE));
        } catch (IllegalArgumentException e) {
            return GameExeResolver.Engine.UNKNOWN;
        }
    }

    /** Component name → its DLLs (assets/wincomponents/wincomponents.json). */
    static synchronized Map<String, List<String>> components(Context ctx) {
        if (components != null) return components;
        Type type = new TypeToken<LinkedHashMap<String, List<String>>>() {}.getType();
        try (Reader in = new InputStreamReader(ctx.getAssets().open("wincomponents/wincomponents.json"), StandardCharsets.UTF_8)) {
            Map<String, List<String>> read = new Gson().fromJson(in, type);
            components = read != null ? read : Collections.emptyMap();
        } catch (Exception e) {
            Log.w(TAG, "wincomponents.json not read", e);
            components = Collections.emptyMap();
        }
        return components;
    }

    /** Opens an Android settings screen, or Settings itself when this phone has not that one. */
    static void open(Activity a, Intent intent) {
        try {
            a.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            a.startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }
}
