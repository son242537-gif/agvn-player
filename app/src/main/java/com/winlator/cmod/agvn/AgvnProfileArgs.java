/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The args of agvn-profile.json, edited after the game was added. "Thêm game" wrote them into the game's arguments
 * once; a game maker who then added args to the profile saw "Tham số chạy: (không có)" in moi-truong.txt, while the env
 * added beside them was taken (I will purify you, 16/11/2026). At each start they are read again with the env
 * ({@link AgvnProfileEnv#atLaunch}): what the profile gives now replaces what it gave before, and the arguments the
 * player or a Doctor fix set stay.
 *
 * <p>What it gave before is kept on the game ({@link #EXTRA}). A game added before that record was kept has none: what
 * the import took is then read from the other profile file (the game folder's and the app's copy held the same args at
 * the import, and one was edited since). With no other file to tell, the arguments are changed only where that cannot
 * undo a choice: the profile's are added to a game that has none. Pure Java but {@link #atLaunch}.
 */
final class AgvnProfileArgs {
    /** Shortcut extra: the arguments the profile gave the game when they were last written ({@link #NONE}: none). */
    static final String EXTRA = "agvnProfileArgs";
    /** Recorded for a profile without args, apart from a game with no record yet (no argument has a bracket). */
    static final String NONE = "()";

    private AgvnProfileArgs() {}

    /** What {@code p} gives the game's arguments. */
    static String given(AgvnProfile p) {
        return String.join(" ", p.args);
    }

    /** {@code given} as {@link #EXTRA} keeps it. */
    static String record(String given) {
        return given.isEmpty() ? NONE : given;
    }

    /**
     * Before the game's settings are read: the game's arguments follow the profile {@code p}, edited last;
     * {@code other} is its other file, or null. Null when nothing was written; else what to tell ("" for a record
     * only). The caller saves the shortcut.
     */
    static String atLaunch(Shortcut s, AgvnProfile p, File other) {
        String now = given(p), recorded = s.getExtra(EXTRA), args = s.getExtra("execArgs");
        if (record(now).equals(recorded)) return null;
        String after = recorded.isEmpty() ? unrecorded(args, argsOf(other), now) : apply(args, before(recorded), now);
        s.putExtra("execArgs", after.isEmpty() ? null : after);
        s.putExtra(EXTRA, record(now));
        if (after.equals(args.trim())) return "";
        return "đổi args từ khi thêm game, nay áp: " + (now.isEmpty() ? "(không có)" : now);
    }

    private static String before(String recorded) {
        return NONE.equals(recorded) ? "" : recorded;
    }

    /** The args of profile {@code file}; null when it is not there or cannot be read. */
    private static String argsOf(File file) {
        try {
            return file != null && file.isFile() ? given(AgvnProfile.parse(FileUtils.readString(file))) : null;
        } catch (AgvnProfileException | RuntimeException e) {
            return null;
        }
    }

    /**
     * {@link #apply} for a game with no record: {@code other}, the args of the profile's other file, are what the
     * import took; null (no such file): the game's arguments stay unless it has none.
     */
    static String unrecorded(String args, String other, String now) {
        String cur = args == null ? "" : args.trim();
        if (other != null) return apply(cur, other, now);
        return cur.isEmpty() ? now.trim() : cur;
    }

    /**
     * {@code args}, the game's arguments, with those the profile gave {@code before} replaced by those it gives
     * {@code now}, where they still stand together; else {@code now} is put first unless it is there. The rest (the
     * player's, a Doctor fix's) stays.
     */
    static String apply(String args, String before, String now) {
        String cur = args == null ? "" : args.trim(), was = before == null ? "" : before.trim();
        String is = now == null ? "" : now.trim();
        if (was.equals(is)) return cur;
        int at = was.isEmpty() ? -1 : whole(cur, was);
        if (at < 0) return is.isEmpty() || whole(cur, is) >= 0 ? cur : cur.isEmpty() ? is : is + " " + cur;
        List<String> parts = new ArrayList<>();
        for (String part : new String[]{cur.substring(0, at).trim(), is, cur.substring(at + was.length()).trim()}) {
            if (!part.isEmpty()) parts.add(part);
        }
        return String.join(" ", parts);
    }

    /** Where {@code part} stands in {@code args} as whole arguments (a space or an end on each side); else -1. */
    static int whole(String args, String part) {
        for (int at = args.indexOf(part); at >= 0; at = args.indexOf(part, at + 1)) {
            int end = at + part.length();
            if ((at == 0 || args.charAt(at - 1) == ' ') && (end == args.length() || args.charAt(end) == ' ')) return at;
        }
        return -1;
    }
}
