/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.util.Map;

/**
 * An NW.js page game ({@link AgvnNwjsGame}) imported before "Chạy nhẹ" took such games has no runner yet: it gets
 * "Chạy nhẹ" from its next start, as a Godot game does ({@link AgvnGodotLight}). The shortcut remembers it, so "Tự sửa
 * lỗi" and a failed start can offer "Chạy bằng Windows", which the game then keeps. A game that has picked Windows, or
 * any other engine, is left as it is.
 */
final class AgvnNwjsLight {
    private static final String TAG = "AGVN";

    private AgvnNwjsLight() {}

    /** The page {@code from}'s game now starts on, its shortcut set to "Chạy nhẹ"; null when it stays on Wine. */
    static String take(Activity activity, Intent from, Map<String, String> extras) {
        String runner = extras.get(AgvnHtmlGame.EXTRA_RUNNER), engine = extras.get(AgvnGameImporter.EXTRA_ENGINE);
        if (runner != null && !runner.isEmpty()) return null;
        if (engine != null && !engine.isEmpty() && !GameExeResolver.Engine.UNKNOWN.name().equals(engine)) return null;
        String path = from.getStringExtra("shortcut_path");
        if (path == null) return null;
        String dir = extras.get(AgvnGameImporter.EXTRA_GAME_DIR);
        File exe = AgvnHtmlGame.exeIn(new File(path));
        File gameDir = dir != null && !dir.isEmpty() ? new File(dir) : exe != null ? exe.getParentFile() : null;
        if (gameDir == null || GameExeResolver.detectEngine(gameDir) != GameExeResolver.Engine.UNKNOWN) return null;
        File page = AgvnNwjsGame.index(gameDir);
        if (page == null) return null;
        int id = from.getIntExtra("container_id", 0);
        if (id == 0) id = AgvnHtmlGame.containerIdIn(new File(path));
        Container container = id != 0 ? new ContainerManager(activity).getContainerById(id) : null;
        if (container == null) return null;
        Shortcut shortcut = new Shortcut(container, new File(path));
        shortcut.putExtra(AgvnHtmlGame.EXTRA_RUNNER, AgvnHtmlGame.RUNNER_HTML);
        shortcut.putExtra(AgvnHtmlGame.EXTRA_INDEX, page.getAbsolutePath());
        shortcut.saveData();
        Log.i(TAG, "NW.js page game " + page + " moves to Chạy nhẹ");
        return page.getAbsolutePath();
    }
}
