/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.agvn.AgvnUnpack7zzList.Entry;
import com.winlator.cmod.agvn.AgvnUnpackException.Reason;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An archive unpacked by 7-Zip's own program, run as a separate process ({@link AgvnUnpack7zzProcess}): RAR and RAR5
 * (locked ones and sets too), TAR and .tar.gz/.xz/.zst/.bz2, and the ZIPs and 7z the Java readers cannot read
 * (Deflate64, LZMA, zstd, PPMd, BCJ2). The same rules as {@link AgvnUnpack}: the list is read first, entries that
 * would leave the folder and links are left out, the work is done in the hidden folder and gets its name when complete.
 * Pure Java (JVM-testable with a 7zz of the build machine).
 */
final class AgvnUnpack7zz {
    private AgvnUnpack7zz() {}

    /** {@code program}: 7-Zip's console program; {@code password}: null when none was given. */
    static AgvnUnpack.Result extract(File program, AgvnArchiveKind.Volumes volumes, String password, File gamesRoot,
                                     AgvnUnpack.Listener listener, long freeBytes) throws AgvnUnpackException {
        if (program == null || !program.isFile()) throw new AgvnUnpackException(Reason.UNSUPPORTED_METHOD);
        String exe = program.getAbsolutePath(), archive = volumes.first.getAbsolutePath();
        // always given, empty too: without it 7-Zip would ask for the password and wait
        String key = "-p" + (password != null ? password : "");
        boolean wrapped = volumes.tarWrapped, set = volumes.files.size() > 1;
        // a packed TAR: the first program unpacks it to its output, the second reads the TAR from its input
        List<String> unwrap = wrapped ? Arrays.asList(exe, "x", "-so", "-bsp2", "-bso0", "-sccUTF-8", key, "--", archive) : null;
        File partial = null, skip = null;
        try {
            AtomicReference<List<Entry>> listed = new AtomicReference<>();
            List<String> list = new ArrayList<>(Arrays.asList(exe, "l", "-slt", "-ba", "-sccUTF-8"));
            list.addAll(wrapped ? Arrays.asList("-si", "-ttar", "-p") : Arrays.asList(key, "--", archive));
            AgvnUnpack7zzProcess.Outcome run = AgvnUnpack7zzProcess.run(wrapped ? unwrap : list, wrapped ? list : null,
                    in -> listed.set(AgvnUnpack7zzList.parse(in)), null, null, listener);
            List<Entry> entries = listed.get() != null ? listed.get() : new ArrayList<>();
            if (run.cancelled) throw new AgvnUnpackException(Reason.CANCELLED);
            // a RAR set with a volume missing lists what it has and says nothing: unpacking then names the volume
            if (!AgvnUnpack7zzErrors.ok(run.exit, run.errors) && (entries.isEmpty() || !run.errors.trim().isEmpty()))
                throw failure(run, password, null, volumes, gamesRoot);

            List<String> paths = new ArrayList<>(entries.size()), skipped = new ArrayList<>();
            long total = 0;
            boolean locked = false;
            for (Entry e : entries) {
                String path = e.link ? null : AgvnArchiveNames.clean(e.path);
                if (path == null) {
                    skipped.add(e.path);
                    continue;
                }
                paths.add(path);
                total += e.size;
                locked |= e.encrypted;
            }
            if (locked && password == null) throw new AgvnUnpackException(Reason.NEED_PASSWORD);
            AgvnUnpackFiles.checkRoom(total, gamesRoot, freeBytes);
            String top = AgvnArchiveNames.singleTop(paths);
            String name = AgvnArchiveNames.folderName(top != null ? top : volumes.baseName);
            partial = AgvnUnpackFiles.begin(gamesRoot, name);

            // -snl- -snh-: no symbolic links, no hard-link bookkeeping; -y: never asks
            List<String> unpack = new ArrayList<>(Arrays.asList(exe, "x", "-y", wrapped ? "-bsp0" : "-bsp1", "-bso0", "-sccUTF-8",
                    "-snl-", "-snh-", "-o" + partial.getAbsolutePath()));
            if (!skipped.isEmpty()) {
                skip = skipList(skipped);
                unpack.addAll(Arrays.asList("-scsUTF-8", "-x@" + skip.getAbsolutePath()));
            }
            unpack.addAll(wrapped ? Arrays.asList("-si", "-ttar", "-p") : Arrays.asList(key, "--", archive));
            long bytes = total;
            String[] shown = {""};
            AgvnUnpack7zzConsole.Progress progress = (percent, file) -> {
                if (file != null) shown[0] = file;
                listener.onProgress(bytes * percent / 100, bytes, shown[0]);
            };
            listener.onProgress(0, total, "");
            run = AgvnUnpack7zzProcess.run(wrapped ? unwrap : unpack, wrapped ? unpack : null, null, progress, progress, listener);
            if (run.cancelled || listener.cancelled()) throw new AgvnUnpackException(Reason.CANCELLED);
            if (!AgvnUnpack7zzErrors.ok(run.exit, run.errors)) throw failure(run, password, entries, volumes, gamesRoot);

            AgvnUnpack7zzSweep sweep = AgvnUnpack7zzSweep.run(partial);
            // the archive's one top folder is the game's folder: it gets the name, the hidden folder around it goes
            File game = partial, inner = top != null ? new File(partial, top) : null;
            String[] left = partial.list();
            if (inner != null && inner.isDirectory() && left != null && left.length == 2) game = inner;
            if (game != partial) AgvnUnpackFiles.touch(new File(game, AgvnUnpack.NOMEDIA));
            listener.onProgress(total, total, "");
            File dir = AgvnArchiveNames.freeFolder(gamesRoot, name);
            if (!game.renameTo(dir)) throw new AgvnUnpackException(Reason.CANNOT_WRITE, dir.getPath());
            if (game == partial) partial = null;
            AgvnUnpack.Result result = new AgvnUnpack.Result(dir, sweep.files - 1, skipped.size() + sweep.removed); // less the .nomedia
            result.top = game == inner ? top : null;
            return result;
        } catch (IOException e) { // the program could not be started
            throw new AgvnUnpackException(Reason.UNSUPPORTED_METHOD, null, 0, e);
        } finally {
            if (skip != null) skip.delete();
            if (partial != null) AgvnUnpackFiles.delete(partial);
        }
    }

    /** The entries 7-Zip is to leave out, one stored path per line (it reads them as its -x list). */
    private static File skipList(List<String> skipped) throws IOException {
        File file = File.createTempFile("agvn-7zz-", ".txt");
        try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            for (String path : skipped) if (path.indexOf('\n') < 0 && path.indexOf('\r') < 0 && !path.isEmpty()) w.write(path + "\n");
        }
        return file;
    }

    private static AgvnUnpackException failure(AgvnUnpack7zzProcess.Outcome run, String password, List<Entry> entries,
                                               AgvnArchiveKind.Volumes volumes, File gamesRoot) {
        AgvnUnpackException e = AgvnUnpack7zzErrors.classify(run.errors, run.exit, password != null, entries, volumes.files.size() > 1);
        switch (e.reason) {
            case NO_SPACE:
                return new AgvnUnpackException(Reason.NO_SPACE, null, Math.max(1L << 20, AgvnUnpack.SPARE_BYTES - gamesRoot.getUsableSpace()), null);
            case MISSING_PART:
                return e.detail != null ? e : new AgvnUnpackException(Reason.MISSING_PART, volumes.likelyMissingNext());
            case DAMAGED: // the name 7-Zip gives for a packed TAR is the TAR's, not a file of the game
                return volumes.tarWrapped && e.detail != null ? new AgvnUnpackException(Reason.DAMAGED) : e;
            default:
                return e;
        }
    }
}
