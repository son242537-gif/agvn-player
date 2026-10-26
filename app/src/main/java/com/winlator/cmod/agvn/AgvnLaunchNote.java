/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.core.EnvVars;

import java.util.Set;
import java.util.TreeSet;

/**
 * What a game was started with, for moi-truong.txt beside the graphics variables: the variables the container and the
 * game's settings set, as Wine got them, the game's own arguments, and the command Wine ran. A player added
 * --disable-gpu and ELECTRON_ENABLE_LOGGING=1 to an Electron game and could not tell whether they reached Wine: logcat
 * cut its env line near 4,000 characters, and no log had the command (26/10/2026). Pure Java except the command hand-over.
 */
public final class AgvnLaunchNote {
    private static volatile String command;

    private AgvnLaunchNote() {}

    /** The command the game starts with (XServerDisplayActivity, before the session log opens). */
    public static void command(String guestExecutable) {
        command = guestExecutable;
    }

    static String takeCommand() {
        String c = command;
        command = null;
        return c;
    }

    /**
     * The lines after the graphics variables: each variable {@code containerEnv} or {@code gameEnv} names, with the
     * value {@code env} (what Wine got) has for it; {@code execArgs}; {@code command} ("?" when unknown).
     */
    static String text(EnvVars env, String containerEnv, String gameEnv, String execArgs, String command) {
        Set<String> names = new TreeSet<>();
        for (String set : new String[]{containerEnv, gameEnv}) for (String name : new EnvVars(set == null ? "" : set)) names.add(name);
        StringBuilder s = new StringBuilder("\n# Biến môi trường do container và Cài đặt game đặt, như Wine nhận:\n");
        for (String name : names) s.append(name).append('=').append(env.has(name) ? env.get(name) : "(Wine không nhận)").append('\n');
        if (names.isEmpty()) s.append("(không có)\n");
        s.append("# Tham số chạy của game (Cài đặt game): ")
                .append(execArgs == null || execArgs.trim().isEmpty() ? "(không có)" : execArgs.trim()).append('\n');
        return s.append("# Lệnh chạy Wine: ").append(command == null || command.isEmpty() ? "?" : command).append('\n').toString();
    }
}
