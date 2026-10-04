/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.Adler32;
import java.util.zip.Deflater;

/** XP3 archives and KiriKiri texts as krkr2's packer (krkrrel) writes them, for the tests. */
final class Xp3Fixture {
    static final byte[] MARK = {'X', 'P', '3', 0x0d, 0x0a, ' ', 0x0a, 0x1a, (byte) 0x8b, 0x67, 0x01};

    private Xp3Fixture() {}

    /**
     * An archive of {@code names} and {@code contents}. {@code v2}: the index is reached through an empty "cushion"
     * index flagged "continue"; {@code packIndex}, {@code packFiles}: zlib.
     */
    static byte[] archive(boolean v2, boolean packIndex, boolean packFiles, String[] names, byte[][] contents) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MARK, 0, MARK.length);
        int header = v2 ? MARK.length + 8 + 4 + 1 + 8 + 8 : MARK.length + 8;
        out.write(new byte[header - MARK.length], 0, header - MARK.length); // filled in below
        ByteArrayOutputStream index = new ByteArrayOutputStream();
        for (int i = 0; i < names.length; i++) {
            long offset = out.size();
            byte[] stored = packFiles ? zlib(contents[i]) : contents[i];
            out.write(stored, 0, stored.length);
            byte[] name = names[i].getBytes(StandardCharsets.UTF_16LE);
            ByteBuffer info = le(4 + 8 + 8 + 2 + name.length).putInt(0).putLong(contents[i].length).putLong(stored.length)
                    .putShort((short) names[i].length()).put(name);
            ByteBuffer segm = le(28).putInt(packFiles ? 1 : 0).putLong(offset).putLong(contents[i].length).putLong(stored.length);
            Adler32 adler = new Adler32();
            adler.update(contents[i]);
            byte[] file = concat(chunk("info", info.array()), chunk("segm", segm.array()),
                    chunk("adlr", le(4).putInt((int) adler.getValue()).array()));
            byte[] fileChunk = chunk("File", file);
            index.write(fileChunk, 0, fileChunk.length);
        }
        long indexAt = out.size();
        byte[] raw = index.toByteArray();
        if (packIndex) {
            byte[] packed = zlib(raw);
            out.write(1);
            write(out, le(16).putLong(packed.length).putLong(raw.length).array());
            write(out, packed);
        } else {
            out.write(0);
            write(out, le(8).putLong(raw.length).array());
            write(out, raw);
        }
        ByteBuffer b = ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
        if (v2) {
            b.putLong(MARK.length, 0x17).putInt(19, 1); // cushion at 0x17, header minor version 1
            b.put(23, (byte) 0x80).putLong(24, 0).putLong(32, indexAt); // an empty raw index, "continue", the real one
        } else {
            b.putLong(MARK.length, indexAt);
        }
        return b.array();
    }

    /** A Config.tjs of KAG3's kind, UTF-16 with its mark, as KiriKiri's tools save it. */
    static byte[] config(int width, int height) {
        String text = "// Config.tjs - KAG3 の設定\r\n//\r\n// ◆ 画面サイズ\r\n;scWidth = " + width + ";\r\n"
                + "// 画面の高さ\r\n;scHeight = " + height + ";\r\n;thumbnailWidth = 133;\r\n";
        return concat(new byte[]{(byte) 0xff, (byte) 0xfe}, text.getBytes(StandardCharsets.UTF_16LE));
    }

    /** {@code text} in KiriKiri's own "FE FE mode FF FE" form: mode 0 and 1 scramble each character, 2 packs. */
    static byte[] scrambled(String text, int mode) {
        byte[] head = {(byte) 0xfe, (byte) 0xfe, (byte) mode, (byte) 0xff, (byte) 0xfe};
        if (mode == 2) {
            byte[] plain = text.getBytes(StandardCharsets.UTF_16LE), packed = zlib(plain);
            return concat(head, le(16).putLong(packed.length).putLong(plain.length).array(), packed);
        }
        char[] c = text.toCharArray();
        for (int i = 0; i < c.length; i++) {
            char ch = c[i];
            if (mode == 0) c[i] = ch >= 0x20 ? (char) (ch ^ (((ch & 0xfe) << 8) ^ 1)) : ch;
            else c[i] = (char) (((ch & 0xaaaa) >> 1) | ((ch & 0x5555) << 1));
        }
        return concat(head, new String(c).getBytes(StandardCharsets.UTF_16LE));
    }

    static byte[] zlib(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf));
        deflater.end();
        return out.toByteArray();
    }

    private static byte[] chunk(String tag, byte[] data) {
        return concat(tag.getBytes(StandardCharsets.US_ASCII), le(8).putLong(data.length).array(), data);
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) write(out, p);
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, byte[] data) {
        out.write(data, 0, data.length);
    }

    private static ByteBuffer le(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }
}
