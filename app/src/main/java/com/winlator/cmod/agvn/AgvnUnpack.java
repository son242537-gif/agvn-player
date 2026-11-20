/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.agvn.AgvnArchiveReader.Item;
import com.winlator.cmod.agvn.AgvnUnpackException.Reason;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "Thêm từ tệp nén": unpacks a downloaded archive into a folder of its own under the games folder, so nothing spills
 * loose beside the archive. The work is done in a hidden folder that gets its name only when all of it is there and is
 * removed when anything fails; an existing game folder is never touched. ZIP is read with zip4j (it gets old ZIPs'
 * file names right) and 7z with commons-compress; RAR, TAR and what those two cannot read go to 7-Zip's own program
 * ({@link AgvnUnpack7zz}). Pure Java (JVM-testable); slow, never on the main thread.
 */
public final class AgvnUnpack {
    /** Room kept free beyond the archive's content. */
    static final long SPARE_BYTES = 32L << 20;
    /** Stops the phone's Gallery from listing the game's pictures and videos. */
    static final String NOMEDIA = ".nomedia";

    public interface Listener {
        /** {@code name}: the file being written. Called from the working thread, often. */
        void onProgress(long doneBytes, long totalBytes, String name);

        boolean cancelled();
    }

    public static final class Result {
        /** The new game folder. */
        public final File dir;
        public final int files;
        /** Entries left out because they pointed outside the folder. */
        public final int rejected;
        /** Every file of the archive (all parts of a set): what the player may delete now. */
        public List<File> archiveFiles = Collections.emptyList();

        Result(File dir, int files, int rejected) {
            this.dir = dir;
            this.files = files;
            this.rejected = rejected;
        }
    }

    private AgvnUnpack() {}

    /** A pasted password without the spaces and line breaks around it; null when nothing is left. */
    public static String trimPassword(String typed) {
        if (typed == null) return null;
        int start = 0, end = typed.length();
        while (start < end && blank(typed.charAt(start))) start++;
        while (end > start && blank(typed.charAt(end - 1))) end--;
        return start == end ? null : typed.substring(start, end);
    }

    private static boolean blank(char c) {
        return c <= ' ' || Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '​' || c == '﻿';
    }

    /**
     * Unpacks the archive {@code picked} belongs to (any part of it) into a new folder of {@code gamesRoot}.
     * {@code password}: as typed or pasted, null when none was asked yet ({@link Reason#NEED_PASSWORD} then asks).
     */
    public static Result extract(File picked, String password, File gamesRoot, Listener listener) throws AgvnUnpackException {
        return extract(picked, password, gamesRoot, null, listener, -1);
    }

    /** {@code sevenZip}: 7-Zip's program (libagvn7zz.so in the app's nativeLibraryDir); null: only what Java reads. */
    public static Result extract(File picked, String password, File gamesRoot, File sevenZip, Listener listener) throws AgvnUnpackException {
        return extract(picked, password, gamesRoot, sevenZip, listener, -1);
    }

    /** {@code freeBytes}: the room to count on, or -1 to ask the storage (tests). */
    static Result extract(File picked, String password, File gamesRoot, File sevenZip, Listener listener, long freeBytes)
            throws AgvnUnpackException {
        AgvnArchiveKind.Volumes volumes = AgvnArchiveKind.resolve(picked);
        if (volumes.missing != null) throw new AgvnUnpackException(Reason.MISSING_PART, volumes.missing);
        if (volumes.type == AgvnArchiveKind.Type.UNKNOWN) throw new AgvnUnpackException(Reason.NOT_ARCHIVE);
        if (!volumes.first.isFile()) throw new AgvnUnpackException(Reason.MISSING_PART, volumes.first.getName());
        // the password without what a paste adds; as typed after that, for a password that does end in a space
        Set<String> tries = new LinkedHashSet<>();
        tries.add(trimPassword(password));
        if (password != null && !password.isEmpty()) tries.add(password.replace("\r", "").replace("\n", ""));
        AgvnUnpackException wrong = null;
        for (String attempt : tries) {
            try {
                Result result = route(volumes, attempt, gamesRoot, sevenZip, listener, freeBytes);
                result.archiveFiles = volumes.files;
                return result;
            } catch (AgvnUnpackException e) {
                if (e.reason != Reason.WRONG_PASSWORD) throw e;
                wrong = e;
            }
        }
        throw wrong;
    }

    /** Java's readers first for ZIP and 7z; 7-Zip's program for the rest and for what they cannot read. */
    private static Result route(AgvnArchiveKind.Volumes volumes, String password, File gamesRoot, File sevenZip, Listener listener,
                                long freeBytes) throws AgvnUnpackException {
        boolean java = volumes.type == AgvnArchiveKind.Type.ZIP || volumes.type == AgvnArchiveKind.Type.SEVEN_Z;
        if (!java) return AgvnUnpack7zz.extract(sevenZip, volumes, password, gamesRoot, listener, freeBytes);
        boolean[] opened = {false};
        try {
            return extractOnce(volumes, password, gamesRoot, listener, freeBytes, opened);
        } catch (AgvnUnpackException e) {
            // a method Java cannot read (Deflate64, LZMA, zstd; PPMd, BCJ2), or a ZIP zip4j cannot open at all
            boolean again = e.reason == Reason.UNSUPPORTED_METHOD
                    || (e.reason == Reason.DAMAGED && !opened[0] && volumes.type == AgvnArchiveKind.Type.ZIP);
            if (listener.cancelled()) throw new AgvnUnpackException(Reason.CANCELLED);
            if (!again || sevenZip == null || !sevenZip.isFile()) throw e;
            try {
                return AgvnUnpack7zz.extract(sevenZip, volumes, password, gamesRoot, listener, freeBytes);
            } catch (AgvnUnpackException second) {
                // 7-Zip could not open it either: what Java found stands (it names the part that is likely missing)
                boolean same = second.reason == Reason.NOT_ARCHIVE || (second.reason == Reason.DAMAGED && second.detail == null)
                        || (second.reason == Reason.MISSING_PART && second.detail == null);
                throw same && e.reason != Reason.UNSUPPORTED_METHOD ? e : second;
            }
        }
    }

    private static Result extractOnce(AgvnArchiveKind.Volumes volumes, String password, File gamesRoot, Listener listener,
                                      long freeBytes, boolean[] opened) throws AgvnUnpackException {
        char[] key = password != null ? password.toCharArray() : null;
        AgvnArchiveReader reader;
        try {
            reader = volumes.type == AgvnArchiveKind.Type.ZIP ? new AgvnUnpackZip(volumes.first, key)
                    : new AgvnUnpack7z(volumes.parts.isEmpty() ? Collections.singletonList(volumes.first) : volumes.parts, key);
            opened[0] = true;
        } catch (AgvnUnpackException e) {
            // the end of a ZIP or 7z lists its files: a set whose last part is not there cannot be opened
            String next = e.reason == Reason.DAMAGED ? volumes.likelyMissingNext() : null;
            if (next != null) throw new AgvnUnpackException(Reason.MISSING_PART, next, 0, e);
            throw e;
        }
        File partial = null;
        try {
            List<Item> items = reader.items();
            List<String> paths = new ArrayList<>(items.size());
            long total = 0;
            boolean locked = false;
            for (Item item : items) {
                paths.add(AgvnArchiveNames.clean(item.path));
                total += item.size;
                locked |= item.encrypted;
            }
            if (locked && key == null) throw new AgvnUnpackException(Reason.NEED_PASSWORD);
            AgvnUnpackFiles.checkRoom(total, gamesRoot, freeBytes);

            String top = AgvnArchiveNames.singleTop(paths);
            String name = AgvnArchiveNames.folderName(top != null ? top : volumes.baseName);
            partial = AgvnUnpackFiles.begin(gamesRoot, name);

            long done = 0;
            int files = 0, rejected = 0;
            boolean verified = false;
            byte[] buffer = new byte[1 << 16];
            for (int i = 0; i < items.size(); i++) {
                Item item = items.get(i);
                if (listener.cancelled()) throw new AgvnUnpackException(Reason.CANCELLED);
                String path = paths.get(i);
                if (path != null && top != null) path = path.length() > top.length() ? path.substring(top.length() + 1) : "";
                if (path != null && path.isEmpty()) continue; // the top folder's own entry
                File out = AgvnArchiveNames.target(partial, path);
                if (out == null) {
                    rejected++;
                    continue;
                }
                if (item.dir) {
                    out.mkdirs();
                    continue;
                }
                File parent = out.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) throw new AgvnUnpackException(Reason.CANNOT_WRITE, path);
                listener.onProgress(done, total, path);
                done = AgvnUnpackFiles.copy(reader, i, item, out, buffer, listener, done, total, path, verified, gamesRoot);
                verified |= item.encrypted;
                files++;
            }
            listener.onProgress(total, total, "");
            reader.close();
            File dir = AgvnArchiveNames.freeFolder(gamesRoot, name);
            if (!partial.renameTo(dir)) throw new AgvnUnpackException(Reason.CANNOT_WRITE, dir.getPath());
            partial = null;
            return new Result(dir, files, rejected);
        } catch (OutOfMemoryError e) { // a dictionary bigger than the app's memory
            throw new AgvnUnpackException(Reason.UNSUPPORTED_METHOD, null, 0, e);
        } finally {
            reader.close();
            if (partial != null) AgvnUnpackFiles.delete(partial);
        }
    }
}
