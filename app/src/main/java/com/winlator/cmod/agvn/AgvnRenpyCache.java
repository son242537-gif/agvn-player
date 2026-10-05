/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Caps a Ren'Py game's image cache with a small script in its game/ folder, run after the game's own init code. The
 * cache keeps decoded images as GPU textures (4 bytes a pixel, which is RAM on a phone). Removing the script, and the
 * .rpyc that Ren'Py compiles from it, gives the game back its own setting.
 */
final class AgvnRenpyCache {
    private static final String TAG = "AGVN";
    static final String NAME = "zz_agvn_mem";

    private AgvnRenpyCache() {}

    static String script(int megabytes) {
        return "# AGVN Player: a low \"Do hoa\" step caps the image cache to save RAM.\n"
                + "# Moving the slider to Cao or Rat cao deletes this file.\n"
                + "init 999 python:\n"
                + "    config.image_cache_size = None\n"
                + "    config.image_cache_size_mb = " + megabytes + "\n";
    }

    /** {@code megabytes} > 0 writes the cap, 0 removes it. Does nothing outside a Ren'Py folder (no game/ dir). */
    static void apply(File gameDir, int megabytes) throws IOException {
        File game = new File(gameDir, "game");
        if (!game.isDirectory()) return;
        File rpy = new File(game, NAME + ".rpy");
        File rpyc = new File(game, NAME + ".rpyc");
        if (megabytes <= 0) {
            if (rpy.delete() | rpyc.delete()) Log.i(TAG, "Ren'Py image cache back to the game's own: " + game);
            return;
        }
        String text = script(megabytes);
        if (rpy.isFile() && text.equals(new String(Files.readAllBytes(rpy.toPath()), StandardCharsets.UTF_8))) return;
        Files.write(rpy.toPath(), text.getBytes(StandardCharsets.UTF_8));
        Log.i(TAG, "Ren'Py image cache capped at " + megabytes + " MB: " + rpy);
    }
}
