/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.agvn.AgvnUnpackException.Reason;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Unpacks a series of archives ({@link AgvnArchiveSeries}: name.part1.zip, name.part2.zip...) into one game folder, as
 * "extract all here" would on a PC: each archive in turn, with the same password, into a hidden folder; then their
 * content is put together (a file two archives hold: the later one's) and the folder gets its name. Anything that
 * fails, or "Hủy", removes all of it. The progress is the series': by the size of the archives done. Pure Java
 * (JVM-testable).
 */
final class AgvnUnpackSeries {
    private static final String PART = ".agvn-phan-", GAME = ".agvn-game";

    private AgvnUnpackSeries() {}

    static AgvnUnpack.Result extract(AgvnArchiveSeries series, String password, File gamesRoot, File sevenZip,
                                     AgvnUnpack.Listener listener, long freeBytes) throws AgvnUnpackException {
        if (series.missing != null) throw new AgvnUnpackException(Reason.MISSING_PART, series.missing);
        long all = 0;
        for (File archive : series.archives) all += archive.length();
        long total = Math.max(1, all);
        String name = AgvnArchiveNames.folderName(series.baseName);
        File staging = AgvnUnpackFiles.begin(gamesRoot, name);
        try {
            List<File> dirs = new ArrayList<>();
            List<String> tops = new ArrayList<>();
            int files = 0, rejected = 0;
            long before = 0;
            for (File archive : series.archives) {
                if (listener.cancelled()) throw new AgvnUnpackException(Reason.CANCELLED);
                long start = before, size = archive.length();
                AgvnUnpack.Listener part = new AgvnUnpack.Listener() {
                    @Override
                    public void onProgress(long doneBytes, long totalBytes, String file) {
                        double share = totalBytes > 0 ? Math.min(1.0, (double) doneBytes / totalBytes) : 0;
                        listener.onProgress(start + (long) (share * size), total, file);
                    }

                    @Override
                    public boolean cancelled() {
                        return listener.cancelled();
                    }
                };
                AgvnUnpack.Result r = AgvnUnpack.extract(AgvnArchiveSeries.single(archive, series.baseName), password, staging,
                        sevenZip, part, freeBytes);
                // out of the way of the next archive, which may unpack to the same name
                File kept = new File(staging, PART + dirs.size());
                if (!r.dir.renameTo(kept)) throw new AgvnUnpackException(Reason.CANNOT_WRITE, r.dir.getPath());
                dirs.add(kept);
                tops.add(r.top);
                files += r.files;
                rejected += r.rejected;
                before += size;
            }
            // every archive has the same top folder: it is the game's folder, as for one archive. Else what an archive
            // had in a top folder stays in it, beside what the others had loose.
            String top = tops.get(0);
            for (String t : tops) if (t == null || top == null || !t.equalsIgnoreCase(top)) top = null;
            File game = new File(staging, GAME);
            if (!game.mkdirs()) throw new AgvnUnpackException(Reason.CANNOT_WRITE, game.getPath());
            for (int i = 0; i < dirs.size(); i++) {
                new File(dirs.get(i), AgvnUnpack.NOMEDIA).delete();
                move(dirs.get(i), top != null || tops.get(i) == null ? game : new File(game, tops.get(i)));
            }
            AgvnUnpackFiles.touch(new File(game, AgvnUnpack.NOMEDIA));
            listener.onProgress(total, total, "");
            File dir = AgvnArchiveNames.freeFolder(gamesRoot, top != null ? AgvnArchiveNames.folderName(top) : name);
            if (!game.renameTo(dir)) throw new AgvnUnpackException(Reason.CANNOT_WRITE, dir.getPath());
            AgvnUnpack.Result result = new AgvnUnpack.Result(dir, files, rejected);
            result.archiveFiles = series.archives;
            result.top = top;
            return result;
        } finally {
            AgvnUnpackFiles.delete(staging);
        }
    }

    /** What {@code from} holds goes into {@code to}: folders both have are put together, a file both have is replaced. */
    static void move(File from, File to) throws AgvnUnpackException {
        File[] children = from.listFiles();
        if (children == null) return;
        if (!to.isDirectory() && !to.mkdirs()) throw new AgvnUnpackException(Reason.CANNOT_WRITE, to.getPath());
        for (File child : children) {
            File target = new File(to, child.getName());
            if (child.isDirectory() && target.isDirectory()) {
                move(child, target);
                continue;
            }
            if (target.exists()) AgvnUnpackFiles.delete(target);
            if (!child.renameTo(target)) throw new AgvnUnpackException(Reason.CANNOT_WRITE, target.getPath());
        }
    }
}
