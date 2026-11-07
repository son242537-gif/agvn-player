/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.FileUtils;

import java.io.File;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Ren'Py game on "Chạy bằng Windows" and what "Tự sửa lỗi" offers it:
 * <ul>
 *   <li>{@link #LIGHT}: a Ren'Py 6 or 7 game tried on "Chạy nhẹ" ({@link AgvnRenpyPy2}). A Ren'Py 7.3.5 game in Wine
 *   took 13 s to its menu, with FEX for its 32-bit exe and the phone's CPU held at 71 % by its heat, where a Ren'Py 8
 *   game of its size takes 4 s on "Chạy nhẹ" (POCO F8 Pro, 07/11/2026);</li>
 *   <li>{@link #ANGLE}: Ren'Py draws through ANGLE (Direct3D, so DXVK) instead of OpenGL, for a game that stays black:
 *   that Ren'Py 7.3.5 game showed nothing with "gl" and came up with "angle". RENPY_RENDERER is "angle" up to Ren'Py
 *   7.3, "angle2" from 7.4, whose games may need its newer renderer.</li>
 * </ul>
 * Pure Java but {@link #fix}.
 */
final class AgvnRenpyWine {
    static final String LIGHT = "renpy-light", ANGLE = "renpy-angle", RENDERER = "RENPY_RENDERER";
    private static final Pattern VERSION = Pattern.compile("^\\s*\\(?\\s*(\\d+)\\s*,\\s*(\\d+)");

    private AgvnRenpyWine() {}

    /** The button {@code id} for a Ren'Py game on Wine, or null when it does not apply or would change nothing. */
    static AgvnFixes.Fix fix(Context a, Shortcut s, String id) {
        if (!GameExeResolver.Engine.RENPY.name().equals(s.getExtra(AgvnGameImporter.EXTRA_ENGINE))
                || AgvnHtmlGame.isLight(s.getExtra(AgvnHtmlGame.EXTRA_RUNNER))) return null;
        File dir = AgvnEngineLogs.gameDir(s);
        if (LIGHT.equals(id)) {
            return AgvnRenpyPy2.canTry(dir) ? new AgvnFixes.Fix(id, a.getString(R.string.agvn_fix_renpy_light), AgvnHtmlGame.RUNNER_RENPY) : null;
        }
        if (!ANGLE.equals(id) || new EnvVars(s.getExtra("envVars")).has(RENDERER)) return null;
        return new AgvnFixes.Fix(id, a.getString(R.string.agvn_fix_renpy_angle), renderer(dir));
    }

    /** Black on Wine: ANGLE for a Ren'Py game that has not tried it; else null (AgvnBlackScreen). */
    static AgvnFixes.Fix blackFix(Context a, Shortcut s, Properties state) {
        return AgvnGoodConfig.tried(state).contains(ANGLE) ? null : fix(a, s, ANGLE);
    }

    /** Does {@code fix} on the game's settings; false when it is none of these. The caller saves. */
    static boolean apply(Shortcut s, AgvnFixes.Fix fix) {
        if (LIGHT.equals(fix.id)) {
            s.putExtra(AgvnHtmlGame.EXTRA_RUNNER, fix.to); // the next start is Ren'Py 8's, on trial
            return true;
        }
        if (!ANGLE.equals(fix.id)) return false;
        EnvVars vars = new EnvVars(s.getExtra("envVars"));
        vars.put(RENDERER, fix.to);
        s.putExtra("envVars", vars.toString());
        return true;
    }

    /** "angle" for a game of Ren'Py up to 7.3, "angle2" from 7.4 (and when its version cannot be told). */
    static String renderer(File gameDir) {
        int[] v = version(FileUtils.readString(new File(gameDir, "game/script_version.txt")));
        if (v == null) return oldLayout(gameDir) ? "angle" : "angle2";
        return v[0] < 7 || v[0] == 7 && v[1] < 4 ? "angle" : "angle2";
    }

    /** {major, minor} of script_version.txt's "(7, 3, 5)"; null when there is none. */
    static int[] version(String text) {
        Matcher m = text == null ? null : VERSION.matcher(text);
        if (m == null || !m.find()) return null;
        try {
            return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Ren'Py up to 7.3 keeps its Python in lib/windows-i686; 7.4 brought lib/py2-windows-*. */
    private static boolean oldLayout(File gameDir) {
        File[] libs = new File(gameDir, "lib").listFiles();
        if (libs == null) return false;
        boolean old = false;
        for (File f : libs) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (n.startsWith("py2-") || n.startsWith("py3-")) return false;
            old |= n.startsWith("windows-");
        }
        return old;
    }

    /** The line for su-kien.txt at a Wine start of a Ren'Py 6 or 7 game, why it is not on "Chạy nhẹ"; else null. */
    static String why(Shortcut s) {
        if (!GameExeResolver.Engine.RENPY.name().equals(s.getExtra(AgvnGameImporter.EXTRA_ENGINE))) return null;
        File dir = AgvnEngineLogs.gameDir(s);
        if (!AgvnRenpyPy2.canTry(dir)) return null;
        return "Game Ren'Py 6/7 (Python 2): \"Chạy nhẹ\" có Ren'Py 8, nên game chạy bằng Windows. Có thể chạy thử bằng "
                + "\"Chạy nhẹ\" trong \"Tự sửa lỗi\" (khi game chậm hoặc lỗi), file lưu được chép sang bản riêng";
    }
}
