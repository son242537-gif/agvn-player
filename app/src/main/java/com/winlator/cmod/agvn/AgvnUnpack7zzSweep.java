/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * After 7-Zip unpacked into the hidden folder: removes whatever an archive must not leave there. 7-Zip is told to make
 * no links and the link entries are left out, so this finds nothing; it is the check that does not depend on that.
 * Removed: symbolic links, files with a second name (hard links), anything that is neither file nor folder, anything
 * whose real path is outside the folder.
 */
final class AgvnUnpack7zzSweep {
    /** Files kept. */
    int files;
    /** Entries removed. */
    int removed;
    private final String base;

    private AgvnUnpack7zzSweep(File root) throws IOException {
        base = root.getCanonicalPath() + File.separator;
    }

    static AgvnUnpack7zzSweep run(File root) throws IOException {
        AgvnUnpack7zzSweep sweep = new AgvnUnpack7zzSweep(root);
        sweep.walk(root);
        return sweep;
    }

    private void walk(File dir) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            Path path = child.toPath();
            if (Files.isSymbolicLink(path) || !inside(child)) {
                child.delete(); // the link itself, never what it points to
                removed++;
            } else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                walk(child);
            } else if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || links(path) > 1) {
                child.delete();
                removed++;
            } else {
                files++;
            }
        }
    }

    private boolean inside(File file) {
        try {
            return file.getCanonicalPath().startsWith(base);
        } catch (IOException e) {
            return false;
        }
    }

    /** How many names the file has; 1 where the file system cannot tell (Windows, some phones' shared storage). */
    private static int links(Path path) {
        try {
            Object n = Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
            return n instanceof Number ? ((Number) n).intValue() : 1;
        } catch (IOException | RuntimeException e) {
            return 1;
        }
    }
}
