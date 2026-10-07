/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Intent;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import com.winlator.cmod.SettingsFragment;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.ui.FpsLimiterControl;
import com.winlator.cmod.xenvironment.ImageFs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Chạy nhẹ" for Godot 4 games: the game's own pack runs on Godot for Android ({@link AgvnGodotActivity}), AGVN's build
 * of Godot 4.7.2 (scripts/agvn/godot), instead of Godot for Windows in Wine, drawing through ANGLE, DXVK and the Vulkan
 * wrapper. That way Party Me (Godot 4.6) froze 10 to 17 s at a time on a Mali-G615. A game runs here when Godot 4.7
 * reads all of it ({@link #fit}). It keeps user:// where its Windows version does (patches/0001), so both share saves.
 * Pure Java (JVM-testable) except {@link #start}, {@link #layoutKind} and {@link #setenv}.
 */
public final class AgvnGodotLight {
    private static final String TAG = "AGVN";
    /** Godot 4.7: it refuses packs of a later Godot. */
    static final int ENGINE_MINOR = 7;
    /** GDScript binary tokens it reads: Godot 4.5's on (101), and 4.3's and 4.4's (100) with patches/0003. */
    static final int ENGINE_TOKENS = 101, OLD_TOKENS = 100;
    /** "gl" or "vulkan": a renderer "Tự sửa lỗi" picked for the game; none means the game's own. */
    static final String EXTRA_RENDERER = "agvnGodotRenderer";

    /** Whether Godot 4.7 for Android runs a game, or why not. */
    enum Fit { OK, NO_PACK, NOT_GODOT_4, NEWER, ENCRYPTED, UNKNOWN_SCRIPTS, EXTENSIONS, MODULES, CSHARP }

    private AgvnGodotLight() {}

    static Fit fit(AgvnGodotPack pack) {
        if (pack == null) return Fit.NO_PACK;
        if (pack.major != 4 || pack.format < 2) return Fit.NOT_GODOT_4;
        if (pack.minor > ENGINE_MINOR || pack.format > 4) return Fit.NEWER;
        if (pack.encrypted()) return Fit.ENCRYPTED; // only the game's own engine has the key
        int tokens = pack.scriptTokens();
        if (tokens > 0 && tokens != ENGINE_TOKENS && tokens != OLD_TOKENS) return Fit.UNKNOWN_SCRIPTS;
        if (pack.extensions()) return Fit.EXTENSIONS; // their libraries are built for Windows
        if (AgvnGodotModules.missing(pack) != null) return Fit.MODULES; // built into the game's own engine only
        if (pack.csharp() || dotnetData(pack.file.getParentFile())) return Fit.CSHARP;
        return Fit.OK;
    }

    /**
     * The pack Godot for Windows opens for {@code exe} (ProjectSettings::_setup): the one embedded in it, else
     * &lt;name&gt;.pck or &lt;name&gt;.exe.pck beside it; else, for an exe renamed since, the only .pck beside it.
     */
    static AgvnGodotPack mainPack(File exe) {
        if (exe == null || !exe.isFile()) return null;
        AgvnGodotPack embedded = AgvnGodotPack.open(exe);
        if (embedded != null) return embedded;
        File dir = exe.getParentFile();
        String name = exe.getName(), base = name.replaceFirst("(?i)\\.exe$", "");
        for (String pck : new String[]{base + ".pck", name + ".pck"}) {
            File f = AgvnRgssGame.child(dir, pck);
            AgvnGodotPack p = f != null ? AgvnGodotPack.open(f) : null;
            if (p != null) return p;
        }
        File only = null;
        int count = 0;
        for (File f : AgvnRgssGame.list(dir)) {
            if (!f.isFile() || !f.getName().toLowerCase(Locale.ROOT).endsWith(".pck")) continue;
            only = f;
            count++;
        }
        return count == 1 ? AgvnGodotPack.open(only) : null;
    }

    /** True for a Godot .NET game's data folder beside its exe (data_&lt;name&gt;_windows_x86_64 with GodotSharp.dll). */
    static boolean dotnetData(File exeDir) {
        for (File d : AgvnRgssGame.list(exeDir)) {
            if (d.isDirectory() && d.getName().toLowerCase(Locale.ROOT).startsWith("data_")
                    && AgvnRgssGame.child(d, "GodotSharp.dll") != null) return true;
        }
        return false;
    }

    /** The runner at import, as AgvnRgssGame.useRgss: "Chạy nhẹ" whenever Godot 4.7 runs the game, unless the profile says Wine. */
    public static boolean useGodot(AgvnProfile profile, GameExeResolver.Engine engine, File exe) {
        if (engine != GameExeResolver.Engine.GODOT) return false;
        if (profile != null && AgvnHtmlGame.RUNNER_WINE.equals(profile.runner)) return false;
        return fit(mainPack(exe)) == Fit.OK;
    }

    /**
     * Godot's command line: the game's pack, its log, the FPS cap of the game's graphics level ({@code maxFps}, 0 for
     * none), the renderer "Tự sửa lỗi" picked ({@link #EXTRA_RENDERER}), and the whole screen without Android's bars.
     */
    static List<String> commandLine(File pack, File log, int maxFps, String renderer) {
        List<String> args = new ArrayList<>();
        args.add("--main-pack");
        args.add(pack.getPath());
        args.add("--log-file");
        args.add(log.getPath());
        if (maxFps > 0) {
            args.add("--max-fps");
            args.add(String.valueOf(maxFps));
        }
        if ("gl".equals(renderer)) {
            args.add("--rendering-method");
            args.add("gl_compatibility");
            args.add("--rendering-driver");
            args.add("opengl3");
        } else if ("vulkan".equals(renderer)) {
            args.add("--rendering-method");
            args.add("mobile");
            args.add("--rendering-driver");
            args.add("vulkan");
        }
        args.add("--fullscreen");
        return args;
    }

    /** The game's key set: the controls kind chosen at import (AgvnLayouts.EXTRA_KIND), else Godot games' 2D pad. */
    static String layoutKind(Intent intent) {
        String path = intent.getStringExtra("shortcut_path");
        String kind = path != null ? AgvnHtmlGame.readExtras(new File(path)).get(AgvnLayouts.EXTRA_KIND) : null;
        return AgvnLayouts.isKind(kind) ? kind : AgvnLayouts.kindFor(GameExeResolver.Engine.GODOT);
    }

    /** AGVN-Player/godot/&lt;game folder&gt;: Godot's log of the last "Chạy nhẹ" run, as Ren'Py's under renpy/. */
    static File publicDir(File agvnPlayerDir, File gameDir) {
        return new File(agvnPlayerDir, "godot/" + (gameDir != null ? gameDir.getName() : "_"));
    }

    /**
     * Called by AgvnHtmlGame.redirect for a shortcut set to "Chạy nhẹ" Godot. Returns false, so the game runs in Wine,
     * when its exe or pack moved, or Godot 4.7 no longer runs it.
     */
    static boolean start(Activity activity, Intent from, Map<String, String> extras) {
        String path = from.getStringExtra("shortcut_path");
        int id = from.getIntExtra("container_id", 0);
        if (id == 0 && path != null) id = AgvnHtmlGame.containerIdIn(new File(path));
        Container container = id != 0 ? new ContainerManager(activity).getContainerById(id) : null;
        if (path == null || container == null) return false;
        Shortcut shortcut = new Shortcut(container, new File(path));
        String exePath = AgvnExeRedirect.toUnixPath(shortcut.path, container);
        File exe = exePath != null ? new File(exePath) : null;
        AgvnGodotPack pack = mainPack(exe);
        Fit fit = fit(pack);
        if (fit != Fit.OK) {
            Log.w(TAG, "Godot game " + exe + " goes to Wine: " + fit);
            return false;
        }
        if (!AgvnHtmlGame.RUNNER_GODOT.equals(shortcut.getExtra(AgvnHtmlGame.EXTRA_RUNNER))) {
            shortcut.putExtra(AgvnHtmlGame.EXTRA_RUNNER, AgvnHtmlGame.RUNNER_GODOT); // "Tự sửa lỗi" can offer Windows now
            shortcut.saveData();
        }
        String dir = extras.get(AgvnGameImporter.EXTRA_GAME_DIR);
        File gameDir = dir != null && !dir.isEmpty() ? new File(dir) : exe.getParentFile();
        File logs = publicDir(new File(SettingsFragment.DEFAULT_WINLATOR_PATH), gameDir);
        File roaming = new File(container.getRootDir(), ".wine/drive_c/users/" + ImageFs.USER + "/AppData/Roaming");
        int fps = parse(extras.get(FpsLimiterControl.EXTRA_LIMIT));
        Intent intent = new Intent(activity, AgvnGodotActivity.class);
        if (from.getExtras() != null) intent.putExtras(from.getExtras());
        intent.putExtra(AgvnGodotActivity.EXTRA_GAME_DIR, gameDir.getPath());
        intent.putExtra(AgvnGodotActivity.EXTRA_EXE, exe.getPath());
        intent.putExtra(AgvnGodotActivity.EXTRA_APPDATA, roaming.getPath());
        intent.putExtra(AgvnGodotActivity.EXTRA_LOG, new File(logs, "godot.log").getPath());
        List<String> args = commandLine(pack.file, new File(logs, "godot.log"), fps, extras.get(EXTRA_RENDERER));
        intent.putExtra(AgvnGodotActivity.EXTRA_COMMAND_LINE, args.toArray(new String[0]));
        Log.i(TAG, "Godot " + pack.major + "." + pack.minor + "." + pack.patch + " game " + exe + " on Chạy nhẹ: " + args);
        activity.startActivity(intent);
        return true;
    }

    /** Sets an environment variable of this process, which Godot's engine reads as it starts. */
    static void setenv(String name, String value) {
        if (value == null) return;
        try {
            Os.setenv(name, value, true);
        } catch (ErrnoException e) {
            Log.w(TAG, "Godot: " + name + " not set", e);
        }
    }

    private static int parse(String value) {
        try {
            return value != null && !value.isEmpty() ? Math.max(0, Integer.parseInt(value.trim())) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
