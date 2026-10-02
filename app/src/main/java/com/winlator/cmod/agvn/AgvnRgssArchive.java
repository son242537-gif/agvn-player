/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads files out of an RPG Maker XP/VX/VX Ace archive (Game.rgssad, Game.rgss2a, Game.rgss3a) the way mkxp-z does
 * (src/crypto/rgssad.cpp): names and sizes are XOR-ed with a running key, and each file with a key that steps
 * key = key * 7 + 3 every 4 bytes. The library uses it for the game's own title picture. Pure Java (JVM-testable).
 */
final class AgvnRgssArchive {
    static final class Entry {
        /** Path inside the archive with '/' separators, e.g. "Graphics/Titles1/Book.png". */
        final String name;
        final long offset;
        final long size;
        final int key;

        Entry(String name, long offset, long size, int key) {
            this.name = name;
            this.offset = offset;
            this.size = size;
            this.key = key;
        }
    }

    private static final int KEY_V1 = 0xDEADCAFE;
    private static final int MAX_ENTRIES = 200_000;
    private static final int MAX_NAME = 512;

    private AgvnRgssArchive() {}

    /** The files in {@code archive}, in archive order; empty when it is not an RGSS archive or cannot be read. */
    static List<Entry> list(File archive) {
        List<Entry> out = new ArrayList<>();
        try (RandomAccessFile f = new RandomAccessFile(archive, "r")) {
            byte[] header = new byte[8];
            f.readFully(header);
            if (!new String(header, 0, 7, StandardCharsets.ISO_8859_1).equals("RGSSAD\0")) return out;
            if (header[7] == 1) listV1(f, out);
            else if (header[7] == 3) listV3(f, out);
        } catch (IOException | RuntimeException e) {
            // a damaged or cut archive: keep what was read
        }
        return out;
    }

    /** XP (.rgssad) and VX (.rgss2a): entries one after the other, the key runs on through names and sizes. */
    private static void listV1(RandomAccessFile f, List<Entry> out) throws IOException {
        int key = KEY_V1;
        long length = f.length();
        while (f.getFilePointer() + 4 <= length && out.size() < MAX_ENTRIES) {
            int nameLen = readInt(f) ^ key;
            key = next(key);
            if (nameLen <= 0 || nameLen > MAX_NAME) return;
            byte[] name = new byte[nameLen];
            f.readFully(name);
            for (int i = 0; i < nameLen; i++) {
                name[i] ^= (byte) key;
                key = next(key);
            }
            long size = (readInt(f) ^ key) & 0xFFFFFFFFL;
            key = next(key);
            long offset = f.getFilePointer();
            if (offset + size > length) return;
            out.add(new Entry(clean(name), offset, size, key));
            f.seek(offset + size);
        }
    }

    /** VX Ace (.rgss3a): a table of offset, size, key and name, XOR-ed with one key read from the header. */
    private static void listV3(RandomAccessFile f, List<Entry> out) throws IOException {
        int base = readInt(f) * 9 + 3;
        long length = f.length();
        while (out.size() < MAX_ENTRIES) {
            long offset = (readInt(f) ^ base) & 0xFFFFFFFFL;
            if (offset == 0) return;
            long size = (readInt(f) ^ base) & 0xFFFFFFFFL;
            int key = readInt(f) ^ base;
            int nameLen = readInt(f) ^ base;
            if (nameLen <= 0 || nameLen > MAX_NAME || offset + size > length) return;
            byte[] name = new byte[nameLen];
            f.readFully(name);
            for (int i = 0; i < nameLen; i++) name[i] ^= (byte) (base >>> (8 * (i % 4)));
            out.add(new Entry(clean(name), offset, size, key));
        }
    }

    /** The decrypted bytes of {@code e}; null when it is larger than {@code maxBytes} or cannot be read. */
    static byte[] read(File archive, Entry e, int maxBytes) {
        if (e.size > maxBytes) return null;
        byte[] data = new byte[(int) e.size];
        try (RandomAccessFile f = new RandomAccessFile(archive, "r")) {
            f.seek(e.offset);
            f.readFully(data);
        } catch (IOException ex) {
            return null;
        }
        decrypt(data, e.key);
        return data;
    }

    /** XOR with the key stream: each 4 bytes (little-endian) with the key, then key = key * 7 + 3. */
    static void decrypt(byte[] data, int key) {
        for (int i = 0; i < data.length; i += 4) {
            for (int j = 0; j < 4 && i + j < data.length; j++) data[i + j] ^= (byte) (key >>> (8 * j));
            key = next(key);
        }
    }

    private static int next(int key) {
        return key * 7 + 3;
    }

    private static int readInt(RandomAccessFile f) throws IOException {
        byte[] b = new byte[4];
        f.readFully(b);
        return (b[0] & 0xFF) | (b[1] & 0xFF) << 8 | (b[2] & 0xFF) << 16 | (b[3] & 0xFF) << 24;
    }

    private static String clean(byte[] name) {
        return new String(name, StandardCharsets.UTF_8).replace('\\', '/');
    }
}
