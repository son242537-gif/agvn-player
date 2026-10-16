/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Wine's log without its floods. A WMV3 movie Wine could not decode wrote two GStreamer lines over and over, 443,180
 * lines and 67 MB in 4.5 minutes with "Bật debug Wine" on (0.1.20). A line seen again within {@link #FORGET_MS} (its
 * thread, time and addresses aside, {@link #key}) is not written; once a second ({@link #tick}) the log says how many
 * times each came again. Past {@link #MAX_BYTES} nothing more is written (the session's wine-cuoi.txt keeps the
 * end). Pure Java (JVM-testable); not thread-safe, the caller holds a lock.
 */
public final class AgvnLogRepeats {
    /** Lines told apart at once: a flood of up to this many different lines is folded. */
    static final int KEYS = 16;
    /** A line not seen for this long is written in full again. */
    static final long FORGET_MS = 5000;
    static final long MAX_BYTES = 64L << 20;
    static final int EXCERPT = 160;
    /** GStreamer's "0:00:05.123456789 12345 0xb400007a WARN ..." and Wine's "00e8:warn:..." heads. */
    private static final Pattern GST_HEAD = Pattern.compile("^\\d+:\\d{2}:\\d{2}\\.\\d+\\s+\\d+\\s+0x[0-9a-fA-F]+\\s+");
    private static final Pattern WINE_THREAD = Pattern.compile("^[0-9a-fA-F]{4,8}:(?=(?:err|warn|fixme|trace):)");
    private static final Pattern HEX = Pattern.compile("0x[0-9a-fA-F]+");

    /** Per line key: {times again since the last tick, last seen ms}. */
    private final LinkedHashMap<String, long[]> recent = new LinkedHashMap<String, long[]>(KEYS, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, long[]> eldest) {
            return size() > KEYS;
        }
    };
    private final long maxBytes;
    private long bytes;
    private boolean full;

    public AgvnLogRepeats() {
        this(MAX_BYTES);
    }

    AgvnLogRepeats(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    /** The same line, its thread, time and addresses aside. */
    static String key(String line) {
        String s = GST_HEAD.matcher(line).replaceFirst("");
        s = WINE_THREAD.matcher(s).replaceFirst("");
        return HEX.matcher(s).replaceAll("0x…");
    }

    /** What to write for {@code line}: itself, nothing for a line just seen, or the note that the log is full. */
    public List<String> add(String line, long nowMs) {
        List<String> out = new ArrayList<>(1);
        String key = key(line);
        long[] seen = recent.get(key);
        if (seen != null && nowMs - seen[1] <= FORGET_MS) {
            seen[0]++;
            seen[1] = nowMs;
            return out;
        }
        recent.put(key, new long[]{0, nowMs});
        write(out, line);
        return out;
    }

    /** Once a second: how many times each line came again, and lines not seen for {@link #FORGET_MS} forgotten. */
    public List<String> tick(long nowMs) {
        List<String> out = new ArrayList<>();
        for (Iterator<Map.Entry<String, long[]>> it = recent.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, long[]> e = it.next();
            long[] seen = e.getValue();
            if (seen[0] > 0) {
                String text = e.getKey().length() > EXCERPT ? e.getKey().substring(0, EXCERPT) + "…" : e.getKey();
                write(out, "[AGVN] ×" + seen[0] + " nữa: " + text);
                seen[0] = 0;
            } else if (nowMs - seen[1] > FORGET_MS) {
                it.remove();
            }
        }
        return out;
    }

    private void write(List<String> out, String line) {
        if (full) return;
        bytes += line.length() + 1;
        if (bytes <= maxBytes) {
            out.add(line);
            return;
        }
        full = true;
        out.add("[AGVN] Log Wine đã quá " + (maxBytes >> 20) + " MB, phần sau không ghi nữa (wine-cuoi.txt giữ các dòng cuối)");
    }
}
