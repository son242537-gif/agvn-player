/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Electron's app.asar, read in place for "Chạy nhẹ" ({@link AgvnElectronGame}): an Electron game ships its pages in it,
 * and unpacking them would take as much room again on the phone. The file starts with two pickles: [uint32 4][uint32 n],
 * then n bytes holding [uint32][uint32 length][the JSON header]. Each file of the header has a "size" and an "offset"
 * (a string) from the end of those n bytes, or is "unpacked" into app.asar.unpacked/ beside it. Paths are found
 * ignoring case, as on Windows. Pure Java (JVM-testable).
 */
final class AgvnAsar {
    static final int MAX_HEADER = 64 << 20;
    private static AgvnAsar last;

    final File file;
    private final long base, length, modified;
    /** By lower-case path ("src/index.html"): {real path, offset, size}; offset -1 for an unpacked file. */
    private final Map<String, Object[]> files = new HashMap<>();

    private AgvnAsar(File file, long base) {
        this.file = file;
        this.base = base;
        this.length = file.length();
        this.modified = file.lastModified();
    }

    /** {@code file} read, or the one read last when it is the same file unchanged; null when it is no asar. */
    static synchronized AgvnAsar of(File file) {
        if (last != null && last.file.equals(file) && last.length == file.length() && last.modified == file.lastModified())
            return last;
        try {
            last = open(file);
        } catch (IOException | RuntimeException e) {
            return null;
        }
        return last;
    }

    static AgvnAsar open(File file) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            byte[] head = new byte[16];
            in.readFully(head);
            if (le(head, 0) != 4) throw new IOException("not an asar: " + file);
            long size = le(head, 4), json = le(head, 12);
            if (size > MAX_HEADER || json > size - 8 || 8 + size > in.length()) throw new IOException("bad asar header: " + file);
            byte[] text = new byte[(int) json];
            in.readFully(text);
            AgvnAsar asar = new AgvnAsar(file, 8 + size);
            JsonObject root = JsonParser.parseString(new String(text, StandardCharsets.UTF_8)).getAsJsonObject();
            asar.add("", root);
            return asar;
        }
    }

    private void add(String prefix, JsonObject dir) {
        JsonElement children = dir.get("files");
        if (children == null || !children.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> e : children.getAsJsonObject().entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject node = e.getValue().getAsJsonObject();
            String path = prefix + e.getKey();
            if (node.has("files")) {
                add(path + "/", node);
            } else if (node.has("size")) {
                boolean unpacked = node.has("unpacked") && node.get("unpacked").getAsBoolean();
                long offset = unpacked ? -1 : node.has("offset") ? Long.parseLong(node.get("offset").getAsString()) : 0;
                files.put(path.toLowerCase(Locale.ROOT), new Object[]{path, offset, node.get("size").getAsLong()});
            }
        }
    }

    /** The real path of {@code path} ("/SRC/./Index.html" finds "src/index.html"), or null; never above the archive. */
    String find(String path) {
        StringBuilder clean = new StringBuilder();
        for (String part : path.replace('\\', '/').split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) return null;
            if (clean.length() > 0) clean.append('/');
            clean.append(part);
        }
        Object[] f = files.get(clean.toString().toLowerCase(Locale.ROOT));
        return f != null ? (String) f[0] : null;
    }

    long size(String realPath) {
        return (long) files.get(realPath.toLowerCase(Locale.ROOT))[2];
    }

    /** The bytes of {@code realPath} (a path {@link #find} gave). */
    InputStream open(String realPath) throws IOException {
        Object[] f = files.get(realPath.toLowerCase(Locale.ROOT));
        long offset = (long) f[1], size = (long) f[2];
        if (offset < 0) return new FileInputStream(new File(file.getPath() + ".unpacked", realPath));
        FileInputStream in = new FileInputStream(file);
        in.getChannel().position(base + offset);
        return new FilterInputStream(in) {
            long left = size;

            @Override
            public int read() throws IOException {
                if (left <= 0) return -1;
                int b = super.read();
                if (b >= 0) left--;
                return b;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (left <= 0) return -1;
                int n = super.read(b, off, (int) Math.min(len, left));
                if (n > 0) left -= n;
                return n;
            }

            @Override
            public long skip(long n) throws IOException {
                long s = super.skip(Math.min(n, left));
                left -= s;
                return s;
            }

            @Override
            public int available() throws IOException {
                return (int) Math.min(super.available(), left);
            }
        };
    }

    /** At most {@code max} bytes of {@code path} as UTF-8 text, or null when it is not there. */
    String text(String path, int max) {
        String real = find(path);
        if (real == null) return null;
        try (InputStream in = open(real)) {
            byte[] buf = new byte[(int) Math.min(size(real), max)];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            return new String(buf, 0, n, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** The archive {@code inside} ("…/app.asar/src/index.html") lies in, or null when it lies in none. */
    static File archiveOf(File inside) {
        for (File f = inside; f != null; f = f.getParentFile()) {
            if (f.getName().toLowerCase(Locale.ROOT).endsWith(".asar")) return f.isFile() ? f : null;
        }
        return null;
    }

    /** {@code inside}'s path in {@code archive} ("src/index.html"). */
    static String pathIn(File archive, File inside) {
        String a = archive.getPath(), p = inside.getPath();
        return p.length() > a.length() ? p.substring(a.length() + 1).replace(File.separatorChar, '/') : "";
    }

    private static long le(byte[] b, int at) {
        return (b[at] & 0xffL) | (b[at + 1] & 0xffL) << 8 | (b[at + 2] & 0xffL) << 16 | (b[at + 3] & 0xffL) << 24;
    }
}
