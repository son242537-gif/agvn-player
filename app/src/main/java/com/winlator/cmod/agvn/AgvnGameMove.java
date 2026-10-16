/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.xenvironment.ImageFs;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Moving a game to another container, so it runs on that container's Wine ({@link AgvnWine10}). The move is asked for
 * while the game runs, or from "Tự sửa lỗi" ({@link #request}: extra agvnMoveTo), and done at the game's next start,
 * before anything reads it ({@link #pending}, from XServerDisplayActivity): a running game writes its settings back to
 * its file when it ends. Its file, its icon, its saves in the Windows user folder (AgvnSaveLocations) and Unity's
 * PlayerPrefs (AgvnSavePrefs) go over; the game's own folder stays where it is, saves in it included.
 */
public final class AgvnGameMove {
    private static final String TAG = "AGVN", PROFILE = "profile/";
    static final String EXTRA_TO = "agvnMoveTo";
    /** "1": the game failed on Proton 10 and went back, so Proton 10 is not offered to it again. */
    static final String EXTRA_NOT_TEN = "agvnNotWine10";

    private AgvnGameMove() {}

    /** The game moves to {@code target} at its next start. */
    static void request(Shortcut s, Container target) {
        s.putExtra(EXTRA_TO, String.valueOf(target.id));
        s.saveData();
    }

    /** At the start of {@code s}: the moved game when a move was asked for and is done, else null. Never throws. */
    public static Shortcut pending(Context ctx, Shortcut s) {
        String to = s != null ? s.getExtra(EXTRA_TO) : "";
        if (to.isEmpty()) return null;
        try {
            s.putExtra(EXTRA_TO, null);
            s.saveData();
            Container target = new ContainerManager(ctx).getContainerById(Integer.parseInt(to.trim()));
            return target != null && target.id != s.container.id ? move(s, target) : null;
        } catch (RuntimeException e) {
            Log.w(TAG, "game not moved", e);
            return null;
        }
    }

    /** Moves {@code s} to {@code target}: the moved game, or null when a different game has its file name there. */
    static Shortcut move(Shortcut s, Container target) {
        File to = new File(target.getDesktopDir(), s.file.getName());
        if (to.exists() && !new Shortcut(target, to).path.equals(s.path)) {
            Log.w(TAG, "game not moved: " + to + " is another game");
            return null;
        }
        copySaves(s, target);
        if (!s.cloneToContainer(target)) return null;
        String base = s.file.getPath().replaceFirst("\\.desktop$", "");
        if (!s.file.delete()) Log.w(TAG, "old game file left: " + s.file);
        new File(base + ".lnk").delete();
        new File(base + ".bat").delete();
        Log.i(TAG, "game " + s.name + " moved to container " + target.id + " (" + target.getWineVersion() + ")");
        return new Shortcut(target, to);
    }

    private static void copySaves(Shortcut s, Container target) {
        File profile = new File(target.getRootDir(), ".wine/drive_c/users/" + ImageFs.USER);
        for (AgvnSaveLocations.Location place : AgvnSaveLocations.find(s)) {
            if (!place.zipPath.startsWith(PROFILE) || !place.dir.isDirectory()) continue;
            FileUtils.copy(place.dir, new File(profile, place.zipPath.substring(PROFILE.length())));
        }
        String key = AgvnSaveDialogs.prefsKey(s);
        if (key == null) return;
        try {
            List<String> prefs = AgvnSavePrefs.read(new File(s.container.getRootDir(), ".wine/user.reg"), key);
            if (!prefs.isEmpty()) AgvnSavePrefs.write(new File(target.getRootDir(), ".wine/user.reg"), key, prefs);
        } catch (IOException e) {
            Log.w(TAG, "PlayerPrefs not moved", e);
        }
    }
}
