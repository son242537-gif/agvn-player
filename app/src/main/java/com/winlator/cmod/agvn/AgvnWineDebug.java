/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * WINEDEBUG from "Bật debug Wine" and "Kênh debug Wine". Upstream wrote "+name" for every picked name. For the message
 * classes err, warn and fixme (the default pick), "+warn" names a channel that does not exist, so Wine logged only its
 * default errors and fixmes, never a warning such as Media Foundation's "Transform failed to process output". A class
 * is written "warn+all" instead, which turns it on for every channel.
 *
 * <p>With warnings on for every channel, three channels keep their errors but lose their warnings (unless picked by
 * name; Wine reads the list in order, so these come last):
 * <ul>
 *   <li>heap: a heap warning makes Wine check every heap and fill freed memory with 0xfeeefeee (dlls/ntdll/heap.c,
 *   heap_set_debug_flags). A game that reads memory it has just freed, as Ren'Py 7's renpysound.pyd does, then
 *   crashes, but only while the log is on.</li>
 *   <li>file and font: Ren'Py looks for each image in several folders and every miss is a warning, 98% of a log
 *   written line by line while the game loads.</li>
 * </ul>
 */
public final class AgvnWineDebug {
    private static final String[] CLASSES = {"err", "warn", "fixme"};
    private static final String[] QUIET = {"heap", "file", "font"};
    /**
     * The text of every message box a game shows (dlls/user32/msgbox.c traces it), whatever the log setting: a game's
     * error box ("Unable to initialize video driver", a Vulkan driver crash Wine turned into "Assertion failed!") is
     * often all it says. One line per box.
     */
    static final String MESSAGE_BOXES = "trace+msgbox";
    /**
     * With the log off, Wine still prints the errors "Tự sửa lỗi" reads (AgvnDoctor): a DLL the game needs and cannot
     * load (module), a .NET game without Wine Mono (mscoree), and error boxes. They print only when that goes wrong.
     */
    static final String QUIET_SPEC = "-all,err+module,err+mscoree," + MESSAGE_BOXES;

    private AgvnWineDebug() {}

    public static String spec(boolean enabled, String channels) {
        List<String> out = new ArrayList<>();
        Set<String> picked = new HashSet<>();
        if (enabled && channels != null) {
            for (String name : channels.split(",")) {
                name = name.trim();
                if (name.isEmpty()) continue;
                picked.add(name);
                out.add(isClass(name) ? name + "+all" : "+" + name);
            }
        }
        if (out.isEmpty()) return QUIET_SPEC;
        if (picked.contains("warn")) for (String quiet : QUIET) if (!picked.contains(quiet)) out.add("warn-" + quiet);
        if (!picked.contains("msgbox")) out.add(MESSAGE_BOXES);
        return String.join(",", out);
    }

    private static boolean isClass(String name) {
        for (String c : CLASSES) if (c.equals(name)) return true;
        return false;
    }
}
