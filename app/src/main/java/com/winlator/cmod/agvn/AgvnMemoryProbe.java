/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.system.Os;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/**
 * Memory figures from /proc. Android shows an app only its own processes there, which here are the app and every
 * Wine process of the game (same uid). DMA-BUF buffers (graphics memory the Vulkan wrapper takes through gralloc) are
 * counted once each, by inode, from /proc/&lt;pid&gt;/fdinfo, since several processes can hold the same buffer.
 */
final class AgvnMemoryProbe {
    static final class Usage {
        final long rssMb, dmabufMb;

        Usage(long rssMb, long dmabufMb) {
            this.rssMb = rssMb;
            this.dmabufMb = dmabufMb;
        }
    }

    private AgvnMemoryProbe() {}

    /** MemAvailable in MB, or -1 when unreadable. */
    static long freeMb() {
        long kb = kb(read(new File("/proc/meminfo")), "MemAvailable:");
        return kb < 0 ? -1 : kb / 1024;
    }

    /** Resident memory of this process (VmRSS) in MB, or -1: the game's own, for an engine in a process of its own. */
    static long processMb() {
        long kb = kb(read(new File("/proc/self/status")), "VmRSS:");
        return kb < 0 ? -1 : kb / 1024;
    }

    /** The phone's RAM (MemTotal) in MB, or 0 when unreadable. */
    static long totalMb() {
        long kb = kb(read(new File("/proc/meminfo")), "MemTotal:");
        return kb < 0 ? 0 : kb / 1024;
    }

    /** Resident memory of all our processes, and the DMA-BUF buffers they hold. */
    static Usage appUsage() {
        long rssKb = 0, dmabufBytes = 0;
        Set<Long> seen = new HashSet<>();
        File[] procs = new File("/proc").listFiles((d, name) -> name.matches("\\d+"));
        if (procs == null) return new Usage(0, 0);
        for (File proc : procs) {
            long rss = kb(read(new File(proc, "status")), "VmRSS:");
            if (rss > 0) rssKb += rss;
            String[] fds = new File(proc, "fd").list();
            if (fds == null) continue;
            for (String fd : fds) {
                try {
                    if (!Os.readlink(proc.getPath() + "/fd/" + fd).contains("dmabuf")) continue;
                } catch (Exception e) {
                    continue; // the fd closed meanwhile
                }
                long[] buf = dmabuf(read(new File(proc, "fdinfo/" + fd)));
                if (buf != null && (buf[1] < 0 || seen.add(buf[1]))) dmabufBytes += buf[0]; // no inode (old kernel): no dedupe
            }
        }
        return new Usage(rssKb / 1024, dmabufBytes >> 20);
    }

    /** The number after {@code key} in a "Key:   123 kB" line, or -1. */
    static long kb(String text, String key) {
        for (String line : text.split("\n")) {
            if (!line.startsWith(key)) continue;
            String digits = line.substring(key.length()).replaceAll("[^0-9]", "");
            return digits.isEmpty() ? -1 : Long.parseLong(digits);
        }
        return -1;
    }

    /** {size in bytes, inode or -1} of a DMA-BUF fdinfo ("size:", "ino:", "exp_name:" lines), or null for other fds. */
    static long[] dmabuf(String fdinfo) {
        long size = -1, ino = -1;
        boolean exported = false;
        for (String line : fdinfo.split("\n")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 2) continue;
            try {
                if (parts[0].equals("size:")) size = Long.parseLong(parts[1]);
                else if (parts[0].equals("ino:")) ino = Long.parseLong(parts[1]);
                else if (parts[0].equals("exp_name:")) exported = true;
            } catch (NumberFormatException ignored) {
                // not a number: not the line we want
            }
        }
        return size >= 0 && (ino >= 0 || exported) ? new long[]{size, ino} : null;
    }

    private static String read(File f) {
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }
}
