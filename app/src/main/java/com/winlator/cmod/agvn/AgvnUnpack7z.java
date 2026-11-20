/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.agvn.AgvnUnpackException.Reason;

import org.apache.commons.compress.PasswordRequiredException;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration;
import org.apache.commons.compress.utils.MultiReadOnlySeekableByteChannel;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A 7z read with commons-compress: AES passwords (also a locked file list), parts name.7z.001... read as one file.
 * Entries are read in the archive's order, as a solid archive needs.
 */
final class AgvnUnpack7z implements AgvnArchiveReader {
    private final List<RandomAccessFile> files = new ArrayList<>();
    private SevenZFile archive;
    private final List<Item> items = new ArrayList<>();
    /** Index of the entry {@link SevenZFile#getNextEntry} gave last. */
    private int cursor = -1;

    /** {@code parts}: the files of one 7z in order (one file for a whole archive). */
    AgvnUnpack7z(List<File> parts, char[] password) throws AgvnUnpackException {
        try {
            try {
                archive = open(parts, null);
            } catch (PasswordRequiredException locked) { // the file list itself is locked
                if (password == null || password.length == 0) throw new AgvnUnpackException(Reason.NEED_PASSWORD);
                archive = null;
            }
            if (archive == null) {
                try {
                    archive = open(parts, password);
                } catch (Exception | OutOfMemoryError e) { // a wrong key turns the list into noise
                    throw new AgvnUnpackException(Reason.WRONG_PASSWORD, null, 0, e);
                }
            } else if (password != null && password.length > 0) {
                closeFiles(); // the list is readable: opened again with the password for the locked files
                archive = open(parts, password);
            }
            for (SevenZArchiveEntry e : archive.getEntries())
                items.add(new Item(e.getName(), e.getSize(), e.isDirectory() || e.isAntiItem(), false));
        } catch (AgvnUnpackException e) {
            close();
            throw e;
        } catch (Exception | OutOfMemoryError e) {
            close();
            throw failure(e instanceof Exception ? (Exception) e : new IOException(e), null, false);
        }
    }

    private SevenZFile open(List<File> parts, char[] password) throws IOException {
        closeFiles();
        SeekableByteChannel[] channels = new SeekableByteChannel[parts.size()];
        for (int i = 0; i < channels.length; i++) {
            RandomAccessFile f = new RandomAccessFile(parts.get(i), "r");
            files.add(f);
            channels[i] = f.getChannel();
        }
        SeekableByteChannel all = channels.length == 1 ? channels[0] : MultiReadOnlySeekableByteChannel.forSeekableByteChannels(channels);
        return new SevenZFile(all, parts.get(0).getName(), password);
    }

    @Override
    public List<Item> items() {
        return items;
    }

    @Override
    public InputStream open(int index) throws IOException {
        SevenZArchiveEntry entry = null;
        while (cursor < index) {
            if ((entry = archive.getNextEntry()) == null) throw new IOException("7z ends before entry " + index);
            cursor++;
        }
        Iterable<? extends SevenZMethodConfiguration> methods = entry != null && entry.hasStream() ? entry.getContentMethods() : null;
        if (methods != null) for (SevenZMethodConfiguration m : methods) items.get(index).encrypted |= m.getMethod() == SevenZMethod.AES256SHA256;
        return new InputStream() {
            @Override
            public int read() throws IOException {
                return archive.read();
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return archive.read(b, off, len);
            }
        };
    }

    @Override
    public AgvnUnpackException failure(Exception e, Item item, boolean verified) {
        String name = item != null ? item.path : null;
        String text = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
        if (e instanceof PasswordRequiredException) return new AgvnUnpackException(Reason.NEED_PASSWORD, name, 0, e);
        if (text.contains("unsupported") || text.contains("not yet supported") || text.contains("not supported"))
            return new AgvnUnpackException(Reason.UNSUPPORTED_METHOD, name, 0, e);
        // a wrong password shows as noise in the first locked file: its unpacking or its CRC fails
        if (item != null && item.encrypted && !verified) return new AgvnUnpackException(Reason.WRONG_PASSWORD, name, 0, e);
        return new AgvnUnpackException(Reason.DAMAGED, name, 0, e);
    }

    private void closeFiles() {
        for (RandomAccessFile f : files) {
            try {
                f.close();
            } catch (IOException ignored) {
                // read only
            }
        }
        files.clear();
    }

    @Override
    public void close() {
        try {
            if (archive != null) archive.close();
        } catch (IOException ignored) {
            // read only
        }
        closeFiles();
    }
}
