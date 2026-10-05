/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * A picture a game ships: a file in its folder, a file packed in a game archive (Ren'Py .rpa, RPG Maker .rgssad/
 * .rgss2a/.rgss3a), or an encrypted RPG Maker MV/MZ image. The bytes are read only when the picture is used.
 * Pure Java (JVM-testable).
 */
final class AgvnArt {
    interface Source {
        byte[] read() throws IOException;
    }

    /** Largest picture read: no cover or icon needs more. */
    static final int MAX_BYTES = 32 << 20;
    /** The first 16 bytes of every PNG: signature, then the IHDR chunk's length and type. */
    private static final byte[] PNG_HEAD = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

    /** Where it comes from, for logs and tests: a path, or "<archive>:<path inside>". */
    final String name;
    /** Size in bytes as stored (an encrypted MV/MZ image is 16 bytes larger than the picture). */
    final long size;
    private final Source source;

    AgvnArt(String name, long size, Source source) {
        this.name = name;
        this.size = size;
        this.source = source;
    }

    static AgvnArt file(File f) {
        String n = f.getName().toLowerCase(java.util.Locale.ROOT);
        if (n.endsWith(".rpgmvp") || n.endsWith(".png_")) {
            return new AgvnArt(f.getPath(), f.length(), () -> restorePng(readFile(f)));
        }
        return new AgvnArt(f.getPath(), f.length(), () -> readFile(f));
    }

    static AgvnArt rpa(File archive, AgvnRpaArchive.Entry e) {
        return new AgvnArt(archive.getName() + ":" + e.name, e.length, () -> AgvnRpaArchive.read(archive, e, MAX_BYTES));
    }

    static AgvnArt rgss(File archive, AgvnRgssArchive.Entry e) {
        return new AgvnArt(archive.getName() + ":" + e.name, e.size, () -> AgvnRgssArchive.read(archive, e, MAX_BYTES));
    }

    static AgvnArt bytes(String name, byte[] data) {
        return new AgvnArt(name, data.length, () -> data);
    }

    /** The picture's bytes; null when it cannot be read. */
    byte[] read() {
        try {
            return source.read();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static byte[] readFile(File f) throws IOException {
        if (f.length() > MAX_BYTES) return null;
        return Files.readAllBytes(f.toPath());
    }

    /**
     * An RPG Maker MV/MZ encrypted image (.rpgmvp, .png_): a 16-byte "RPGMV" header, then the PNG with its first
     * 16 bytes XOR-ed with the game's key. Those 16 bytes are the same in every PNG, so they are put back without the
     * key. Null when {@code data} is not such an image.
     */
    static byte[] restorePng(byte[] data) {
        if (data == null || data.length < 32 || !new String(data, 0, 5, StandardCharsets.ISO_8859_1).equals("RPGMV")) return null;
        byte[] png = new byte[data.length - 16];
        System.arraycopy(data, 16, png, 0, png.length);
        System.arraycopy(PNG_HEAD, 0, png, 0, PNG_HEAD.length);
        return png;
    }

    /** {width, height} of a PNG from its IHDR, or null when {@code data} is not a PNG. */
    static int[] pngSize(byte[] data) {
        if (data == null || data.length < 24) return null;
        for (int i = 0; i < PNG_HEAD.length; i++) if (data[i] != PNG_HEAD[i]) return null;
        return new int[]{beInt(data, 16), beInt(data, 20)};
    }

    private static int beInt(byte[] d, int at) {
        return (d[at] & 0xFF) << 24 | (d[at + 1] & 0xFF) << 16 | (d[at + 2] & 0xFF) << 8 | (d[at + 3] & 0xFF);
    }
}
