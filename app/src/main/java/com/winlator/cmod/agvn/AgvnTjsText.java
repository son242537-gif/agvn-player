/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The text of a KiriKiri script (.tjs, .ks), as krkr2's TextStream.cpp reads it: UTF-16 or UTF-8 by its mark, else
 * the bytes as they are (Shift-JIS keeps ASCII as is), or KiriKiri's own "FE FE mode FF FE" text, where mode 0 and 1
 * scramble each character and mode 2 is zlib-packed. Pure Java.
 */
final class AgvnTjsText {
    private static final int PACKED = 21; // FE FE 02 FF FE, the packed size, the text's size

    private AgvnTjsText() {}

    /** The text of {@code b}, or null for a KiriKiri text it cannot read or longer than {@code max} bytes. */
    static String decode(byte[] b, int max) {
        if (b.length >= 5 && u(b, 0) == 0xfe && u(b, 1) == 0xfe && u(b, 3) == 0xff && u(b, 4) == 0xfe) {
            if (b[2] == 2) return unpack(b, max);
            if (b[2] != 0 && b[2] != 1) return null;
            char[] c = new char[(b.length - 5) / 2];
            for (int i = 0; i < c.length; i++) {
                char ch = (char) (u(b, 5 + 2 * i) | u(b, 6 + 2 * i) << 8);
                if (b[2] == 0) c[i] = ch >= 0x20 ? (char) (ch ^ (((ch & 0xfe) << 8) ^ 1)) : ch;
                else c[i] = (char) (((ch & 0xaaaa) >> 1) | ((ch & 0x5555) << 1));
            }
            return new String(c);
        }
        if (b.length >= 2 && u(b, 0) == 0xff && u(b, 1) == 0xfe) return new String(b, 2, b.length - 2, StandardCharsets.UTF_16LE);
        if (b.length >= 3 && u(b, 0) == 0xef && u(b, 1) == 0xbb && u(b, 2) == 0xbf)
            return new String(b, 3, b.length - 3, StandardCharsets.UTF_8);
        return new String(b, StandardCharsets.ISO_8859_1);
    }

    private static String unpack(byte[] b, int max) {
        if (b.length < PACKED) return null;
        ByteBuffer sizes = ByteBuffer.wrap(b, 5, 16).order(ByteOrder.LITTLE_ENDIAN);
        long packed = sizes.getLong(), size = sizes.getLong();
        if (packed < 0 || packed > b.length - PACKED || size < 0 || size > max) return null;
        try {
            byte[] text = AgvnXp3.inflate(Arrays.copyOfRange(b, PACKED, PACKED + (int) packed), (int) size);
            return new String(text, StandardCharsets.UTF_16LE);
        } catch (IOException e) {
            return null;
        }
    }

    private static int u(byte[] b, int i) {
        return b[i] & 0xff;
    }
}
