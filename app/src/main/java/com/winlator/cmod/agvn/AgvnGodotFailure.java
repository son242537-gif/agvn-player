/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.app.AlertDialog;
import android.util.Log;

import com.winlator.cmod.R;
import com.winlator.cmod.core.FileUtils;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Why Godot ended a "Chạy nhẹ" game before its first frame ({@link AgvnGodotActivity}): the first error in Godot's log
 * of the run (--log-file), such as "Cannot open resource pack" or a script that does not compile; and the dialog that
 * says it and offers "Chạy bằng Windows". Pure Java (JVM-testable) except {@link #ask}.
 */
final class AgvnGodotFailure {
    static final int MAX_LENGTH = 300;

    private AgvnGodotFailure() {}

    /** Tells the player why the game did not open; {@code windows} (null for a game without an exe) or {@code close} next. */
    static void ask(Activity activity, String logPath, Runnable windows, Runnable close) {
        String error = reason(logPath);
        String why = error.isEmpty() ? activity.getString(R.string.agvn_godot_failed_unknown) : error;
        Log.w("AGVN", "Godot ended before its first frame: " + why);
        AgvnLightSession.shown(activity, why); // this dialog offers the fix; the library does not ask again
        AlertDialog.Builder b = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.agvn_godot_failed_title)
                .setMessage(activity.getString(R.string.agvn_godot_failed_message, why))
                .setNegativeButton(R.string.agvn_close, (d, w) -> close.run())
                .setCancelable(false);
        if (windows != null) b.setPositiveButton(R.string.agvn_html_use_windows, (d, w) -> windows.run());
        b.show();
    }

    /** The first error in the log at {@code logPath}, or "" when it has none (or there is no log). */
    static String reason(String logPath) {
        File log = logPath != null ? new File(logPath) : null;
        return reason(log != null && log.isFile() ? FileUtils.readLines(log) : Collections.emptyList());
    }

    /** The first "ERROR:", "SCRIPT ERROR:" or "USER ERROR:" line of Godot's log, without that word, cut short. */
    static String reason(List<String> lines) {
        for (String raw : lines) {
            String line = raw.trim();
            if (!line.startsWith("ERROR:") && !line.startsWith("SCRIPT ERROR:") && !line.startsWith("USER ERROR:")) continue;
            String message = line.substring(line.indexOf(':') + 1).trim();
            if (message.isEmpty()) continue;
            return message.length() > MAX_LENGTH ? message.substring(0, MAX_LENGTH) + "…" : message;
        }
        return "";
    }
}
