/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

/** A runner's native crash in words: mkxp-z's SIGSEGV of 14/10/2026, as Android's tombstone.proto stores it. */
public class AgvnTombstoneTest {
    /** A protocol buffer, written field by field. */
    private static final class Pb {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Pb varint(long v) {
            while ((v & ~0x7fL) != 0) {
                out.write((int) ((v & 0x7f) | 0x80));
                v >>>= 7;
            }
            out.write((int) v);
            return this;
        }

        Pb num(int field, long v) {
            return varint(field << 3).varint(v);
        }

        Pb bytes(int field, byte[] b) {
            varint(field << 3 | 2).varint(b.length);
            out.write(b, 0, b.length);
            return this;
        }

        Pb str(int field, String s) {
            return bytes(field, s.getBytes(StandardCharsets.UTF_8));
        }

        Pb msg(int field, Pb m) {
            return bytes(field, m.out.toByteArray());
        }

        byte[] done() {
            return out.toByteArray();
        }
    }

    private static Pb frame(long relPc, String file, String function, long offset) {
        Pb f = new Pb().num(1, relPc).num(2, 0x7000000000L + relPc).num(3, 0x7fff0000L);
        if (function != null) f.str(4, function).num(5, offset);
        return f.str(6, file).num(7, 0).str(8, "abcd");
    }

    private static Pb thread(int id, String name, Pb... frames) {
        Pb t = new Pb().num(1, id).str(2, name).msg(3, new Pb().str(1, "x0").num(2, 1));
        for (Pb f : frames) t.msg(4, f);
        return t;
    }

    /** mkxp-z 0.1.41: "app_process" read as a PHYSFS_AndroidInit*, on thread "rgss". */
    private static byte[] mkxpz() {
        Pb signal = new Pb().num(1, 11).str(2, "SIGSEGV").num(3, 1).str(4, "SEGV_MAPERR").num(8, 1).num(9, 0x636f72705f707061L);
        return new Pb().num(1, 3).str(2, "Xiaomi/…").num(5, 14180).num(6, 16578).num(7, 10321).str(9, "com.agvn.player:rgss")
                .msg(10, signal)
                .msg(16, new Pb().num(1, 14180).msg(2, thread(14180, "agvn.player:rgss", frame(0x5f00, "libc.so", "epoll_wait", 8))))
                .msg(16, new Pb().num(1, 16578).msg(2, thread(16578, "rgss", frame(0xc55c24, "/data/app/~~x/lib/arm64/libmkxp-z.so", null, 0),
                        frame(0xc5102c, "/data/app/~~x/lib/arm64/libmkxp-z.so", null, 0))))
                .msg(17, new Pb().num(1, 0x1000).num(2, 0x2000).str(7, "/system/lib64/libc.so"))
                .done();
    }

    @Test
    public void readsTheSignalAndTheCrashingThread() {
        String text = AgvnTombstone.summary(mkxpz());
        assertEquals("signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x636f72705f707061\n\nthread \"rgss\":\n"
                + "#00 pc 0000000000c55c24  /data/app/~~x/lib/arm64/libmkxp-z.so\n"
                + "#01 pc 0000000000c5102c  /data/app/~~x/lib/arm64/libmkxp-z.so", text);
    }

    @Test
    public void abortMessagesCausesAndFunctionNames() {
        Pb signal = new Pb().num(1, 6).str(2, "SIGABRT").num(3, -6).str(4, "SI_TKILL");
        byte[] b = new Pb().num(6, 7).msg(10, signal).str(14, "Check failed: x").msg(15, new Pb().str(1, "null pointer dereference"))
                .msg(16, new Pb().num(1, 7).msg(2, thread(7, "SDLThread", frame(0x1234, "libSDL2.so", "SDL_RunThread", 36)))).done();
        String text = AgvnTombstone.summary(b);
        assertTrue(text, text.startsWith("signal 6 (SIGABRT), code -6 (SI_TKILL)\nAbort message: 'Check failed: x'\n"
                + "Cause: null pointer dereference"));
        assertTrue(text, text.endsWith("#00 pc 0000000000001234  libSDL2.so (SDL_RunThread+36)"));
    }

    @Test
    public void aCutOffTombstoneKeepsWhatCameBefore() {
        byte[] whole = mkxpz();
        String text = AgvnTombstone.summary(Arrays.copyOf(whole, whole.length - 60));
        assertTrue(text, text.startsWith("signal 11 (SIGSEGV)"));
        assertEquals("", AgvnTombstone.summary(new byte[0]));
        assertEquals("", AgvnTombstone.summary(new byte[]{(byte) 0xff, (byte) 0xff}));
    }

    @Test
    public void readsTheStreamPlainOrGzipped() throws IOException {
        assertTrue(AgvnTombstone.read(new ByteArrayInputStream(mkxpz())).contains("thread \"rgss\""));
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(gz)) {
            out.write(mkxpz());
        }
        assertTrue(AgvnTombstone.read(new ByteArrayInputStream(gz.toByteArray())).contains("SEGV_MAPERR"));
        assertNull(AgvnTombstone.read(new ByteArrayInputStream(new byte[0])));
    }
}
