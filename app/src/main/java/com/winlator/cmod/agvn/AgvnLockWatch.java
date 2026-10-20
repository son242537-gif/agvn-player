/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A game whose threads wait on each other for good (a deadlock), from Wine's own report: a thread that waits more
 * than 5 s for a critical section prints "RtlpWaitForCriticalSection section … wait timed out in thread …, blocked by
 * …, retrying (60 sec)" (err:sync, which the app keeps on with the log off), then again every 65 s while it waits.
 * Its second report means it has waited more than a minute: that is no loading, the game is stuck. Ticket of
 * 21/10/2026: a Unity 2022.3 IL2CPP game froze for good while loading a scene, on Proton 9 arm64ec (FEX), its main
 * thread waiting on a lock a threadpool worker had just taken and never left; GameHub ran the same game through.
 * One note per lock and session, kept in Wine's tail ({@link AgvnWineTail}) for "Tự sửa lỗi" (game-problems.json
 * "deadlock").
 */
final class AgvnLockWatch {
    /** The note's start, which game-problems.json reads. */
    static final String NOTE = "[AGVN] Khoá chết:";
    private static final Pattern WAIT = Pattern.compile(
            "RtlpWaitForCriticalSection section (\\S+) .*wait timed out in thread (\\S+), blocked by (\\S+?),");

    private final Map<String, Integer> reports = new HashMap<>();
    private final Set<String> told = new HashSet<>();

    /** The note for a lock {@code line} shows a thread stuck on, once per lock; null for any other line. */
    String line(String line) {
        if (!line.contains("RtlpWaitForCriticalSection")) return null;
        Matcher m = WAIT.matcher(line);
        if (!m.find()) return null;
        String section = m.group(1), waiter = m.group(2);
        Integer before = reports.get(section + "/" + waiter);
        int count = before == null ? 1 : before + 1;
        reports.put(section + "/" + waiter, count);
        if (count < 2 || !told.add(section)) return null;
        return NOTE + " luồng " + waiter + " chờ khoá " + section + " hơn 1 phút, khoá do luồng " + m.group(3) + " giữ";
    }

    void reset() {
        reports.clear();
        told.clear();
    }
}
