/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** One opened archive as {@link AgvnUnpack} reads it: its entries, then their bytes in the archive's order. */
interface AgvnArchiveReader extends Closeable {
    final class Item {
        /** The path as stored (decoded); cleaned by {@link AgvnArchiveNames#clean}. */
        final String path;
        final long size;
        final boolean dir;
        /** Locked with a password; a 7z knows only once the entry is opened. */
        boolean encrypted;

        Item(String path, long size, boolean dir, boolean encrypted) {
            this.path = path;
            this.size = Math.max(0, size);
            this.dir = dir;
            this.encrypted = encrypted;
        }
    }

    List<Item> items();

    /**
     * The bytes of entry {@code index}; entries are asked for in rising order. Reading to the end checks the entry
     * (CRC, AES). Errors: an AgvnUnpackException, or an IOException that {@link #failure} explains.
     */
    InputStream open(int index) throws IOException, AgvnUnpackException;

    /**
     * What a read error means. {@code verified}: a locked entry was already read and checked with this password, so
     * the password is right and a failed check is damage.
     */
    AgvnUnpackException failure(Exception e, Item item, boolean verified);

    @Override
    void close();
}
