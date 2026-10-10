/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.util.Log;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Godot games: knowing one, its version, and the renderer "Tự sửa lỗi" picks for it. A game exported as one exe ("Embed
 * PCK") has no .pck beside it, so its import took it for an unknown engine: no OpenGL 3.3 from Zink
 * ({@link AgvnGlDriver#forGodot}), no godot.log, no Godot fixes. It is known by the pack at the end of its exe when it
 * starts, or by Godot's own words in its error box when it ends.
 */
public final class AgvnGodotGame {
    private static final String TAG = "AGVN", GODOT = GameExeResolver.Engine.GODOT.name();
    private static final Pattern FIRST_LINE = Pattern.compile("Godot Engine v(\\d+)\\.(\\d+)");
    /** Godot's first line, and its "Unable to initialize video driver" boxes (Godot 4, then Godot 3). */
    private static final Pattern SAYS_GODOT = Pattern.compile("Godot Engine v\\d|video card drivers seem not to support the "
            + "required|video card driver does not support any of the supported OpenGL versions", Pattern.CASE_INSENSITIVE);

    private AgvnGodotGame() {}

    /** A game starts: an unknown one whose exe ends with a Godot pack is a Godot game from now on. Never throws. */
    public static void recognize(Shortcut s) {
        if (s == null || !unknown(s)) return;
        try {
            String exe = AgvnExeRedirect.toUnixPath(s.path, s.container); // AGVN's own path, or a drive letter's
            if (exe != null && AgvnGodotFiles.version(new File(exe)) != null) remember(s, "its exe holds a Godot pack");
        } catch (RuntimeException e) {
            Log.w(TAG, "Godot pack not read", e);
        }
    }

    /** A game ended: true when an unknown one printed Godot's words in {@code lines}; it is a Godot game from now on. */
    static boolean learn(Shortcut s, List<String> lines) {
        if (!unknown(s) || !saysGodot(lines)) return false;
        remember(s, "it printed Godot's words");
        return true;
    }

    static boolean saysGodot(List<String> lines) {
        for (String line : lines) if (SAYS_GODOT.matcher(line).find()) return true;
        return false;
    }

    private static boolean unknown(Shortcut s) {
        String engine = s.getExtra(AgvnGameImporter.EXTRA_ENGINE);
        return engine.isEmpty() || GameExeResolver.Engine.UNKNOWN.name().equals(engine);
    }

    private static void remember(Shortcut s, String why) {
        s.putExtra(AgvnGameImporter.EXTRA_ENGINE, GODOT);
        s.saveData();
        Log.i(TAG, s.name + " is a Godot game: " + why);
    }

    /**
     * Puts the game's Godot version into {@code ev.params}: "godot" (major) and "godotMinor", from Godot's first line in
     * its log or Wine's, else from its pack. Nothing for a game of another engine or a version nothing tells.
     */
    static void version(AgvnEvidence ev, File exe, File gameDir) {
        if (!GODOT.equals(ev.engine)) return;
        int[] version = fromLines(ev.lines);
        if (version == null) version = AgvnGodotFiles.release(exe, gameDir);
        if (version == null) return;
        ev.params.put("godot", String.valueOf(version[0]));
        ev.params.put("godotMinor", String.valueOf(version[1]));
    }

    /** {4, 3} from Godot's first line ("Godot Engine v4.3.stable.official..."), or null. */
    static int[] fromLines(List<String> lines) {
        for (String line : lines) {
            Matcher m = FIRST_LINE.matcher(line);
            if (m.find()) return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        }
        return null;
    }

    /** The button of a Godot fix, or null when it changes nothing for this game ({@link AgvnFixes}). */
    static AgvnFixes.Fix fix(Activity a, Shortcut s, AgvnProblemCatalog.Finding f, String id) {
        if (!GODOT.equals(s.getExtra(AgvnGameImporter.EXTRA_ENGINE))) return null;
        String args = s.getExtra("execArgs");
        switch (id) {
            case "godot-renderer": {
                String to = AgvnFixEdits.godotArgs(f.params.get("godot"));
                if (args.contains(to)) return null;
                boolean three = AgvnFixEdits.GODOT3_ARGS.equals(to);
                return new AgvnFixes.Fix(id, a.getString(three ? R.string.agvn_fix_godot_gles2 : R.string.agvn_fix_godot_vulkan), to);
            }
            case "godot-angle":
                return AgvnFixEdits.godotAngle(f.params.get("godot"), f.params.get("godotMinor"))
                        && !args.contains(AgvnFixEdits.GODOT_ANGLE_ARGS)
                        ? new AgvnFixes.Fix(id, a.getString(R.string.agvn_fix_godot_angle), AgvnFixEdits.GODOT_ANGLE_ARGS) : null;
            case "godot-undo":
                return AgvnFixEdits.godotSwitched(args) ? new AgvnFixes.Fix(id, a.getString(R.string.agvn_fix_godot_undo), null) : null;
            case "godot-light": // a game moved to Windows, back on Godot for Android when Godot 4.7 reads it all
                return AgvnHtmlGame.RUNNER_WINE.equals(s.getExtra(AgvnHtmlGame.EXTRA_RUNNER))
                        && AgvnGodotLight.fit(AgvnGodotLight.mainPack(exe(s))) == AgvnGodotLight.Fit.OK
                        ? new AgvnFixes.Fix(id, a.getString(R.string.agvn_fix_godot_light), AgvnHtmlGame.RUNNER_GODOT)
                        : null;
            case "godot-light-renderer": { // "Chạy nhẹ": the other of Godot for Android's two renderers
                if (!AgvnHtmlGame.RUNNER_GODOT.equals(s.getExtra(AgvnHtmlGame.EXTRA_RUNNER))) return null;
                File exe = exe(s);
                String to = "gl".equals(lightRenderer(s.getExtra(AgvnGodotLight.EXTRA_RENDERER),
                        exe != null ? AgvnGodotUserDir.settingsOf(exe) : null)) ? "vulkan" : "gl";
                int label = "gl".equals(to) ? R.string.agvn_fix_godot_light_gl : R.string.agvn_fix_godot_light_vulkan;
                return new AgvnFixes.Fix(id, a.getString(label), to);
            }
            default:
                return null;
        }
    }

    /** The game starts with the fix's renderer instead of one picked before, or with its own for "godot-undo". */
    static void apply(Shortcut s, AgvnFixes.Fix fix) {
        s.putExtra("execArgs", AgvnFixEdits.withGodotRenderer(s.getExtra("execArgs"), fix.to));
    }

    /** Does {@link #fix}'s "Chạy nhẹ" fixes; false for any other fix. The caller saves. */
    static boolean applyLight(Shortcut s, AgvnFixes.Fix fix) {
        switch (fix.id) {
            case "godot-light":
                s.putExtra(AgvnHtmlGame.EXTRA_RUNNER, fix.to); // the next start is Godot for Android's
                return true;
            case "godot-light-renderer":
                s.putExtra(AgvnGodotLight.EXTRA_RENDERER, fix.to);
                return true;
            default:
                return false;
        }
    }

    /**
     * "gl" or "vulkan": what Godot for Android draws the game with, the renderer "Tự sửa lỗi" picked ({@code picked}),
     * else the project's for phones ({@code settings}, project.binary): Compatibility is OpenGL, Mobile (also a project
     * that names none) is Vulkan.
     */
    static String lightRenderer(String picked, Map<String, Object> settings) {
        if ("gl".equals(picked) || "vulkan".equals(picked)) return picked;
        Object method = settings != null ? settings.get(AgvnGodotUserDir.METHOD_MOBILE) : null;
        return "gl_compatibility".equals(method) ? "gl" : "vulkan";
    }

    /** The version a problem the player reports ("Tự sửa lỗi") goes with for a Godot game, so its fixes fit it. */
    static Map<String, String> params(Shortcut s) {
        AgvnEvidence ev = new AgvnEvidence();
        ev.engine = s.getExtra(AgvnGameImporter.EXTRA_ENGINE);
        File exe = exe(s);
        if (exe != null) version(ev, exe, AgvnEngineLogs.gameDir(s));
        return ev.params;
    }

    /** The game's exe on the phone: AGVN's own path, or a drive letter's; null when neither resolves. */
    private static File exe(Shortcut s) {
        String path = AgvnExeRedirect.toUnixPath(s.path, s.container);
        return path != null ? new File(path) : null;
    }
}
