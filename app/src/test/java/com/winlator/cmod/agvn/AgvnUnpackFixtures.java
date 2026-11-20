/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Archives built for the unpack tests, and the listener they pass. */
final class AgvnUnpackFixtures {
    private AgvnUnpackFixtures() {}

    /** Never cancels; remembers the last file named. */
    static final class Quiet implements AgvnUnpack.Listener {
        String last;
        long done, total;

        @Override
        public void onProgress(long doneBytes, long totalBytes, String name) {
            done = doneBytes;
            total = totalBytes;
            if (!name.isEmpty()) last = name;
        }

        @Override
        public boolean cancelled() {
            return false;
        }
    }

    static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** {@code unicode} when this machine's file names can hold it (a Linux build machine without a UTF-8 locale cannot). */
    static String name(String unicode, String ascii) {
        try {
            new File(unicode).toPath();
            return unicode;
        } catch (java.nio.file.InvalidPathException e) {
            return ascii;
        }
    }

    static byte[] readBytes(File file) throws IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    static String read(File file) throws IOException {
        return new String(readBytes(file), StandardCharsets.UTF_8);
    }

    static File write(File file, byte[] data) throws IOException {
        file.getParentFile().mkdirs();
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            out.write(data);
        }
        return file;
    }

    static void delete(File file) {
        AgvnUnpackFiles.delete(file);
    }

    /** Cuts {@code whole} into name.001, name.002... of {@code partSize} bytes, as 7-Zip's "split to volumes" does. */
    static List<File> cut(File whole, int partSize) throws IOException {
        byte[] all = readBytes(whole);
        List<File> parts = new ArrayList<>();
        for (int at = 0, n = 1; at < all.length; at += partSize, n++) {
            File part = new File(whole.getPath() + String.format(Locale.ROOT, ".%03d", n));
            write(part, Arrays.copyOfRange(all, at, Math.min(all.length, at + partSize)));
            parts.add(part);
        }
        whole.delete();
        return parts;
    }

    /** A 7z without a password; {@code files}: name, content, name, content... */
    static File sevenZ(File out, String... files) throws IOException {
        try (SevenZOutputFile z = new SevenZOutputFile(out)) {
            for (int i = 0; i < files.length; i += 2) {
                SevenZArchiveEntry e = new SevenZArchiveEntry();
                e.setName(files[i]);
                z.putArchiveEntry(e);
                z.write(bytes(files[i + 1]));
                z.closeArchiveEntry();
            }
        }
        return out;
    }

    /**
     * A 7z holding one file locked with AES-256 as 7-Zip does it (no 7-Zip on the build machine, and commons-compress
     * 1.20 writes no locked 7z): written by hand from the format's description. {@code lockNames}: the file list is
     * locked too ("Encrypt file names").
     */
    static File locked7z(File out, String name, byte[] content, String password, boolean lockNames) throws Exception {
        byte[] iv = new byte[16];
        for (int i = 0; i < iv.length; i++) iv[i] = (byte) (i * 7 + 1);
        byte[] packed = aes(content, password, iv);
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        header.write(new byte[]{1, 4}); // Header, MainStreamsInfo
        streams(header, 0, packed.length, content, iv, true);
        header.write(new byte[]{5, 1, 0x11}); // FilesInfo, one file, Names
        byte[] utf16 = (name + "\0").getBytes(StandardCharsets.UTF_16LE);
        header.write(utf16.length + 1);
        header.write(0);
        header.write(utf16);
        header.write(new byte[]{0, 0}); // end of FilesInfo, end of Header
        byte[] next = header.toByteArray(), extra = new byte[0];
        if (lockNames) {
            extra = aes(next, password, iv);
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            encoded.write(0x17); // EncodedHeader
            streams(encoded, packed.length, extra.length, next, iv, false);
            next = encoded.toByteArray();
        }
        ByteBuffer start = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
        start.putLong(packed.length + extra.length).putLong(next.length).putInt((int) crc(next));
        ByteBuffer file = ByteBuffer.allocate(32 + packed.length + extra.length + next.length).order(ByteOrder.LITTLE_ENDIAN);
        file.put(new byte[]{'7', 'z', (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C, 0, 4}).putInt((int) crc(start.array())).put(start.array());
        file.put(packed).put(extra).put(next);
        return write(out, file.array());
    }

    /** StreamsInfo of one AES-locked stream (sizes below 128: one byte each). */
    private static void streams(ByteArrayOutputStream out, int packPos, int packSize, byte[] plain, byte[] iv, boolean files)
            throws IOException {
        out.write(new byte[]{6, (byte) packPos, 1, 9, (byte) packSize, 0}); // PackInfo
        out.write(new byte[]{7, 0x0B, 1, 0, 1, 0x24, 6, (byte) 0xF1, 7, 1, 18, 0x46, 0x0F}); // one folder, coder AES, 2^6 rounds, 16-byte IV
        out.write(iv);
        out.write(new byte[]{0x0C, (byte) plain.length, 0x0A, 1}); // CodersUnpackSize, CRC
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((int) crc(plain)).array());
        out.write(0); // end of UnpackInfo
        if (files) out.write(new byte[]{8, 0}); // SubStreamsInfo: one file per folder
        out.write(0); // end of StreamsInfo
    }

    private static byte[] aes(byte[] plain, String password, byte[] iv) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] pass = password.getBytes(StandardCharsets.UTF_16LE);
        for (long round = 0; round < 64; round++) {
            sha.update(pass);
            sha.update(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(round).array());
        }
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sha.digest(), "AES"), new IvParameterSpec(iv));
        return cipher.doFinal(Arrays.copyOf(plain, (plain.length + 15) / 16 * 16));
    }

    private static long crc(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }
}
