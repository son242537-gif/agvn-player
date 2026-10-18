/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Changes a game's extras in its .desktop file without a Container, for a "Chạy nhẹ" game, which runs in a process of
 * its own ({@link AgvnHtmlGame#readExtras} reads them so). Writes the file as Shortcut.saveData does: the
 * [Desktop Entry] lines without blank ones, then the [Extra Data] section, the extras in their order. Pure Java.
 */
final class AgvnDesktopExtras {
    private AgvnDesktopExtras() {}

    /** Sets each of {@code changes} (a null value removes the extra); false when the file cannot be read or written. */
    static boolean put(File desktop, Map<String, String> changes) {
        try {
            String content = new String(Files.readAllBytes(desktop.toPath()), StandardCharsets.UTF_8);
            File tmp = new File(desktop.getPath() + ".tmp");
            Files.write(tmp.toPath(), apply(content, changes).getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(desktop)) { // Windows (the unit tests of a PC build) does not rename over a file
                Files.deleteIfExists(desktop.toPath());
                if (!tmp.renameTo(desktop)) return false;
            }
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** {@code content} with {@code changes} made to its extras. */
    static String apply(String content, Map<String, String> changes) {
        StringBuilder entry = new StringBuilder("[Desktop Entry]\n");
        Map<String, String> extras = new LinkedHashMap<>();
        boolean seenExtras = false, inExtras = false;
        for (String raw : content.split("\r?\n")) {
            String line = raw.trim();
            if (line.startsWith("[") && seenExtras || line.contains("[Extra Data]")) { // a section after it: dropped, as saveData does
                inExtras = line.contains("[Extra Data]");
                seenExtras = true;
                continue;
            }
            if (inExtras) {
                int eq = line.indexOf('=');
                if (eq > 0) extras.put(line.substring(0, eq), line.substring(eq + 1));
            } else if (!seenExtras && !line.isEmpty() && !line.contains("[Desktop Entry]")) {
                entry.append(raw).append('\n');
            }
        }
        for (Map.Entry<String, String> change : changes.entrySet()) {
            if (change.getValue() != null) extras.put(change.getKey(), change.getValue());
            else extras.remove(change.getKey());
        }
        if (extras.isEmpty()) return entry.toString();
        entry.append("\n[Extra Data]\n");
        for (Map.Entry<String, String> extra : extras.entrySet()) entry.append(extra.getKey()).append('=').append(extra.getValue()).append('\n');
        return entry.toString();
    }
}
