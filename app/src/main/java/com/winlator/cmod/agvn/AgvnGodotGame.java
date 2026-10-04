/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.util.Log;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.util.List;
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
            default:
                return null;
        }
    }

    /** The game starts with the fix's renderer instead of one picked before, or with its own for "godot-undo". */
    static void apply(Shortcut s, AgvnFixes.Fix fix) {
        s.putExtra("execArgs", AgvnFixEdits.withGodotRenderer(s.getExtra("execArgs"), fix.to));
    }
}
