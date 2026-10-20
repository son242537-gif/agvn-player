/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * The crash of a "Chạy nhẹ" runner's process in words, from the tombstone Android keeps (Android 12+,
 * ApplicationExitInfo.getTraceInputStream: system/core/debuggerd/proto/tombstone.proto). The lines read as in a
 * tombstone text: the signal and its fault address, the abort message and causes, then the crashing thread's frames.
 * mkxp-z 0.1.41 died this way in every RPG Maker game ("app_process" read as a pointer, 14/10/2026), and nothing told
 * the player or "Gửi nhật ký" why. Pure Java (JVM-testable).
 */
final class AgvnTombstone {
    /** The threads come before the memory maps and the logcat, which can make the file big. */
    static final int MAX_BYTES = 8 << 20;
    static final int FRAMES = 16;

    private AgvnTombstone() {}

    /** The tombstone's lines, or null when there are none to read. Never throws. */
    static String read(InputStream in) {
        try (InputStream raw = new BufferedInputStream(in)) {
            raw.mark(2);
            boolean gz = raw.read() == 0x1f && raw.read() == 0x8b;
            raw.reset();
            InputStream src = gz ? new GZIPInputStream(raw) : raw;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            for (int n; out.size() < MAX_BYTES && (n = src.read(buf)) > 0; ) out.write(buf, 0, n);
            String text = summary(out.toByteArray());
            return text.isEmpty() ? null : text;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** {@link #read} of the bytes; a cut-off tombstone gives what came before the cut. */
    static String summary(byte[] b) {
        StringBuilder out = new StringBuilder();
        long tid = -1;
        int[] crashing = null, first = null;
        Reader r = new Reader(b, 0, b.length);
        try {
            while (r.more()) {
                int field = r.tag();
                if (field == 6 && r.wire == 0) tid = r.varint();
                else if (field == 10 && r.wire == 2) out.append(signal(r.message())).append('\n');
                else if (field == 14 && r.wire == 2) {
                    String abort = r.string();
                    if (!abort.isEmpty()) out.append("Abort message: '").append(abort).append("'\n");
                } else if (field == 15 && r.wire == 2) {
                    Reader cause = r.message();
                    while (cause.more()) {
                        if (cause.tag() == 1 && cause.wire == 2) out.append("Cause: ").append(cause.string()).append('\n');
                        else cause.skip();
                    }
                } else if (field == 16 && r.wire == 2) { // map<uint32, Thread>: key 1, value 2
                    Reader entry = r.message();
                    long key = -1;
                    int[] value = null;
                    while (entry.more()) {
                        int f = entry.tag();
                        if (f == 1 && entry.wire == 0) key = entry.varint();
                        else if (f == 2 && entry.wire == 2) value = entry.range();
                        else entry.skip();
                    }
                    if (value != null && first == null) first = value;
                    if (value != null && key == tid) crashing = value;
                } else r.skip();
            }
        } catch (IllegalStateException cut) {
            // the fields before the cut stay
        }
        int[] thread = crashing != null ? crashing : first;
        try {
            if (thread != null) thread(new Reader(b, thread[0], thread[1]), out);
        } catch (IllegalStateException cut) {
            // the frames before the cut stay
        }
        return out.toString().trim();
    }

    /** "signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x…". */
    private static String signal(Reader r) {
        long number = 0, code = 0, fault = 0;
        String name = "", codeName = "";
        boolean hasFault = false;
        while (r.more()) {
            int f = r.tag();
            if (f == 1 && r.wire == 0) number = r.varint();
            else if (f == 2 && r.wire == 2) name = r.string();
            else if (f == 3 && r.wire == 0) code = r.varint();
            else if (f == 4 && r.wire == 2) codeName = r.string();
            else if (f == 8 && r.wire == 0) hasFault = r.varint() != 0;
            else if (f == 9 && r.wire == 0) fault = r.varint();
            else r.skip();
        }
        return "signal " + (int) number + " (" + name + "), code " + (int) code + " (" + codeName + ")"
                + (hasFault ? ", fault addr 0x" + Long.toHexString(fault) : "");
    }

    /** The thread's name, then its first {@link #FRAMES} frames: "#00 pc 0000000000c55c24  libmkxp-z.so (fn+36)". */
    private static void thread(Reader r, StringBuilder out) {
        int frames = 0;
        while (r.more()) {
            int f = r.tag();
            if (f == 2 && r.wire == 2) out.append("\nthread \"").append(r.string()).append("\":");
            else if (f == 4 && r.wire == 2 && frames < FRAMES) out.append('\n').append(frame(frames++, r.message()));
            else r.skip();
        }
    }

    private static String frame(int index, Reader r) {
        long relPc = 0, offset = 0;
        String function = "", file = "";
        while (r.more()) {
            int f = r.tag();
            if (f == 1 && r.wire == 0) relPc = r.varint();
            else if (f == 4 && r.wire == 2) function = r.string();
            else if (f == 5 && r.wire == 0) offset = r.varint();
            else if (f == 6 && r.wire == 2) file = r.string();
            else r.skip();
        }
        return String.format(Locale.ROOT, "#%02d pc %016x  %s", index, relPc, file)
                + (function.isEmpty() ? "" : " (" + function + "+" + offset + ")");
    }

    /** Protocol buffer fields between {@code pos} and {@code end}; a field cut off ends the reading. */
    private static final class Reader {
        final byte[] b;
        int pos, wire;
        final int end;

        Reader(byte[] b, int pos, int end) {
            this.b = b;
            this.pos = pos;
            this.end = end;
        }

        boolean more() {
            return pos < end;
        }

        int tag() {
            long key = varint();
            wire = (int) (key & 7);
            return (int) (key >>> 3);
        }

        long varint() {
            long v = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                if (pos >= end) throw new IllegalStateException("cut off");
                int x = b[pos++];
                v |= (long) (x & 0x7f) << shift;
                if (x >= 0) return v;
            }
            throw new IllegalStateException("bad varint");
        }

        int[] range() {
            long len = varint();
            if (len < 0 || len > end - pos) {
                pos = end; // the rest was not read
                return null;
            }
            int[] r = {pos, pos + (int) len};
            pos += (int) len;
            return r;
        }

        Reader message() {
            int[] r = range();
            return r == null ? new Reader(b, end, end) : new Reader(b, r[0], r[1]);
        }

        String string() {
            int[] r = range();
            return r == null ? "" : new String(b, r[0], r[1] - r[0], StandardCharsets.UTF_8);
        }

        void skip() {
            switch (wire) {
                case 0: varint(); break;
                case 1: pos += 8; break;
                case 2: range(); break;
                case 5: pos += 4; break;
                default: pos = end; // groups are not used by tombstones
            }
        }
    }
}
