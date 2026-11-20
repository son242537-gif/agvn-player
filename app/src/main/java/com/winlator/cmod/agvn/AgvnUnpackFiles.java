/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.agvn.AgvnArchiveReader.Item;
import com.winlator.cmod.agvn.AgvnUnpackException.Reason;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** File work of {@link AgvnUnpack}: writing one entry, the empty marker file, removing a half-written folder. */
final class AgvnUnpackFiles {
    private AgvnUnpackFiles() {}

    /**
     * Writes entry {@code index} to {@code out}, read to its end so the archive checks it (CRC, AES). Returns the bytes
     * done so far. A read error is the archive's ({@link AgvnArchiveReader#failure}); a write error is the storage's:
     * no room left, or the file cannot be written.
     */
    static long copy(AgvnArchiveReader reader, int index, Item item, File out, byte[] buffer, AgvnUnpack.Listener listener,
                     long done, long total, String shownName, boolean verified, File gamesRoot) throws AgvnUnpackException {
        OutputStream os;
        try {
            os = new FileOutputStream(out);
        } catch (IOException e) {
            throw new AgvnUnpackException(Reason.CANNOT_WRITE, shownName, 0, e);
        }
        try (OutputStream closing = os) {
            InputStream in;
            try {
                in = reader.open(index);
            } catch (AgvnUnpackException e) {
                throw e;
            } catch (Exception e) {
                throw reader.failure(e, item, verified);
            }
            try (InputStream closingIn = in) {
                while (true) {
                    if (listener.cancelled()) throw new AgvnUnpackException(Reason.CANCELLED);
                    int n;
                    try {
                        n = in.read(buffer, 0, buffer.length);
                    } catch (Exception e) {
                        throw reader.failure(e, item, verified);
                    }
                    if (n < 0) break;
                    try {
                        os.write(buffer, 0, n);
                    } catch (IOException e) {
                        throw writeFailure(e, shownName, gamesRoot);
                    }
                    done += n;
                    listener.onProgress(done, total, shownName);
                }
            } catch (IOException e) { // closing the entry: zip4j checks what is left of it
                throw reader.failure(e, item, verified);
            }
            try {
                os.flush();
            } catch (IOException e) {
                throw writeFailure(e, shownName, gamesRoot);
            }
        } catch (IOException e) {
            throw writeFailure(e, shownName, gamesRoot);
        }
        return done;
    }

    private static AgvnUnpackException writeFailure(IOException e, String name, File gamesRoot) {
        long free = gamesRoot.getUsableSpace();
        if (free < (1L << 20)) return new AgvnUnpackException(Reason.NO_SPACE, name, (1L << 20) - free, e);
        return new AgvnUnpackException(Reason.CANNOT_WRITE, name, 0, e);
    }

    static void touch(File file) throws AgvnUnpackException {
        try {
            new FileOutputStream(file).close();
        } catch (IOException e) {
            throw new AgvnUnpackException(Reason.CANNOT_WRITE, file.getPath(), 0, e);
        }
    }

    /** Removes a folder and all in it; links are removed, not followed. */
    static void delete(File file) {
        if (file == null) return;
        if (file.isDirectory() && !java.nio.file.Files.isSymbolicLink(file.toPath())) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) delete(child);
        }
        file.delete();
    }
}
