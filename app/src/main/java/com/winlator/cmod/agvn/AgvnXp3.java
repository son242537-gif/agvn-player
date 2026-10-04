/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Reads one file out of a KiriKiri XP3 archive, as krkr2's XP3Archive.cpp does: the "XP3\r\n \n\x1a\x8b\x67\x01" mark,
 * then the offset of the index, raw or zlib-packed. A version 2 archive reaches its index through an empty "cushion"
 * index flagged "continue". The index is a list of "File" chunks; each has "info" (its path) and "segm" (where its
 * bytes are, raw or zlib-packed). An archive the game encrypts gives bytes only its own exe can decode. Pure Java.
 */
final class AgvnXp3 {
    private static final byte[] MARK = {'X', 'P', '3', 0x0d, 0x0a, ' ', 0x0a, 0x1a, (byte) 0x8b, 0x67, 0x01};
    private static final int INDEX_CONTINUE = 0x80, METHOD = 0x07, ZLIB = 1, SEGMENT = 28;
    private static final int FILE = tag("File"), INFO = tag("info"), SEGM = tag("segm");
    /** Bounds against a damaged or foreign file. */
    static final int MAX_INDEX = 32 << 20, MAX_CHAINED = 4;

    /** A file in the archive: its path ("system/Config.tjs") and its segments, {flags, offset, size, packed size}. */
    static final class Entry {
        final String path;
        final long size;
        final List<long[]> segments;

        Entry(String path, long size, List<long[]> segments) {
            this.path = path;
            this.size = size;
            this.segments = segments;
        }
    }

    private AgvnXp3() {}

    /**
     * The files named {@code name} (any case) in any folder of the archive, in index order; empty when it is not an
     * XP3 archive.
     */
    static List<Entry> find(File archive, String name) throws IOException {
        List<Entry> found = new ArrayList<>();
        String wanted = name.toLowerCase(Locale.ROOT);
        try (RandomAccessFile f = new RandomAccessFile(archive, "r")) {
            byte[] mark = new byte[MARK.length];
            if (f.length() < MARK.length + 8) return found;
            f.readFully(mark);
            if (!Arrays.equals(mark, MARK)) return found;
            long next = MARK.length;
            for (int i = 0; i < MAX_CHAINED; i++) {
                f.seek(next);
                long at = readLong(f);
                if (at < MARK.length || at >= f.length()) throw new IOException("index outside the archive");
                f.seek(at);
                int flag = f.read();
                parse(readIndex(f, flag), wanted, found);
                if ((flag & INDEX_CONTINUE) == 0) break;
                next = f.getFilePointer();
            }
        }
        return found;
    }

    /** The bytes of {@code e}, or null when it is larger than {@code max}. */
    static byte[] read(File archive, Entry e, int max) throws IOException {
        if (e.size < 0 || e.size > max) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) e.size);
        try (RandomAccessFile f = new RandomAccessFile(archive, "r")) {
            for (long[] s : e.segments) {
                long offset = s[1], size = s[2], packed = s[3];
                if (offset < 0 || size < 0 || packed < 0 || size > max || packed > max || offset + packed > f.length())
                    throw new IOException("segment outside the archive");
                byte[] data = new byte[(int) packed];
                f.seek(offset);
                f.readFully(data);
                out.write((s[0] & METHOD) == ZLIB ? inflate(data, (int) size) : data);
                if (out.size() > max) return null;
            }
        }
        return out.toByteArray();
    }

    /** {@code size} bytes out of zlib data. */
    static byte[] inflate(byte[] packed, int size) throws IOException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(packed);
            byte[] out = new byte[size];
            int n = 0;
            while (n < size && !inflater.finished()) {
                int got = inflater.inflate(out, n, size - n);
                if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                n += got;
            }
            if (n != size) throw new IOException("zlib data ends early");
            return out;
        } catch (DataFormatException e) {
            throw new IOException(e);
        } finally {
            inflater.end();
        }
    }

    private static byte[] readIndex(RandomAccessFile f, int flag) throws IOException {
        int method = flag & METHOD;
        if (flag < 0 || method > ZLIB) throw new IOException("unknown index method " + flag);
        long packed = method == ZLIB ? readLong(f) : 0, size = readLong(f);
        if (method != ZLIB) packed = size;
        if (size < 0 || size > MAX_INDEX || packed < 0 || packed > f.length() - f.getFilePointer())
            throw new IOException("index too large");
        byte[] data = new byte[(int) packed];
        f.readFully(data);
        return method == ZLIB ? inflate(data, (int) size) : data;
    }

    private static void parse(byte[] index, String wanted, List<Entry> found) {
        ByteBuffer b = ByteBuffer.wrap(index).order(ByteOrder.LITTLE_ENDIAN);
        while (b.remaining() >= 12) {
            int tag = b.getInt();
            long size = b.getLong();
            if (size < 0 || size > b.remaining()) return;
            int end = b.position() + (int) size;
            if (tag == FILE) {
                Entry e = entry(b, end);
                if (e != null && named(e.path, wanted)) found.add(e);
            }
            b.position(end);
        }
    }

    private static Entry entry(ByteBuffer b, int end) {
        String path = null;
        long size = -1;
        List<long[]> segments = new ArrayList<>();
        while (end - b.position() >= 12) {
            int tag = b.getInt();
            long length = b.getLong();
            if (length < 0 || length > end - b.position()) return null;
            int next = b.position() + (int) length;
            if (tag == INFO && length >= 22) {
                b.getInt(); // flags
                size = b.getLong();
                b.getLong(); // size in the archive
                int chars = b.getShort() & 0xffff;
                if (chars * 2L > next - b.position()) return null;
                char[] name = new char[chars];
                for (int i = 0; i < chars; i++) name[i] = b.getChar();
                path = new String(name).replace('\\', '/');
            } else if (tag == SEGM) {
                for (long n = length / SEGMENT; n > 0; n--)
                    segments.add(new long[]{b.getInt() & 0xffffffffL, b.getLong(), b.getLong(), b.getLong()});
            }
            b.position(next);
        }
        return path == null || segments.isEmpty() ? null : new Entry(path, size, segments);
    }

    private static boolean named(String path, String wanted) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.equals(wanted) || p.endsWith("/" + wanted);
    }

    private static long readLong(RandomAccessFile f) throws IOException {
        return Long.reverseBytes(f.readLong());
    }

    private static int tag(String name) {
        return name.charAt(0) | name.charAt(1) << 8 | name.charAt(2) << 16 | name.charAt(3) << 24;
    }
}
