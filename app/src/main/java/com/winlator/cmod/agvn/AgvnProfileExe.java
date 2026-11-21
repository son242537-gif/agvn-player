/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.container.Shortcut;

import java.io.File;

/**
 * The "exe" of agvn-profile.json, followed after the game was added. The entry's command is written once, at import,
 * so a profile edited later to name the game's own exe was not heard: Oppai Spy Gakuen went on starting BootStrap.exe,
 * the maker's start menu, with "exe": "hspy.exe" in its profile (POCO, 21/11/2026). At each launch the profile's exe,
 * when it is a file beside the one the entry starts, is started instead ({@link #EXTRA}, read by {@link AgvnExeMain}).
 * An exe in another folder needs the game added again: only the name in the command can change. Pure Java.
 */
public final class AgvnProfileExe {
    /** Shortcut extra: the name of the exe the profile asks for, beside the entry's own; none when they agree. */
    static final String EXTRA = "agvnProfileExe";

    private AgvnProfileExe() {}

    /**
     * What {@link #EXTRA} should hold for profile exe {@code asked} (relative to {@code gameDir}) and the exe the entry
     * starts: its name when it is another file in the same folder, "" when they agree or nothing is asked, null when
     * it cannot be followed (missing, outside the folder, or in another folder than the entry's exe).
     */
    static String follow(String asked, File gameDir, File entryExe) {
        String exe = asked == null ? "" : asked.trim().replace('\\', '/');
        if (exe.isEmpty() || gameDir == null || entryExe == null) return "";
        if (exe.startsWith("/") || exe.contains("..") || exe.matches("^[A-Za-z]:.*") || !exe.toLowerCase().endsWith(".exe"))
            return null;
        File file = new File(gameDir, exe);
        if (!file.isFile() || !AgvnProfileValidator.isInside(file, gameDir)) return null;
        if (file.getAbsoluteFile().equals(entryExe.getAbsoluteFile())) return "";
        File dir = entryExe.getAbsoluteFile().getParentFile();
        return dir != null && dir.equals(file.getAbsoluteFile().getParentFile()) ? file.getName() : null;
    }

    /**
     * Before a launch: the entry follows its profile's exe. Returns what to say in the session's events, "" when the
     * entry changed with nothing to say, or null when nothing changed. The caller saves the shortcut.
     */
    static String atLaunch(Shortcut s, AgvnProfile p, File gameDir) {
        if (GameExeResolver.Engine.UNREAL.name().equals(s.getExtra(AgvnGameImporter.EXTRA_ENGINE))) return null;
        File entry = AgvnHtmlGame.exeIn(s.file);
        String to = follow(p.exe, gameDir, entry), now = s.getExtra(EXTRA);
        if (to == null) {
            return now.isEmpty() ? null : clear(s);
        }
        if (to.equals(now)) return null;
        s.putExtra(EXTRA, to.isEmpty() ? null : to);
        return to.isEmpty() ? "" : "ghi exe = " + p.exe + ", nay chạy " + to + " thay cho " + entry.getName();
    }

    private static String clear(Shortcut s) {
        s.putExtra(EXTRA, null);
        return "";
    }
}
