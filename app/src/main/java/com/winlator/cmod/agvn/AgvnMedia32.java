/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.EnvVars;

import java.io.File;

/**
 * Movies of a 32-bit game on Proton 9: read Wine's old way from the start.
 *
 * <p>Proton 9's new media source (wg_source.c) cannot open any movie for a 32-bit program. Wine runs such a program
 * on its 64-bit side through thunks, and the two the new source needs are written wrong in Proton's
 * experimental_9.0 (wg_parser.c): wow64_wg_source_create leaves out file_size, wow64_wg_source_push_data leaves out
 * offset. The 64-bit side then reads the data pointer as 0 and the file's length as the size of the data, and answers
 * the source's handle where the program does not look. I will purify you (Unity 2022.3 IL2CPP x86, POCO F8 Pro,
 * 16/11/2026) showed it: "GStreamer-Base-CRITICAL: gst_type_find_helper_for_data_with_extension: assertion 'data !=
 * NULL' failed", then "wg_source.c:99:detect_caps_from_data: Failed to detect caps for url ...mp4, data 0x0, size
 * 658352" (the whole file), and Unity's "WindowsVideoMedia error 0xc00d36bb ... CreateObjectFromByteStream"; the same
 * with the movie made WebM. The game stayed black after its splash. Steam's Proton never runs these thunks: there a
 * 32-bit game has a 32-bit Wine.
 *
 * <p>The old media source (wg_parser) has every thunk right, as Wine itself uses it, and WINE_OLD_MEDIA_SOURCE=1 picks
 * it ({@link AgvnMediaFoundation}). So a 32-bit game gets it unless the variable is set already: the new way cannot
 * work for it, so no game that plays well loses anything. A 64-bit game keeps the new way. Proton 10 has neither the
 * switch nor these thunks. A movie that still meets the fault (a 64-bit launcher starting a 32-bit game, the variable
 * set to 0) is told by {@link #NO_DATA}, and {@link AgvnMovieWatch}'s bar offers the old way. Pure Java but
 * {@link #applies}.
 */
final class AgvnMedia32 {
    /** What Wine prints (GLib, logs on or off) when its new media source was handed no data. */
    static final String NO_DATA = "gst_type_find_helper_for_data_with_extension: assertion 'data != NULL' failed";
    private static final int I386 = 0x14c;
    private static volatile String note;

    private AgvnMedia32() {}

    /** True when Wine runs {@code exe} as a 32-bit program (a PE32 one that is not .NET "AnyCPU"). */
    static boolean thirtyTwo(File exe) {
        return AgvnPeFacts.machine(exe) == I386 && !AgvnClrHeader.runs64(exe);
    }

    /** True for a 32-bit game on Proton 9: its movies open the old way only. */
    static boolean applies(Shortcut s) {
        if (s == null || s.container == null || AgvnWine10.runs(s.container)) return false;
        String path = AgvnExeRedirect.toUnixPath(s.path, s.container);
        return path != null && thirtyTwo(new File(path));
    }

    /**
     * On the final environment, after the container's and the game's own variables: WINE_OLD_MEDIA_SOURCE=1 for a
     * 32-bit game ({@code game32}), unless the variable is set (a player's "0" keeps the new way).
     */
    static void apply(EnvVars env, boolean game32) {
        if (!game32 || env.has(AgvnMediaFoundation.OLD_MEDIA_SOURCE)) return;
        env.put(AgvnMediaFoundation.OLD_MEDIA_SOURCE, "1");
        note = "Game 32-bit trên Proton 9: đọc phim bằng cách cũ của Wine (WINE_OLD_MEDIA_SOURCE=1), vì cách mới không"
                + " mở được phim nào cho chương trình 32-bit";
    }

    /** The line for the session's events, once; null when nothing was set. */
    static String takeNote() {
        String n = note;
        note = null;
        return n;
    }
}
