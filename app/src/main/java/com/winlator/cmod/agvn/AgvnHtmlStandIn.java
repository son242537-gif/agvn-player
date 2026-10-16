/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.core.FileUtils;

import java.io.File;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An empty picture in place of an RPG Maker MV/MZ encrypted one (.rpgmvp, .png_) that is missing or too short to be a
 * picture, as {@link AgvnHtmlFiles} does for a missing .png. The engine checks the 16-byte header and decrypts the next
 * 16 bytes before it draws: an empty file stopped Yarisutemesubuta with "RangeError: Invalid typed array length: 16"
 * at Decrypter.decryptArrayBuffer (OPPO CPH2127, 10/10/2026), and a missing one stops the game at "Failed to load".
 * The empty picture is encrypted with the game's own key (data/System.json), so the engine takes it as one of its own.
 */
final class AgvnHtmlStandIn {
    /** What every encrypted picture starts with ("RPGMV", version 0.3.1); MV and MZ check it byte by byte. */
    static final byte[] HEADER = {0x52, 0x50, 0x47, 0x4D, 0x56, 0, 0, 0, 0, 0x03, 0x01, 0, 0, 0, 0, 0};
    /** The header and the 16 bytes the key hides: a shorter file cannot be a picture. */
    static final int SHORTEST = 32;
    private static final Pattern KEY = Pattern.compile("\"encryptionKey\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");
    /** The last game folder asked about and its key (a missing picture is asked for again and again). */
    private static String keyRoot;
    private static byte[] keyBytes;

    private AgvnHtmlStandIn() {}

    /** True for the URL path of an encrypted picture (MV: .rpgmvp, MZ: .png_). */
    static boolean encryptedPicture(String urlPath) {
        String lower = urlPath == null ? "" : urlPath.toLowerCase(Locale.ROOT);
        return lower.endsWith(".rpgmvp") || lower.endsWith(".png_");
    }

    /**
     * The bytes to serve for encrypted picture {@code urlPath} whose file is {@code file} (null: missing): {@code png}
     * encrypted with the game's key when the file is missing or shorter than {@link #SHORTEST}; null to serve the file
     * as it is (a whole file, another kind of file, or a game without a key).
     */
    static byte[] bytesFor(File root, String urlPath, File file, byte[] png) {
        if (!encryptedPicture(urlPath) || file != null && file.length() >= SHORTEST) return null;
        byte[] key = key(root);
        return key != null ? encrypt(png, key) : null;
    }

    /** The game's key: data/System.json's "encryptionKey" (32 hex digits) as 16 bytes, or null without one. */
    static synchronized byte[] key(File root) {
        String at = root.getAbsolutePath();
        if (at.equals(keyRoot)) return keyBytes;
        File system = AgvnHtmlFiles.resolve(root, "/data/System.json");
        String json = system != null ? FileUtils.readString(system) : null;
        Matcher m = json != null ? KEY.matcher(json) : null;
        keyRoot = at;
        keyBytes = m != null && m.find() ? hex(m.group(1)) : null;
        return keyBytes;
    }

    /** {@code png} as RPG Maker MV/MZ stores it: the header, then the picture, its first 16 bytes XOR-ed by the key. */
    static byte[] encrypt(byte[] png, byte[] key) {
        byte[] out = new byte[HEADER.length + png.length];
        System.arraycopy(HEADER, 0, out, 0, HEADER.length);
        System.arraycopy(png, 0, out, HEADER.length, png.length);
        for (int i = 0; i < key.length && i < png.length; i++) out[HEADER.length + i] ^= key[i];
        return out;
    }

    private static byte[] hex(String digits) {
        byte[] out = new byte[digits.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(digits.substring(2 * i, 2 * i + 2), 16);
        return out;
    }
}
