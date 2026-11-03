/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A page game's request to another host than its own. Many web games read their pictures beside the page when it is
 * a file: and from their maker's server when it is http(s):, as their web version. The app serves the page from
 * https://&lt;code&gt;.agvn.game/, so such a game asked the maker's server, which the app blocks (nothing leaves the
 * phone), and showed no picture though all of them lay beside index.html (an HTML game of about 60 000 pictures, POCO
 * F8 Pro, 03/11/2026). A request to another host whose path names a file of the game folder now gets that file: the
 * path as it is, else without its first folders ("/cdn/v2/images/a.png" gives "images/a.png"), down to the file and
 * the folder it is in. Anything else is still blocked; each host is logged once, and a blocked one is listed with the
 * files the game missed. Pure Java but the logging.
 */
final class AgvnHtmlOutside {
    private static final String TAG = "AGVN";
    private final Set<String> told = new HashSet<>();

    /** The path under {@code root} a request to another host for {@code encodedPath} stands for, or null. */
    static String localPath(File root, String encodedPath) {
        if (root == null || encodedPath == null) return null;
        List<String> parts = new ArrayList<>();
        for (String part : encodedPath.split("/")) if (!part.isEmpty()) parts.add(part);
        int keep = Math.min(2, parts.size()); // the file and its folder, or the file alone when it was alone
        for (int start = 0; start <= parts.size() - keep && keep > 0; start++) {
            String path = "/" + String.join("/", parts.subList(start, parts.size()));
            File file = AgvnHtmlFiles.resolve(root, path);
            if (file != null && file.isFile()) return path;
        }
        return null;
    }

    /** Logs a request to {@code host} once, served from {@code local} or (null) blocked; a blocked one goes to {@code missing}. */
    synchronized void note(String host, String url, String local, Consumer<String> missing) {
        if (!told.add((local != null ? "+" : "-") + host)) return;
        if (local != null) {
            Log.i(TAG, "HTML game request to " + host + " served from the game folder: " + url + " -> " + local);
            return;
        }
        Log.w(TAG, "HTML game external request blocked: " + url);
        if (missing != null) missing.accept(url + " (máy chủ ngoài, app không cho ra mạng)");
    }
}
