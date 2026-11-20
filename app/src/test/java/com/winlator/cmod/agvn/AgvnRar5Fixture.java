/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * RAR5 archives for the unpack tests, written by hand from the format's description (rarlab.com/technote.htm): no tool
 * that is free to use creates RAR. The files are stored, never compressed (RAR's compression is not written here), so
 * they test the reading of the format: lists, sets of volumes, passwords. Names ending in "/" are folders.
 */
final class AgvnRar5Fixture {
    private static final byte[] SIGNATURE = {'R', 'a', 'r', '!', 0x1A, 7, 1, 0};

    private AgvnRar5Fixture() {}

    /** One archive; {@code files}: name, content, name, content... {@code password}: null for none (file data locked). */
    static File write(File out, String password, Object... files) throws Exception {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        all.write(SIGNATURE);
        all.write(block(1, 0, vint(0), null));
        for (int i = 0; i < files.length; i += 2) all.write(file((String) files[i], (byte[]) files[i + 1], password));
        all.write(block(5, 0, vint(0), null));
        return AgvnUnpackFixtures.write(out, all.toByteArray());
    }

    /** A set base.part1.rar, base.part2.rar... holding one file cut every {@code chunk} bytes. */
    static List<File> volumes(File dir, String base, String name, byte[] content, int chunk) throws Exception {
        List<File> out = new ArrayList<>();
        CRC32 whole = new CRC32();
        whole.update(content);
        for (int at = 0, n = 0; at < content.length; at += chunk, n++) {
            boolean last = at + chunk >= content.length;
            byte[] piece = Arrays.copyOfRange(content, at, Math.min(content.length, at + chunk));
            CRC32 crc = new CRC32();
            crc.update(piece);
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            all.write(SIGNATURE);
            all.write(block(1, 0, n == 0 ? vint(1) : concat(vint(3), vint(n)), null)); // a volume, its number from the second on
            // 8: continues from the volume before, 16: continues in the next; the last piece carries the whole file's CRC
            all.write(block(2, (n > 0 ? 8 : 0) | (last ? 0 : 16), fileBody(name, content.length, last ? whole.getValue() : crc.getValue()), piece));
            all.write(block(5, 0, vint(last ? 0 : 1), null));
            out.add(AgvnUnpackFixtures.write(new File(dir, base + ".part" + (n + 1) + ".rar"), all.toByteArray()));
        }
        return out;
    }

    private static byte[] file(String name, byte[] data, String password) throws Exception {
        if (name.endsWith("/")) return block(2, 0, concat(vint(1), vint(0), vint(0x10), vint(0), vint(0), named(name.substring(0, name.length() - 1))), null);
        CRC32 crc = new CRC32();
        crc.update(data);
        byte[] body = fileBody(name, data.length, crc.getValue());
        if (password == null) return block(2, 0, body, data);
        // the file encryption record: AES-256, a password check, 2^15 rounds of PBKDF2
        byte[] salt = new byte[16], iv = new byte[16];
        for (int i = 0; i < 16; i++) {
            salt[i] = (byte) (i + 1);
            iv[i] = (byte) (100 + i);
        }
        int rounds = 1 << 15;
        byte[] key = pbkdf2(password, salt, rounds), v2 = pbkdf2(password, salt, rounds + 32), check = new byte[8];
        for (int i = 0; i < 32; i++) check[i % 8] ^= v2[i];
        byte[] record = concat(vint(1), vint(0), vint(1), new byte[]{15}, salt, iv, check,
                Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(check), 4));
        Cipher aes = Cipher.getInstance("AES/CBC/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        byte[] packed = aes.doFinal(Arrays.copyOf(data, (data.length + 15) / 16 * 16));
        return block(2, 1, concat(body, vint(record.length), record), packed, vint(record.length).length + record.length);
    }

    /** File flags (4: CRC), size, attributes, CRC, compression (0: stored), host OS, name. */
    private static byte[] fileBody(String name, long size, long crc) {
        return concat(vint(4), vint(size), vint(0x20), ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((int) crc).array(),
                vint(0), vint(0), named(name));
    }

    private static byte[] named(String name) {
        byte[] utf8 = name.getBytes(StandardCharsets.UTF_8);
        return concat(vint(utf8.length), utf8);
    }

    private static byte[] block(int type, int flags, byte[] body, byte[] data) {
        return block(type, flags, body, data, 0);
    }

    /** CRC, header size, type, flags (1: extra area, 2: data), [extra size], [data size], body (the extra area at its end), data. */
    private static byte[] block(int type, int flags, byte[] body, byte[] data, int extraSize) {
        byte[] header = concat(vint(type), vint(flags | (data != null ? 2 : 0)), extraSize > 0 ? vint(extraSize) : new byte[0],
                data != null ? vint(data.length) : new byte[0], body);
        header = concat(vint(header.length), header);
        CRC32 crc = new CRC32();
        crc.update(header);
        return concat(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((int) crc.getValue()).array(), header,
                data != null ? data : new byte[0]);
    }

    private static byte[] pbkdf2(String password, byte[] salt, int rounds) throws Exception {
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(password.toCharArray(), salt, rounds, 256)).getEncoded();
    }

    private static byte[] vint(long n) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (; n >= 0x80; n >>>= 7) out.write((int) (n & 0x7F) | 0x80);
        out.write((int) n);
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.write(p, 0, p.length);
        return out.toByteArray();
    }
}
