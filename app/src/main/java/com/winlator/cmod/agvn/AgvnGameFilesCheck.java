/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Before a library game starts: tells the player when its exe, or an exe or DLL next to it or in the game folder, was
 * copied or unpacked only partly (AgvnPeCheck). Wine cannot load such a file, so the game would close at once on a
 * black screen with nothing said. The player can still start it. The files are read off the main thread.
 */
final class AgvnGameFilesCheck {
    private static final String TAG = "AGVN";
    private static final int LISTED = 5;
    private static final Locale VI = Locale.forLanguageTag("vi-VN");

    private AgvnGameFilesCheck() {}

    static void run(Activity activity, Shortcut shortcut, Runnable next) {
        if (AgvnHtmlGame.isLight(shortcut.getExtra(AgvnHtmlGame.EXTRA_RUNNER))) {
            next.run(); // "Chạy nhẹ" runs on Android itself, not in Wine
            return;
        }
        String gameDirPath = shortcut.getExtra(AgvnGameImporter.EXTRA_GAME_DIR);
        File gameDir = gameDirPath.isEmpty() ? null : new File(gameDirPath);
        new Thread(() -> {
            List<AgvnPeCheck.Broken> broken = Collections.emptyList();
            try {
                String path = AgvnExeRedirect.toUnixPath(shortcut.path, shortcut.container);
                if (path != null) {
                    File exe = new File(path);
                    File launched = AgvnExeRedirect.redirectUnrealBootstrap(exe);
                    broken = AgvnPeCheck.scan(launched, launched.getParentFile(), exe.getParentFile(), gameDir);
                }
            } catch (Throwable t) {
                Log.w(TAG, "game file check failed", t); // never keeps a game from starting
            }
            List<AgvnPeCheck.Broken> found = broken;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (found.isEmpty()) next.run();
                else ask(activity, found, gameDir, next);
            });
        }, "AgvnGameFilesCheck").start();
    }

    private static void ask(Activity activity, List<AgvnPeCheck.Broken> broken, File gameDir, Runnable next) {
        AgvnPeCheck.Broken first = broken.get(0);
        Log.w(TAG, "pre-launch: " + broken.size() + " cut-short game file(s); " + first.file + " has " + first.size
                + " of " + first.needed + " bytes");
        new AlertDialog.Builder(activity)
                .setTitle(R.string.agvn_broken_files_title)
                .setMessage(activity.getString(R.string.agvn_broken_files_message, list(activity, broken, gameDir)))
                .setPositiveButton(R.string.agvn_close, null)
                .setNeutralButton(R.string.agvn_broken_files_open_anyway, (d, w) -> next.run())
                .show();
    }

    static String list(Context ctx, List<AgvnPeCheck.Broken> broken, File gameDir) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(broken.size(), LISTED); i++) {
            AgvnPeCheck.Broken b = broken.get(i);
            if (sb.length() > 0) sb.append('\n');
            String name = shown(b.file, gameDir);
            sb.append(b.size == 0 ? ctx.getString(R.string.agvn_broken_files_empty, name)
                    : ctx.getString(R.string.agvn_broken_files_line, name, size(b.size), size(b.needed)));
        }
        if (broken.size() > LISTED) sb.append('\n').append(ctx.getString(R.string.agvn_broken_files_more, broken.size() - LISTED));
        return sb.toString();
    }

    /** The path inside the game folder ("Game/Binaries/Win64/x.dll"), else the file name. */
    static String shown(File file, File gameDir) {
        if (gameDir != null) {
            String dir = AgvnGameScanner.canonical(gameDir) + File.separator;
            String path = AgvnGameScanner.canonical(file);
            if (path.startsWith(dir)) return path.substring(dir.length()).replace(File.separatorChar, '/');
        }
        return file.getName();
    }

    /** "25,6 MB" or "340 KB". */
    static String size(long bytes) {
        if (bytes >= 1L << 20) return String.format(VI, "%.1f MB", bytes / (double) (1L << 20));
        return String.format(VI, "%d KB", Math.max(1, (bytes + 512) / 1024));
    }
}
