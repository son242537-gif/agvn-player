/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A TrueType/OpenType font as its tables. DirectWrite knows a font by the family in its "name" table, so a font is
 * offered under another family by a new name table, and several names of one font are written as one collection
 * (.ttc) whose faces share every other table. The copyright and license records are kept: Bitstream Vera's license
 * (DejaVu Sans) lets a font be changed when it no longer carries the names "Bitstream" or "Vera".
 */
final class AgvnFontFile {
    static final int NAME = tag("name"), TTCF = tag("ttcf");
    /** Replaced name IDs: family, style, unique ID, full name, PostScript name, typographic and WWS names. */
    private static final List<Integer> RENAMED = Arrays.asList(1, 2, 3, 4, 6, 16, 17, 21, 22);

    final int version;
    final int[] tags;
    final byte[][] tables;

    private AgvnFontFile(int version, int[] tags, byte[][] tables) {
        this.version = version;
        this.tags = tags;
        this.tables = tables;
    }

    static int tag(String s) {
        return s.charAt(0) << 24 | s.charAt(1) << 16 | s.charAt(2) << 8 | s.charAt(3);
    }

    /** The tables of a font file with one face, in tag order. */
    static AgvnFontFile read(File file) throws IOException {
        byte[] all = Files.readAllBytes(file.toPath());
        ByteBuffer b = ByteBuffer.wrap(all);
        if (all.length < 12 || b.getInt(0) == TTCF) throw new IOException("not a single font: " + file);
        int n = b.getShort(4) & 0xffff;
        int[][] records = new int[n][];
        for (int i = 0; i < n; i++) {
            int record = 12 + 16 * i;
            if (record + 16 > all.length) throw new IOException("a cut table directory: " + file);
            records[i] = new int[]{b.getInt(record), b.getInt(record + 8), b.getInt(record + 12)};
            if (records[i][1] < 0 || records[i][2] < 0 || records[i][1] > all.length - records[i][2])
                throw new IOException("a table outside the file: " + file);
        }
        Arrays.sort(records, (x, y) -> Integer.compareUnsigned(x[0], y[0]));
        int[] tags = new int[n];
        byte[][] tables = new byte[n][];
        for (int i = 0; i < n; i++) {
            tags[i] = records[i][0];
            tables[i] = Arrays.copyOfRange(all, records[i][1], records[i][1] + records[i][2]);
        }
        return new AgvnFontFile(b.getInt(0), tags, tables);
    }

    /** Name {@code id} of face {@code face} of a font file (a collection too), or null when it is not a font. */
    static String name(File file, int face, int id) {
        try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
            long dir = 0;
            if (f.readInt() == TTCF) {
                f.seek(8);
                if (face >= f.readInt()) return null;
                f.seek(12 + 4L * face);
                dir = f.readInt() & 0xffffffffL;
            } else if (face != 0) {
                return null;
            }
            f.seek(dir + 4);
            int n = f.readUnsignedShort();
            for (int i = 0; i < n; i++) {
                f.seek(dir + 12 + 16L * i);
                int t = f.readInt();
                f.readInt();
                long at = f.readInt() & 0xffffffffL;
                int length = f.readInt();
                if (t != NAME) continue;
                if (length < 6 || length > 1 << 20 || at + length > f.length()) return null;
                byte[] table = new byte[length];
                f.seek(at);
                f.readFully(table);
                return nameIn(table, id);
            }
        } catch (IOException | RuntimeException e) {
            // not a font
        }
        return null;
    }

    /** A name of a name table: Windows US English first, then any Windows one, then Macintosh. */
    static String nameIn(byte[] table, int id) {
        ByteBuffer b = ByteBuffer.wrap(table);
        int count = b.getShort(2) & 0xffff, strings = b.getShort(4) & 0xffff, rank = 0;
        String best = null;
        for (int i = 0; i < count && 18 + 12 * i <= table.length; i++) {
            int r = 6 + 12 * i, platform = b.getShort(r) & 0xffff, language = b.getShort(r + 4) & 0xffff;
            int length = b.getShort(r + 8) & 0xffff, at = strings + (b.getShort(r + 10) & 0xffff);
            int score = platform == 3 ? (language == 0x409 ? 3 : 2) : platform == 1 ? 1 : 0;
            if ((b.getShort(r + 6) & 0xffff) != id || score <= rank || at + length > table.length) continue;
            best = new String(table, at, length,
                    platform == 3 ? StandardCharsets.UTF_16BE : StandardCharsets.ISO_8859_1);
            rank = score;
        }
        return best;
    }

    /** A name table calling this font {@code family} in {@code style} ("Regular", "Bold"); other records stay. */
    byte[] renamed(String family, String style) {
        byte[] old = tables[index(NAME)];
        ByteBuffer b = ByteBuffer.wrap(old);
        int count = b.getShort(2) & 0xffff, strings = b.getShort(4) & 0xffff;
        List<int[]> records = new ArrayList<>();
        List<byte[]> texts = new ArrayList<>();
        for (int i = 0; i < count && 18 + 12 * i <= old.length; i++) {
            int r = 6 + 12 * i, length = b.getShort(r + 8) & 0xffff, at = strings + (b.getShort(r + 10) & 0xffff);
            int[] rec = {b.getShort(r) & 0xffff, b.getShort(r + 2) & 0xffff, b.getShort(r + 4) & 0xffff,
                    b.getShort(r + 6) & 0xffff, records.size()};
            if (RENAMED.contains(rec[3]) || at + length > old.length) continue;
            records.add(rec);
            texts.add(Arrays.copyOfRange(old, at, at + length));
        }
        boolean regular = "Regular".equals(style);
        String full = regular ? family : family + " " + style;
        String[] ours = {family, style, "AGVN: " + full, full, family.replace(" ", "") + (regular ? "" : "-" + style)};
        int[] ids = {1, 2, 3, 4, 6};
        for (int i = 0; i < ids.length; i++) {
            records.add(new int[]{3, 1, 0x409, ids[i], records.size()});
            texts.add(ours[i].getBytes(StandardCharsets.UTF_16BE));
        }
        Collections.sort(records, Comparator.<int[]>comparingInt(r -> r[0]).thenComparingInt(r -> r[1])
                .thenComparingInt(r -> r[2]).thenComparingInt(r -> r[3]));
        int size = 6 + 12 * records.size();
        for (byte[] t : texts) size += t.length;
        ByteBuffer out = ByteBuffer.allocate(size);
        out.putShort((short) 0).putShort((short) records.size()).putShort((short) (6 + 12 * records.size()));
        int at = 0;
        for (int[] r : records) {
            byte[] t = texts.get(r[4]);
            out.putShort((short) r[0]).putShort((short) r[1]).putShort((short) r[2]).putShort((short) r[3])
                    .putShort((short) t.length).putShort((short) at);
            at += t.length;
        }
        for (int[] r : records) out.put(texts.get(r[4]));
        return out.array();
    }

    /** One collection file: this font once for each name table, every other table stored once and shared. */
    byte[] collection(List<byte[]> names) {
        int faces = names.size(), n = tags.length, name = index(NAME);
        int header = 12 + 4 * faces, dir = 12 + 16 * n, end = header + dir * faces;
        int[] shared = new int[n], own = new int[faces];
        for (int i = 0; i < n; i++) if (i != name) end = (shared[i] = end) + pad(tables[i].length);
        for (int k = 0; k < faces; k++) end = (own[k] = end) + pad(names.get(k).length);
        ByteBuffer b = ByteBuffer.allocate(end);
        b.putInt(TTCF).putInt(0x00010000).putInt(faces);
        for (int k = 0; k < faces; k++) b.putInt(header + dir * k);
        int power = Integer.highestOneBit(Math.max(n, 1));
        for (int k = 0; k < faces; k++) {
            b.putInt(version).putShort((short) n).putShort((short) (power * 16))
                    .putShort((short) Integer.numberOfTrailingZeros(power)).putShort((short) ((n - power) * 16));
            for (int i = 0; i < n; i++) {
                byte[] t = i == name ? names.get(k) : tables[i];
                b.putInt(tags[i]).putInt(checksum(t)).putInt(i == name ? own[k] : shared[i]).putInt(t.length);
            }
        }
        byte[] out = b.array();
        for (int i = 0; i < n; i++) if (i != name) System.arraycopy(tables[i], 0, out, shared[i], tables[i].length);
        for (int k = 0; k < faces; k++) System.arraycopy(names.get(k), 0, out, own[k], names.get(k).length);
        return out;
    }

    /** The table with {@code tag}; a font without it is no font to rename. */
    private int index(int tag) {
        for (int i = 0; i < tags.length; i++) if (tags[i] == tag) return i;
        throw new IllegalArgumentException("no table " + Integer.toHexString(tag));
    }

    static int checksum(byte[] t) {
        int sum = 0;
        for (int i = 0; i < t.length; i += 4) {
            int word = 0;
            for (int j = 0; j < 4; j++) word = word << 8 | (i + j < t.length ? t[i + j] & 0xff : 0);
            sum += word;
        }
        return sum;
    }

    private static int pad(int length) {
        return (length + 3) & ~3;
    }
}
