/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Engine modules a game's own Godot was built with and AGVN's Godot for Android lacks, known by the files their
 * importers leave in the pack. Mii Chan (Godot 4.5.1 with spine-godot built in, 08/10/2026) ran its menus on "Chạy
 * nhẹ" but none of its Spine scenes ("Cannot get class 'SpineSkeletonDataResource'"), so a Spine game stays on
 * Windows. The session log says which Spine the game was exported from: spine-godot reads only skeletons of its own
 * version ("Skeleton version 4.3.x does not match runtime version 4.2"), so an engine with Spine would need that one.
 * Spine as a GDExtension is in extension_list.cfg instead ({@link AgvnGodotPack#extensions}). Pure Java.
 */
final class AgvnGodotModules {
    private static final Pattern JSON_VERSION = Pattern.compile("\"spine\"\\s*:\\s*\"([0-9][0-9A-Za-z.\\-]*)\"");
    private static final int HEAD = 1024;

    private AgvnGodotModules() {}

    /** What {@code pack} needs that AGVN's Godot lacks ("Spine 4.2.43"), or null when it needs nothing it lacks. */
    static String missing(AgvnGodotPack pack) {
        if (!usesSpine(pack)) return null;
        String version = spineVersion(pack);
        return "Spine " + (version != null ? version : "(chưa rõ bản)");
    }

    /** For the session log of a Godot game with Spine: which Spine, and why it is not on Chạy nhẹ; null without. */
    static String fact(AgvnGodotPack pack) {
        String spine = missing(pack);
        return spine == null ? null : "Game Godot dùng " + spine + ": Chạy nhẹ chưa có Spine, game chạy bằng Windows";
    }

    /** True when the pack holds spine-godot's imported atlases or skeletons. */
    static boolean usesSpine(AgvnGodotPack pack) {
        if (pack == null) return false;
        for (AgvnGodotPack.Entry e : pack.entries) if (spineFile(e.path) != null) return true;
        return false;
    }

    /** The version the first readable skeleton of {@code pack} was exported from, or null. */
    static String spineVersion(AgvnGodotPack pack) {
        if (pack == null) return null;
        for (AgvnGodotPack.Entry e : pack.entries) {
            String kind = spineFile(e.path);
            if (kind == null || kind.equals("spatlas")) continue;
            byte[] head = read(pack.file, e.offset, (int) Math.min(HEAD, e.size));
            String version = head == null ? null : kind.equals("spskel") ? binaryVersion(head) : jsonVersion(head);
            if (version != null) return version;
        }
        return null;
    }

    /** A binary skeleton (spine-cpp SkeletonBinary): an 8-byte hash, then the version: varint length + 1, UTF-8. */
    static String binaryVersion(byte[] b) {
        if (b.length < 10) return null;
        int at = 8, length = 0;
        for (int shift = 0; shift <= 28 && at < b.length; shift += 7) {
            int v = b[at++] & 0xff;
            length |= (v & 0x7f) << shift;
            if ((v & 0x80) == 0) break;
        }
        length--; // 0 is null, so a string's length is written plus one
        if (length < 1 || length > 32 || at + length > b.length) return null;
        String version = new String(b, at, length, StandardCharsets.UTF_8);
        return version.matches("[0-9]+\\.[0-9]+.*") ? version : null;
    }

    /** A JSON skeleton: its "spine" field ({"skeleton": {"hash": ..., "spine": "4.2.43", ...}}). */
    static String jsonVersion(byte[] b) {
        Matcher m = JSON_VERSION.matcher(new String(b, StandardCharsets.UTF_8));
        return m.find() ? m.group(1) : null;
    }

    /** "spatlas", "spskel" or "spjson" for a file spine-godot imported, else null. */
    private static String spineFile(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (!p.startsWith(".godot/imported/")) return null;
        for (String ext : new String[]{"spatlas", "spskel", "spjson"}) if (p.endsWith("." + ext)) return ext;
        return null;
    }

    private static byte[] read(File file, long at, int count) {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            if (count <= 0 || at < 0 || at + count > in.length()) return null;
            byte[] b = new byte[count];
            in.seek(at);
            in.readFully(b);
            return b;
        } catch (IOException e) {
            return null;
        }
    }
}
