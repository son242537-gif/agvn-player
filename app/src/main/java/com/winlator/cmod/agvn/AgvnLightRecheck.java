/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.FileUtils;

import java.io.File;
import java.util.Map;

/**
 * "Chạy nhẹ" for a Ren'Py 8 or RPG Maker XP/VX/VX Ace game whose folder suits it only now. The runner was chosen once,
 * when the game was added: a Ren'Py 8 game copied without lib/ went to Wine, and once lib/ and renpy/ were copied in,
 * it still started in Wine, its script loading in 92 s instead of 1.5 s; only removing and adding it again changed
 * that (POCO F8 Pro, 03/11/2026). At each start a game with no runner of its own (Wine because nothing else could run
 * it when it was added, or added before "Chạy nhẹ" took such games) gets the one adding it now would give, as
 * AgvnGameImporter.applyProfile decides. A player's "Chạy bằng Windows" and a profile's runner stay. Pure Java but
 * {@link #atStart}.
 */
final class AgvnLightRecheck {
    private static final String TAG = "AGVN";

    private AgvnLightRecheck() {}

    /** The runner the game starts on: {@code runner}, unless it has none and its folder now suits "Chạy nhẹ". */
    static String runner(String runner, String engine, AgvnProfile profile, File gameDir) {
        if (runner != null && !runner.isEmpty() || gameDir == null || engine == null) return runner;
        if (GameExeResolver.Engine.RENPY.name().equals(engine)
                && AgvnRenpyGame.useRenpy(profile, GameExeResolver.Engine.RENPY, gameDir)) return AgvnHtmlGame.RUNNER_RENPY;
        if (GameExeResolver.Engine.RPGMAKER.name().equals(engine)
                && AgvnRgssGame.useRgss(profile, GameExeResolver.Engine.RPGMAKER, gameDir)) return AgvnHtmlGame.RUNNER_RGSS;
        return runner;
    }

    /** The runner {@code s} starts on ({@link #runner}). */
    static String runner(Shortcut s) {
        String dir = s.getExtra(AgvnGameImporter.EXTRA_GAME_DIR);
        return runner(s.getExtra(AgvnHtmlGame.EXTRA_RUNNER), s.getExtra(AgvnGameImporter.EXTRA_ENGINE),
                AgvnQuality.profileOf(s), dir.isEmpty() ? null : new File(dir));
    }

    /**
     * At the start ({@link AgvnHtmlGame#redirect}, which every start goes through): {@code extras}, read from the
     * game's file, and the file itself get that runner. Never throws.
     */
    static void atStart(Context ctx, Intent from, Map<String, String> extras) {
        try {
            String runner = extras.get(AgvnHtmlGame.EXTRA_RUNNER), dir = extras.get(AgvnGameImporter.EXTRA_GAME_DIR);
            if (runner != null && !runner.isEmpty() || dir == null || dir.isEmpty()) return;
            String now = runner(runner, extras.get(AgvnGameImporter.EXTRA_ENGINE),
                    profile(extras.get(AgvnGameImporter.EXTRA_PROFILE_PATH)), new File(dir));
            String path = from.getStringExtra("shortcut_path");
            if (now == null || now.isEmpty() || path == null) return;
            int id = from.getIntExtra("container_id", 0);
            if (id == 0) id = AgvnHtmlGame.containerIdIn(new File(path));
            Container container = id != 0 ? new ContainerManager(ctx).getContainerById(id) : null;
            if (container == null) return;
            Shortcut s = new Shortcut(container, new File(path));
            s.putExtra(AgvnHtmlGame.EXTRA_RUNNER, now);
            s.saveData();
            extras.put(AgvnHtmlGame.EXTRA_RUNNER, now);
            Log.i(TAG, s.name + ": its folder now suits Chạy nhẹ (" + now + "); it ran in Wine");
        } catch (RuntimeException e) {
            Log.w(TAG, "runner not rechecked", e);
        }
    }

    /** The game's profile copy at {@code path}, or null (the importer's default: no runner of its own). */
    private static AgvnProfile profile(String path) {
        if (path == null || path.isEmpty()) return null;
        try {
            String json = FileUtils.readString(new File(path));
            return json == null || json.isEmpty() ? null : AgvnProfile.parse(json);
        } catch (AgvnProfileException e) {
            return null;
        }
    }
}
