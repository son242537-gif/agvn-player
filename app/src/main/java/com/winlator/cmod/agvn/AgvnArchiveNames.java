/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Names for unpacking an archive: the charset of the file names of an old ZIP, where each entry goes (never outside the
 * folder being written), whether the archive already has one folder of its own, and a folder name that is still free.
 * Pure Java (JVM-testable).
 */
public final class AgvnArchiveNames {
    /** Marks the folder an archive is being unpacked into; the dot in front keeps "Thêm game" and the Gallery out of it. */
    public static final String PARTIAL_SUFFIX = ".agvn-dang-giai-nen";
    /** A lone top folder with one of these names is part of the game ("www" of RPG Maker MV), not the game's folder. */
    private static final Set<String> STRUCTURAL = new HashSet<>(Arrays.asList(
            "www", "game", "data", "renpy", "lib", "bin", "content", "engine", "resources", "assets", "audio", "img", "js"));
    /** Characters shared storage refuses in a file name. */
    private static final String FORBIDDEN = "\"*:<>?|";

    private AgvnArchiveNames() {}

    /**
     * The charset of the names a ZIP stores without the UTF-8 flag, decided for all of them together ({@code names}:
     * their raw bytes). Valid UTF-8 is UTF-8; else Shift-JIS (CP932) or GBK when the bytes are valid there (Shift-JIS
     * unless it would give half-width katakana, the sign of Chinese text read as Japanese); else CP437, ZIP's own.
     */
    public static Charset charsetFor(List<byte[]> names) {
        if (allDecode(names, StandardCharsets.UTF_8)) return StandardCharsets.UTF_8;
        Charset sjis = charset("windows-31j", "Shift_JIS"), gbk = charset("GBK", "GB2312");
        boolean japanese = sjis != null && allDecode(names, sjis), chinese = gbk != null && allDecode(names, gbk);
        if (japanese && chinese && halfWidthKana(names, sjis)) return gbk;
        if (japanese) return sjis;
        if (chinese) return gbk;
        Charset dos = charset("IBM437", "Cp437");
        return dos != null ? dos : StandardCharsets.ISO_8859_1;
    }

    private static Charset charset(String... tries) {
        for (String name : tries) {
            try {
                return Charset.forName(name);
            } catch (Exception ignored) {
                // not on this phone
            }
        }
        return null;
    }

    private static boolean allDecode(List<byte[]> names, Charset cs) {
        for (byte[] name : names) if (strict(name, cs) == null) return false;
        return true;
    }

    private static String strict(byte[] bytes, Charset cs) {
        try {
            return cs.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static boolean halfWidthKana(List<byte[]> names, Charset sjis) {
        for (byte[] name : names) {
            String s = strict(name, sjis);
            if (s == null) continue;
            for (int i = 0; i < s.length(); i++) if (s.charAt(i) >= 0xFF61 && s.charAt(i) <= 0xFF9F) return true;
        }
        return false;
    }

    /**
     * An entry's path as folders and a file name, "/" between them: "\" also separates (old Windows ZIPs), a drive
     * letter, leading "/" and "." are dropped, characters shared storage refuses become "_". Null for an entry that
     * climbs out with ".." or has no name: it is not unpacked.
     */
    public static String clean(String entry) {
        if (entry == null) return null;
        String path = entry.replace('\\', '/');
        if (path.length() >= 2 && path.charAt(1) == ':' && Character.isLetter(path.charAt(0))) path = path.substring(2);
        StringBuilder out = new StringBuilder();
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) return null;
            StringBuilder name = new StringBuilder(part.length());
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                name.append(c < 0x20 || c == 0x7F || FORBIDDEN.indexOf(c) >= 0 ? '_' : c);
            }
            if (out.length() > 0) out.append('/');
            out.append(name);
        }
        return out.length() == 0 ? null : out.toString();
    }

    /** The file of a cleaned path below {@code root}, or null when it would land outside it (zip-slip). */
    public static File target(File root, String cleanPath) {
        if (cleanPath == null) return null;
        File out = new File(root, cleanPath.replace('/', File.separatorChar));
        try {
            String base = root.getCanonicalPath() + File.separator;
            return out.getCanonicalPath().startsWith(base) ? out : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * The one top folder all entries sit in ({@code cleanPaths}: cleaned paths, nulls skipped), which then is the
     * game's folder: its content is unpacked without it, so the game is not two folders deep. Null when files lie loose
     * at the top, there are several top folders, or the folder is a part of the game ({@link #STRUCTURAL}).
     */
    public static String singleTop(List<String> cleanPaths) {
        String top = null;
        boolean inside = false;
        for (String path : cleanPaths) {
            if (path == null) continue;
            int slash = path.indexOf('/');
            String first = slash < 0 ? path : path.substring(0, slash);
            if (top == null) top = first;
            else if (!top.equals(first)) return null;
            if (slash >= 0) inside = true;
        }
        // "inside": the top name is a folder; a lone file at the top is no folder
        if (top == null || !inside || STRUCTURAL.contains(top.toLowerCase(Locale.ROOT))) return null;
        return top;
    }

    /** A folder name from an archive's name or its top folder: cleaned, no dot or space at either end, never empty. */
    public static String folderName(String raw) {
        String name = clean(raw == null ? "" : raw.replace('/', '_').replace('\\', '_'));
        if (name == null) name = "";
        name = name.replaceAll("^[.\\s]+", "").replaceAll("[.\\s]+$", "");
        if (name.endsWith(PARTIAL_SUFFIX)) name = name.substring(0, name.length() - PARTIAL_SUFFIX.length());
        return name.isEmpty() ? "Game" : name;
    }

    /** {@code name} in {@code parent} when nothing has that name, else "name (2)", "name (3)": a game is never overwritten. */
    public static File freeFolder(File parent, String name) {
        File dir = new File(parent, name);
        for (int n = 2; dir.exists(); n++) dir = new File(parent, name + " (" + n + ")");
        return dir;
    }

    /** The hidden folder {@code name} is unpacked into before it gets its name. */
    public static File partialFolder(File parent, String name) {
        return new File(parent, "." + name + PARTIAL_SUFFIX);
    }

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
}
