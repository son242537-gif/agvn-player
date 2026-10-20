/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A Godot game's pack, read as Godot reads it (core/io/file_access_pack.cpp), for "Chạy nhẹ" ({@link AgvnGodotLight}):
 * where it starts (a .pck, or the end of the exe with "Embed PCK"), its format and engine version, and its directory:
 * whether files are encrypted, how scripts are kept, and whether the game loads native extensions (GDExtension). Pack
 * formats 2 to 4 (Godot 4.0 to 4.7) have a directory this reads; format 1 (Godot 3) only its header. Pure Java.
 */
final class AgvnGodotPack {
    static final int DIR_ENCRYPTED = 1, FILE_ENCRYPTED = 1, FILE_REMOVAL = 2;
    /** "GDSC": a script kept as GDScript's binary tokens (.gdc) instead of text (.gd). */
    static final int SCRIPT_MAGIC = 0x43534447;
    private static final int MAX_FILES = 1_000_000, MAX_PATH = 4096;

    /** One file of the pack: its path without "res://", where it starts in {@link #file}, its size and flags. */
    static final class Entry {
        final String path;
        final long offset, size;
        final int flags;

        Entry(String path, long offset, long size, int flags) {
            this.path = path;
            this.offset = offset;
            this.size = size;
            this.flags = flags;
        }
    }

    final File file;
    /** Where the pack starts in {@link #file}: 0 for a .pck, after the engine for a pack embedded in an exe. */
    final long start;
    final int format, major, minor, patch, flags;
    /** The directory; empty when it is encrypted or the format has none this reads. */
    final List<Entry> entries;

    private AgvnGodotPack(File file, long start, int format, int major, int minor, int patch, int flags, List<Entry> entries) {
        this.file = file;
        this.start = start;
        this.format = format;
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.flags = flags;
        this.entries = entries;
    }

    /** The pack {@code f} is or ends with, or null when it holds none (or is cut short). */
    static AgvnGodotPack open(File f) {
        if (f == null || !f.isFile()) return null;
        try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
            long start = 0;
            if (in.length() < 32) return null;
            if (read(in, 0, 4).getInt(0) != AgvnGodotFiles.MAGIC) {
                ByteBuffer end = read(in, in.length() - 12, 12);
                if (end.getInt(8) != AgvnGodotFiles.MAGIC) return null;
                long size = end.getLong(0);
                if (size <= 0 || size > in.length() - 12) return null;
                start = in.length() - 12 - size;
                if (read(in, start, 4).getInt(0) != AgvnGodotFiles.MAGIC) return null;
            }
            ByteBuffer h = read(in, start, 40);
            int format = h.getInt(4), major = h.getInt(8), minor = h.getInt(12), patch = h.getInt(16), flags = h.getInt(20);
            if (major < 2 || major > 9) return null;
            List<Entry> entries = Collections.emptyList();
            if (format >= 2 && format <= 4 && (flags & DIR_ENCRYPTED) == 0) {
                long fileBase = h.getLong(24) + (format >= 3 || (flags & 2) != 0 ? start : 0); // PACK_REL_FILEBASE
                long dir = format >= 3 ? start + h.getLong(32) : start + 96; // format 2: right after its 96-byte header
                entries = directory(f, dir, fileBase, in.length());
            }
            return new AgvnGodotPack(f, start, format, major, minor, patch, flags, entries);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** True when the directory or a file is encrypted: only the game's own engine has the key. */
    boolean encrypted() {
        if ((flags & DIR_ENCRYPTED) != 0) return true;
        for (Entry e : entries) if ((e.flags & FILE_ENCRYPTED) != 0) return true;
        return false;
    }

    /** True when the game loads native extensions (GDExtension): libraries built for Windows, not for Android. */
    boolean extensions() {
        Entry list = find(".godot/extension_list.cfg");
        return list != null && list.size > 0;
    }

    /** True for a Godot .NET (C#) game: Godot for Android here has no C#. */
    boolean csharp() {
        for (Entry e : entries) if (e.path.toLowerCase(Locale.ROOT).endsWith(".cs")) return true;
        return false;
    }

    /**
     * The version of GDScript's binary tokens the scripts are kept in (100: Godot 4.3 and 4.4, 101: from 4.5), 0 when
     * they are text (Godot 4.0 to 4.2, or exported as text), -1 when no script could be read.
     */
    int scriptTokens() {
        boolean text = false;
        for (Entry e : entries) {
            String p = e.path.toLowerCase(Locale.ROOT);
            if (p.endsWith(".gd")) text = true;
            if (!p.endsWith(".gdc") || e.size < 8) continue;
            try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
                ByteBuffer b = read(in, e.offset, 8);
                return b.getInt(0) == SCRIPT_MAGIC ? b.getInt(4) : -1;
            } catch (IOException | RuntimeException ex) {
                return -1;
            }
        }
        return text ? 0 : -1;
    }

    Entry find(String path) {
        for (Entry e : entries) if (e.path.equals(path)) return e;
        return null;
    }

    private static List<Entry> directory(File f, long dir, long fileBase, long length) throws IOException {
        List<Entry> out = new ArrayList<>();
        try (InputStream in = new BufferedInputStream(new FileInputStream(f), 1 << 16)) {
            skipFully(in, dir);
            int count = (int) readInt(in);
            if (count < 0 || count > MAX_FILES) throw new IOException("pack directory of " + count + " files");
            for (int i = 0; i < count; i++) {
                long len = readInt(in);
                if (len < 0 || len > MAX_PATH) throw new IOException("pack path of " + len + " bytes");
                byte[] name = new byte[(int) len];
                readFully(in, name);
                int n = name.length;
                while (n > 0 && name[n - 1] == 0) n--; // paths are padded to 4 bytes
                String path = new String(name, 0, n, StandardCharsets.UTF_8);
                if (path.startsWith("res://")) path = path.substring(6); // formats 2 and 3 kept it
                long offset = readLong(in), size = readLong(in);
                skipFully(in, 16); // MD5
                int flags = (int) readInt(in);
                if ((flags & FILE_REMOVAL) != 0) continue;
                if (offset < 0 || size < 0 || fileBase + offset + size > length) throw new IOException("pack file outside the pack");
                out.add(new Entry(path, fileBase + offset, size, flags));
            }
        }
        return out;
    }

    private static ByteBuffer read(RandomAccessFile in, long at, int count) throws IOException {
        if (at < 0 || at + count > in.length()) throw new EOFException();
        byte[] buf = new byte[count];
        in.seek(at);
        in.readFully(buf);
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static long readInt(InputStream in) throws IOException {
        byte[] b = new byte[4];
        readFully(in, b);
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xffffffffL;
    }

    private static long readLong(InputStream in) throws IOException {
        byte[] b = new byte[8];
        readFully(in, b);
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    private static void readFully(InputStream in, byte[] b) throws IOException {
        for (int got = 0, n; got < b.length; got += n) if ((n = in.read(b, got, b.length - got)) < 0) throw new EOFException();
    }

    private static void skipFully(InputStream in, long count) throws IOException {
        while (count > 0) {
            long n = in.skip(count);
            if (n <= 0) {
                if (in.read() < 0) throw new EOFException();
                n = 1;
            }
            count -= n;
        }
    }
}
