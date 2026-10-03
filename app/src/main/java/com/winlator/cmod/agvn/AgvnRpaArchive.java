/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Reads files out of a Ren'Py archive (game/*.rpa), as renpy/loader.py does: the first line ("RPA-3.0 <index offset>
 * <key>", or RPA-2.0 without a key) points at a zlib-compressed pickle ({@link AgvnPickle}) mapping each file name to
 * its offset and length (XOR-ed with the key) and a few first bytes kept in the index. The library uses it for the
 * game's own menu picture and window icon. Pure Java (JVM-testable).
 */
final class AgvnRpaArchive {
    static final class Entry {
        /** Path under game/, e.g. "gui/main_menu.png". */
        final String name;
        final long offset;
        final long length;
        final byte[] prefix;

        Entry(String name, long offset, long length, byte[] prefix) {
            this.name = name;
            this.offset = offset;
            this.length = length;
            this.prefix = prefix;
        }
    }

    private static final int MAX_INDEX = 64 << 20;

    private AgvnRpaArchive() {}

    /** The files in {@code rpa} by name; empty when it is not a Ren'Py archive this reader knows or cannot be read. */
    static Map<String, Entry> index(File rpa) {
        Map<String, Entry> out = new LinkedHashMap<>();
        try (RandomAccessFile f = new RandomAccessFile(rpa, "r")) {
            byte[] head = new byte[(int) Math.min(64, f.length())];
            f.readFully(head);
            String[] parts = new String(head, StandardCharsets.ISO_8859_1).split("\n", 2)[0].trim().split("\\s+");
            long offset, key = 0;
            if (parts[0].equals("RPA-3.0") && parts.length >= 3) {
                offset = Long.parseLong(parts[1], 16);
                key = Long.parseLong(parts[2], 16);
            } else if (parts[0].equals("RPA-2.0") && parts.length >= 2) {
                offset = Long.parseLong(parts[1], 16);
            } else {
                return out;
            }
            long packed = f.length() - offset;
            if (offset <= 0 || packed <= 0 || packed > MAX_INDEX) return out;
            byte[] data = new byte[(int) packed];
            f.seek(offset);
            f.readFully(data);
            Object index = AgvnPickle.load(inflate(data));
            for (Map.Entry<?, ?> e : ((Map<?, ?>) index).entrySet()) {
                List<?> where = (List<?>) ((List<?>) e.getValue()).get(0);
                long at = ((Long) where.get(0)) ^ key, length = ((Long) where.get(1)) ^ key;
                Object start = where.size() > 2 ? where.get(2) : null;
                byte[] prefix = start instanceof byte[] ? (byte[]) start
                        : start instanceof String ? ((String) start).getBytes(StandardCharsets.ISO_8859_1) : new byte[0];
                String name = ((String) e.getKey()).replace('\\', '/');
                if (at >= 0 && length >= prefix.length) out.put(name, new Entry(name, at, length, prefix));
            }
        } catch (IOException | RuntimeException e) {
            // not an archive this reader knows: the game simply has no picture from it
        }
        return out;
    }

    /** The bytes of {@code e} (prefix kept in the index, then the rest from the archive); null when too large or unreadable. */
    static byte[] read(File rpa, Entry e, int maxBytes) {
        if (e.length > maxBytes) return null;
        byte[] data = new byte[(int) e.length];
        System.arraycopy(e.prefix, 0, data, 0, e.prefix.length);
        try (RandomAccessFile f = new RandomAccessFile(rpa, "r")) {
            f.seek(e.offset);
            f.readFully(data, e.prefix.length, data.length - e.prefix.length);
        } catch (IOException ex) {
            return null;
        }
        return data;
    }

    private static byte[] inflate(byte[] data) throws IOException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(data.length * 4, 1 << 20));
            byte[] buf = new byte[64 * 1024];
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) throw new IOException("index is cut short");
                out.write(buf, 0, n);
                if (out.size() > 4 * MAX_INDEX) throw new IOException("index too large");
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new IOException("index is not zlib data", e);
        } finally {
            inflater.end();
        }
    }
}
