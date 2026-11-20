/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.container.Shortcut;

import java.io.File;

/**
 * Which way a game in the library runs, and the way back: "Chạy bằng Windows" was remembered for good, so a player who
 * pressed it by mistake had no way to "Chạy nhẹ" again short of removing the game ("Tự sửa lỗi" offers it only when
 * Windows fails). This finds the "Chạy nhẹ" a game has, as the importer does, for the library's "Cách chạy".
 */
public final class AgvnRunMode {
    /** A game's "Chạy nhẹ": its runner and, for a page game, the page it opens. */
    public static final class Light {
        public final String runner;
        public final String index;

        Light(String runner, String index) {
            this.runner = runner;
            this.index = index;
        }
    }

    private AgvnRunMode() {}

    /**
     * The "Chạy nhẹ" of the game in {@code gameDir}, in the importer's order, then the two "Tự sửa lỗi" also knows (a
     * Ren'Py 6 or 7 game on trial, an Electron game's page); null when it has none or its profile says Windows.
     */
    static Light lightFor(AgvnProfile profile, GameExeResolver.Engine engine, File gameDir, File exe) {
        if (gameDir == null || !gameDir.isDirectory()) return null;
        if (profile != null && AgvnHtmlGame.RUNNER_WINE.equals(profile.runner)) return null;
        File index = AgvnHtmlGame.indexFor(gameDir, engine);
        if (AgvnHtmlGame.useHtml(profile, index)) return new Light(AgvnHtmlGame.RUNNER_HTML, index.getAbsolutePath());
        if (AgvnRenpyGame.useRenpy(profile, engine, gameDir)) return new Light(AgvnHtmlGame.RUNNER_RENPY, null);
        if (AgvnRgssGame.useRgss(profile, engine, gameDir)) return new Light(AgvnHtmlGame.RUNNER_RGSS, null);
        if (exe != null && AgvnGodotLight.useGodot(profile, engine, exe)) return new Light(AgvnHtmlGame.RUNNER_GODOT, null);
        if (engine == GameExeResolver.Engine.RENPY && AgvnRenpyPy2.canTry(gameDir))
            return new Light(AgvnHtmlGame.RUNNER_RENPY, null);
        File page = AgvnElectronGame.pageOf(gameDir);
        return page != null ? new Light(AgvnHtmlGame.RUNNER_HTML, page.getPath()) : null;
    }

    public static Light lightFor(Shortcut s) {
        try {
            return lightFor(AgvnQuality.profileOf(s), engine(s.getExtra(AgvnGameImporter.EXTRA_ENGINE)),
                    AgvnEngineLogs.gameDir(s), AgvnEngineLogs.exe(s));
        } catch (RuntimeException e) {
            return null; // a shortcut this cannot read keeps the way it runs
        }
    }

    static GameExeResolver.Engine engine(String name) {
        try {
            return GameExeResolver.Engine.valueOf(name);
        } catch (RuntimeException e) {
            return GameExeResolver.Engine.UNKNOWN;
        }
    }

    public static boolean isLight(Shortcut s) {
        return AgvnHtmlGame.isLight(s.getExtra(AgvnHtmlGame.EXTRA_RUNNER));
    }

    /** True when the shortcut starts a real .exe, which Windows can run. */
    public static boolean hasWindows(Shortcut s) {
        return AgvnHtmlGame.exeIn(s.file) != null && AgvnHtmlGame.exeExists(s.file);
    }

    /** The next start is "Chạy nhẹ". */
    public static void toLight(Shortcut s, Light light) {
        s.putExtra(AgvnHtmlGame.EXTRA_RUNNER, light.runner);
        s.putExtra(AgvnHtmlGame.EXTRA_INDEX, light.index);
        s.saveData();
    }

    /** The next start is Windows, remembered as "Chạy bằng Windows" in game is. */
    public static void toWindows(Shortcut s) {
        s.putExtra(AgvnHtmlGame.EXTRA_RUNNER, AgvnHtmlGame.RUNNER_WINE);
        s.saveData();
    }
}
