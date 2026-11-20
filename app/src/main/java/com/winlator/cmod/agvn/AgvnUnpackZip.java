/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.agvn.AgvnUnpackException.Reason;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ExtraDataRecord;
import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * A ZIP read with zip4j: ZipCrypto and AES passwords, split ZIPs (name.zip + name.z01..., name.zip.001...). Names are
 * taken as raw bytes and decoded here: UTF-8 when the entry says so or carries a Unicode name (WinRAR, Info-ZIP), else
 * the charset {@link AgvnArchiveNames#charsetFor} picks for the whole archive.
 */
final class AgvnUnpackZip implements AgvnArchiveReader {
    /** Info-ZIP Unicode Path extra field: version, CRC of the stored name, the name in UTF-8. */
    private static final long UNICODE_PATH = 0x7075;

    private final ZipFile zip;
    private final List<FileHeader> headers;
    private final List<Item> items = new ArrayList<>();

    AgvnUnpackZip(File first, char[] password) throws AgvnUnpackException {
        zip = new ZipFile(first, password);
        zip.setCharset(StandardCharsets.ISO_8859_1); // one char per byte: the stored bytes come back untouched
        try {
            headers = zip.getFileHeaders();
            if (zip.isSplitArchive()) {
                for (File part : zip.getSplitZipFiles())
                    if (!part.isFile()) throw new AgvnUnpackException(Reason.MISSING_PART, part.getName());
            }
        } catch (IOException e) {
            close();
            throw failure(e, null, false);
        }
        List<byte[]> plain = new ArrayList<>();
        String[] unicode = new String[headers.size()];
        for (int i = 0; i < headers.size(); i++) {
            FileHeader h = headers.get(i);
            byte[] raw = h.getFileName().getBytes(StandardCharsets.ISO_8859_1);
            unicode[i] = h.isFileNameUTF8Encoded() ? new String(raw, StandardCharsets.UTF_8) : unicodePath(h, raw);
            if (unicode[i] == null) plain.add(raw);
        }
        Charset charset = AgvnArchiveNames.charsetFor(plain);
        for (int i = 0; i < headers.size(); i++) {
            FileHeader h = headers.get(i);
            String name = unicode[i] != null ? unicode[i] : new String(h.getFileName().getBytes(StandardCharsets.ISO_8859_1), charset);
            items.add(new Item(name, h.getUncompressedSize(), h.isDirectory(), h.isEncrypted()));
        }
    }

    private static String unicodePath(FileHeader h, byte[] rawName) {
        List<ExtraDataRecord> extras = h.getExtraDataRecords();
        if (extras == null) return null;
        for (ExtraDataRecord extra : extras) {
            byte[] d = extra.getData();
            if (extra.getHeader() != UNICODE_PATH || d == null || d.length < 6 || d[0] != 1) continue;
            CRC32 crc = new CRC32();
            crc.update(rawName);
            long stored = (d[1] & 0xFFL) | (d[2] & 0xFFL) << 8 | (d[3] & 0xFFL) << 16 | (d[4] & 0xFFL) << 24;
            if (stored == crc.getValue()) return new String(d, 5, d.length - 5, StandardCharsets.UTF_8);
        }
        return null;
    }

    @Override
    public List<Item> items() {
        return items;
    }

    @Override
    public InputStream open(int index) throws IOException {
        return zip.getInputStream(headers.get(index));
    }

    @Override
    public AgvnUnpackException failure(Exception e, Item item, boolean verified) {
        String name = item != null ? item.path : null;
        if (e instanceof ZipException) {
            ZipException.Type type = ((ZipException) e).getType();
            String text = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
            // zip4j also calls a failed CRC of a ZipCrypto entry "wrong password": damage once the password is known right
            if (type == ZipException.Type.WRONG_PASSWORD && !verified) return new AgvnUnpackException(Reason.WRONG_PASSWORD, name, 0, e);
            if (type == ZipException.Type.UNKNOWN_COMPRESSION_METHOD || type == ZipException.Type.UNSUPPORTED_ENCRYPTION
                    || text.contains("unsupported") || text.contains("not supported"))
                return new AgvnUnpackException(Reason.UNSUPPORTED_METHOD, name, 0, e);
            if (type == ZipException.Type.FILE_NOT_FOUND) return new AgvnUnpackException(Reason.MISSING_PART, null, 0, e);
        }
        return new AgvnUnpackException(Reason.DAMAGED, name, 0, e);
    }

    @Override
    public void close() {
        try {
            zip.close();
        } catch (IOException ignored) {
            // nothing was written through it
        }
    }
}
