/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The file on shared storage that a content address from another app stands for, as far as the address itself tells:
 * Android's own file pickers put the place in a document id ("primary:Download/Game.zip"), file managers and browsers
 * often put the path, or a part of it, in the address. What is only likely ({@link #guesses}) is checked against the
 * file's name and size by {@link AgvnOpenArchivePath} before it is used. Pure Java (JVM-testable).
 */
final class AgvnUriPath {
    static final String EXTERNAL_STORAGE = "com.android.externalstorage.documents";
    static final String DOWNLOADS = "com.android.providers.downloads.documents";
    static final String MEDIA = "com.android.providers.media.documents";

    private AgvnUriPath() {}

    /**
     * The path a document id of Android's storage or downloads picker names, or null: "primary:Download/Game.zip"
     * (internal storage, {@code internalRoot}), "1A2B-3C4D:Games/Game.zip" (an SD card), "raw:/storage/…/Game.zip".
     */
    static String fromDocumentId(String authority, String docId, String internalRoot) {
        if (authority == null || docId == null) return null;
        int colon = docId.indexOf(':');
        if (colon <= 0) return null;
        String kind = docId.substring(0, colon), rest = docId.substring(colon + 1);
        if (climbs(rest)) return null;
        if (DOWNLOADS.equals(authority)) return kind.equals("raw") && rest.startsWith("/") ? rest : null;
        if (!EXTERNAL_STORAGE.equals(authority) || rest.isEmpty()) return null;
        if (kind.equalsIgnoreCase("primary")) return join(internalRoot, rest);
        if (kind.equalsIgnoreCase("home")) return join(internalRoot + "/Documents", rest);
        return kind.matches("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}") ? join("/storage/" + kind, rest) : null;
    }

    /** The media-store number in "msf:1234", "document:1234" or a bare "1234" (downloads and media pickers); -1: none. */
    static long mediaId(String docId) {
        if (docId == null) return -1;
        String digits = docId.substring(docId.indexOf(':') + 1);
        if (digits.isEmpty() || digits.length() > 18) return -1;
        for (int i = 0; i < digits.length(); i++) if (digits.charAt(i) < '0' || digits.charAt(i) > '9') return -1;
        String kind = docId.indexOf(':') < 0 ? "" : docId.substring(0, docId.indexOf(':'));
        return kind.isEmpty() || kind.equals("msf") || kind.equals("document") ? Long.parseLong(digits) : -1;
    }

    /**
     * Where the file of a content address may be, most likely first: a full path inside the address
     * ("/root/storage/emulated/0/Download/Game.zip"), the address without its first word below internal storage
     * ("/external_files/Download/Game.zip"), a download's name in Download. {@code path}: the address's path, decoded;
     * {@code displayName}: the name the other app gives the file, or null.
     */
    static List<String> guesses(String path, String displayName, String internalRoot) {
        List<String> out = new ArrayList<>();
        if (path != null && !climbs(path)) {
            int storage = path.indexOf("/storage/");
            if (storage >= 0) add(out, path.substring(storage));
            int sdcard = path.indexOf("/sdcard/");
            if (sdcard >= 0) add(out, join(internalRoot, path.substring(sdcard + 8)));
            int second = path.indexOf('/', 1);
            if (path.startsWith("/") && second > 0 && second + 1 < path.length()) {
                String first = path.substring(1, second).toLowerCase(Locale.ROOT), rest = path.substring(second + 1);
                if (first.contains("download")) add(out, join(internalRoot + "/Download", rest));
                add(out, join(internalRoot, rest));
            }
        }
        if (displayName != null && !displayName.isEmpty() && displayName.indexOf('/') < 0 && !displayName.startsWith("."))
            add(out, join(internalRoot + "/Download", displayName));
        return out;
    }

    /** The last word of an address's path, when it looks like a file name with an extension; else null. */
    static String nameIn(String path) {
        if (path == null) return null;
        String name = path.substring(path.lastIndexOf('/') + 1);
        name = name.substring(name.lastIndexOf(':') + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name : null;
    }

    /** A path with ".." in it: never followed. */
    static boolean climbs(String path) {
        return path.equals("..") || path.startsWith("../") || path.endsWith("/..") || path.contains("/../") || path.indexOf('\0') >= 0;
    }

    private static String join(String root, String rest) {
        return root + (rest.startsWith("/") ? "" : "/") + rest;
    }

    private static void add(List<String> out, String path) {
        if (!out.contains(path)) out.add(path);
    }
}
