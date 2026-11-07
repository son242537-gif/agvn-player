/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * A Ren'Py 6 or 7 game (Python 2) on "Chạy nhẹ", on trial: AGVN's Ren'Py 8.5.3 runs it with Python 2's ways
 * (app/src/main/renpy/agvn_py2.py). Ren'Py 8 read the .rpyc files of Ren'Py 7.3.5's own games and of a test game made
 * with it, and with agvn_py2.py ran their code as Ren'Py 7 did (07/11/2026); a game may still use some Python 2 that
 * Ren'Py 8 lacks, so the player picks it ("Tự sửa lỗi", or the profile's "runner": "renpy") and "Chạy bằng Windows"
 * takes it back.
 * <p>Ren'Py 8 reads Ren'Py 7's saves, but writes its own in a form Ren'Py 7 cannot read (pickle protocol 5): in the
 * game's saves folder, the Windows run would lose them, its persistent data too. So the trial saves in the app's
 * folder for the game, AGVN-Player/renpy/&lt;game folder&gt;/game/saves (ANDROID_OLD_PUBLIC, the first folder Ren'Py
 * tries), and gets a copy of the game's own saves at its first start. Pure Java.
 */
final class AgvnRenpyPy2 {
    private static final String TAG = "AGVN";

    private AgvnRenpyPy2() {}

    /** True for a Ren'Py 6 or 7 game "Chạy nhẹ" can try: Python 2, and its game/ folder. */
    static boolean canTry(File gameDir) {
        return gameDir != null && new File(gameDir, "game").isDirectory() && AgvnRenpyGame.pythonOf(gameDir) == 2;
    }

    /** Where the trial keeps its saves: the app's folder for the game ({@code publicDir}), game/saves in it. */
    static File savesDir(File publicDir) {
        return new File(publicDir, "game/saves");
    }

    /**
     * Before Ren'Py starts: the trial's saves folder, with a copy of the game's own saves (game/saves) when it has
     * none yet. Returns how many files were copied, -1 when the folder could not be made. Never throws.
     */
    static int prepareSaves(File gameDir, File publicDir) {
        File to = savesDir(publicDir);
        if (!to.isDirectory() && !to.mkdirs()) {
            Log.w(TAG, "cannot create " + to);
            return -1;
        }
        String[] there = to.list();
        if (there != null && there.length > 0) return 0;
        File[] own = new File(gameDir, "game/saves").listFiles();
        int copied = 0;
        if (own == null) return 0;
        for (File f : own) {
            if (!f.isFile()) continue;
            try {
                Files.copy(f.toPath(), new File(to, f.getName()).toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                copied++;
            } catch (IOException e) {
                Log.w(TAG, "save not copied: " + f, e);
            }
        }
        Log.i(TAG, "Ren'Py 7 on trial: " + copied + " save files copied to " + to);
        return copied;
    }
}
