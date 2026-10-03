/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.XrActivity;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;

import java.io.File;

/** Starts a library game again after a fix, the way the library starts it (ShortcutsFragment.runFromShortcut). */
final class AgvnRelaunch {
    private AgvnRelaunch() {}

    static void start(Activity a, Shortcut s) {
        s.putExtra("lastRunAt", String.valueOf(System.currentTimeMillis()));
        s.saveData();
        if (XrActivity.isEnabled(a)) {
            XrActivity.openIntent(a, s.container.id, s.file.getPath());
            return;
        }
        Intent intent = new Intent(a, XServerDisplayActivity.class);
        intent.putExtra("container_id", s.container.id);
        intent.putExtra("shortcut_path", s.file.getPath());
        intent.putExtra("shortcut_name", s.name);
        intent.putExtra("disableXinput", s.getExtra("disableXinput", "0"));
        intent.putExtra("native_rendering", s.getRendererNative());
        PreLaunchCheck.run(a, s, () -> a.startActivity(intent));
    }

    /** The game {@code path} names in container {@code containerId}, or null when either is gone. */
    static Shortcut find(Context ctx, int containerId, String path) {
        try {
            Container container = new ContainerManager(ctx).getContainerById(containerId);
            File file = new File(path);
            return container != null && file.isFile() ? new Shortcut(container, file) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
