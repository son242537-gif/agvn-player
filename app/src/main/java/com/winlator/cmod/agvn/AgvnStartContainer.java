/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;

import java.io.File;
import java.util.List;

/**
 * The container a game starts in: the one that holds its file now. A start names a container and the game's file (the
 * library, a home-screen shortcut, adb), and they can disagree. A game moved to Proton 10 ({@link AgvnGameMove},
 * "Tự sửa lỗi") has its file in Proton 10's container, and the old file is deleted, while a home-screen shortcut made
 * before keeps the old container and file. An adb start with container_id 1 for a game on Proton 10 ran it on Proton 9
 * (27/10/2026). So the container whose desktop holds the file is the game's, and a file that is gone is looked for by
 * its name on the other containers' desktops. A file outside every desktop (a file manager's start) keeps the container
 * named. Pure Java but {@link #fix}.
 */
public final class AgvnStartContainer {
    private static final String TAG = "AGVN";

    private AgvnStartContainer() {}

    /** Puts the game's container and file in {@code activity}'s intent, first thing at its start. Never throws. */
    public static void fix(Activity activity) {
        try {
            Intent intent = activity.getIntent();
            String path = intent != null ? intent.getStringExtra("shortcut_path") : null;
            if (path == null || path.isEmpty()) return;
            int named = intent.getIntExtra("container_id", 0);
            List<Container> containers = new ContainerManager(activity).getContainers();
            int[] ids = new int[containers.size()];
            File[] desktops = new File[containers.size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = containers.get(i).id;
                desktops[i] = containers.get(i).getDesktopDir();
            }
            int at = owner(desktops, new File(path));
            if (at < 0) return;
            File file = new File(desktops[at], new File(path).getName());
            if (ids[at] == named && file.getPath().equals(path)) return;
            Log.i(TAG, "game starts in container " + ids[at] + " (named " + named + "), file " + file);
            intent.putExtra("container_id", ids[at]);
            intent.putExtra("shortcut_path", file.getPath());
        } catch (RuntimeException e) {
            Log.w(TAG, "start container not checked", e);
        }
    }

    /**
     * The index of the desktop that holds {@code file}; when the file was on a desktop and is gone, of the one with a
     * file of its name; -1 when none does, or the file was on no desktop.
     */
    static int owner(File[] desktops, File file) {
        File parent = file.getAbsoluteFile().getParentFile();
        int home = -1;
        for (int i = 0; i < desktops.length && home < 0; i++) {
            if (desktops[i] != null && desktops[i].getAbsoluteFile().equals(parent)) home = i;
        }
        if (home < 0 || file.exists()) return home;
        for (int i = 0; i < desktops.length; i++) {
            if (i != home && desktops[i] != null && new File(desktops[i], file.getName()).isFile()) return i;
        }
        return -1;
    }
}
