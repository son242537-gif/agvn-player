/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads what 7-Zip writes to a console: the progress it redraws with backspaces (" 42% 17 - folder/file.png"), and
 * everything else, which is kept as text: its errors. Pure Java (JVM-testable).
 */
final class AgvnUnpack7zzConsole implements Runnable {
    interface Progress {
        /** {@code percent}: 0..100; {@code name}: the file being written, or null when the line names none. */
        void at(int percent, String name);
    }

    /** "  0M Scan", " 12%", "100% 4 - Game/www/index.html", "  0% 3 T Game/data.bin", "  0% 1 Open" */
    private static final Pattern DRAWN = Pattern.compile("^\\s*\\d+[%M](\\s.*)?$");
    private static final Pattern PERCENT = Pattern.compile("^\\s*(\\d{1,3})%(?:\\s+\\d+)?(?:\\s+[-+TU.=]\\s(.+))?$");
    /** Errors beyond this are of no use to anyone. */
    private static final int KEPT_CHARS = 16 << 10;

    private final InputStream in;
    private final Progress progress;
    private final StringBuilder text = new StringBuilder();
    /** When the first error line came (System.nanoTime), 0 while none has. */
    private volatile long errorAt;

    AgvnUnpack7zzConsole(InputStream in, Progress progress) {
        this.in = in;
        this.progress = progress;
    }

    @Override
    public void run() {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            StringBuilder piece = new StringBuilder();
            char[] buffer = new char[4096];
            for (int n; (n = reader.read(buffer)) > 0; ) {
                for (int i = 0; i < n; i++) {
                    char c = buffer[i];
                    if (c == '\b' || c == '\r' || c == '\n') {
                        take(piece.toString());
                        piece.setLength(0);
                    } else if (piece.length() < 4096) {
                        piece.append(c);
                    }
                }
            }
            take(piece.toString());
        } catch (IOException ignored) {
            // the program ended or was stopped
        }
    }

    /** One piece between backspaces or line ends. */
    void take(String piece) {
        if (piece.trim().isEmpty()) return;
        if (DRAWN.matcher(piece).matches()) {
            Matcher m = PERCENT.matcher(piece);
            if (m.matches() && progress != null) progress.at(Math.min(100, Integer.parseInt(m.group(1))), m.group(2));
            return;
        }
        synchronized (text) {
            if (text.length() < KEPT_CHARS) text.append(piece).append('\n');
        }
        if (errorAt == 0 && piece.startsWith("ERROR") && !AgvnUnpack7zzErrors.harmless(piece)) errorAt = System.nanoTime();
    }

    String text() {
        synchronized (text) {
            return text.toString();
        }
    }

    /** An error line came at least {@code millis} ago: the rest of its message has had time to follow. */
    boolean failedFor(long millis) {
        long at = errorAt;
        return at != 0 && System.nanoTime() - at >= millis * 1_000_000L;
    }
}
