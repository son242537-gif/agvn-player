/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import com.winlator.cmod.container.Shortcut;

/**
 * A game's settings as its file holds them now. The library and the game's page act on the Shortcut they read when
 * they showed, and Shortcut.saveData writes all of it back: a fix put on the game since then (in the game, by "Tự sửa
 * lỗi" or the doctor, or the RAM mark of a game swiped away) was saved over. Support Pregnancy School then ran with its
 * settings from before "Dùng WineD3D" while that fix was on trial (Galaxy M34, 09/10/2026). So they read the file again
 * before they act on a game.
 */
public final class AgvnShortcutReload {
    private AgvnShortcutReload() {}

    /** {@code s} read again from its file, keeping its icon when the file gives none; {@code s} when that fails. */
    public static Shortcut of(Shortcut s) {
        if (s == null || s.file == null || !s.file.isFile()) return s;
        try {
            Shortcut now = new Shortcut(s.container, s.file);
            if (now.icon == null) now.icon = s.icon;
            return now;
        } catch (RuntimeException e) {
            Log.w("AGVN", "game settings not read again: " + s.file, e);
            return s;
        }
    }
}
