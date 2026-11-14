/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * fs.readdirSync and fs.statSync of html-compat.js, which a page game written for NW.js calls to scan a folder (a mod
 * loader reading mods/) or to tell a folder from a file. They returned [] and "a file" for anything, so such a game
 * saw no mod and took wrong turns (an RPG Maker MV game, POCO F8 Pro, 0.1.77, 06/11/2026). The page asks
 * GET /__agvn/fs/list/&lt;path&gt; or /__agvn/fs/stat/&lt;path&gt; (a path of the game folder, '/' its root, found
 * ignoring case as on Windows, never above it); the answer is JSON, or 404 when there is no such file or folder:
 * list → [{"name":"a.png","dir":false}, …], stat → {"dir":false,"size":12,"mtime":1730000000000}.
 */
final class AgvnHtmlFs {
    static final String PREFIX = "/__agvn/fs/";
    private static final Map<String, String> CORS = Collections.singletonMap("Access-Control-Allow-Origin", "*");

    private AgvnHtmlFs() {}

    static boolean handles(String urlPath) {
        return urlPath != null && urlPath.startsWith(PREFIX);
    }

    /** "list" or "stat" of {@code urlPath} ("/__agvn/fs/list/mods"); "" for neither. */
    static String op(String urlPath) {
        String rest = urlPath.substring(PREFIX.length());
        int slash = rest.indexOf('/');
        String op = slash < 0 ? rest : rest.substring(0, slash);
        return op.equals("list") || op.equals("stat") ? op : "";
    }

    /** The game path {@code urlPath} asks about, still URL-encoded: "/mods" for "/__agvn/fs/list/mods". */
    static String path(String urlPath) {
        String rest = urlPath.substring(PREFIX.length());
        int slash = rest.indexOf('/');
        return slash < 0 ? "/" : rest.substring(slash);
    }

    /** A folder game's answer, from {@code root}. */
    static WebResourceResponse serve(File root, String urlPath) {
        String op = op(urlPath);
        File f = find(root, path(urlPath));
        if (op.isEmpty() || f == null) return missing();
        if (op.equals("stat")) return json(stat(f.isDirectory(), f.isDirectory() ? 0 : f.length(), f.lastModified()));
        File[] children = f.listFiles();
        if (children == null) return missing();
        StringBuilder sb = new StringBuilder("[");
        for (File c : children) entry(sb, c.getName(), c.isDirectory());
        return json(sb.append(']').toString());
    }

    /**
     * The file or folder of game path {@code urlPath} under {@code root} (the root itself for "/"), or null; also with
     * a first folder named as the root left out ({@link AgvnHtmlFiles#withoutRootName}).
     */
    static File find(File root, String urlPath) {
        File found = walk(root, urlPath);
        String inRoot = found == null ? AgvnHtmlFiles.withoutRootName(root, urlPath) : null;
        return inRoot != null ? walk(root, inRoot) : found;
    }

    private static File walk(File root, String urlPath) {
        File current = root;
        for (String part : AgvnHtmlFiles.decode(urlPath).replace('\\', '/').split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) return null;
            File exact = new File(current, part);
            if (exact.exists()) {
                current = exact;
                continue;
            }
            File[] children = current.listFiles();
            File found = null;
            if (children != null) for (File c : children) if (c.getName().equalsIgnoreCase(part)) found = c;
            if (found == null) return null;
            current = found;
        }
        return current.exists() ? current : null;
    }

    /** A list answer: {@code dirs} are the names of {@code names} that are folders. */
    static String list(Collection<String> names, Collection<String> dirs) {
        StringBuilder sb = new StringBuilder("[");
        for (String n : names) entry(sb, n, dirs.contains(n));
        return sb.append(']').toString();
    }

    static String stat(boolean dir, long size, long mtime) {
        return "{\"dir\":" + dir + ",\"size\":" + size + ",\"mtime\":" + mtime + "}";
    }

    private static void entry(StringBuilder sb, String name, boolean dir) {
        if (sb.length() > 1) sb.append(',');
        sb.append("{\"name\":\"").append(name.replace("\\", "\\\\").replace("\"", "\\\"")).append("\",\"dir\":").append(dir).append('}');
    }

    static WebResourceResponse json(String body) {
        return new WebResourceResponse("application/json", "UTF-8", 200, "OK", CORS,
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    static WebResourceResponse missing() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", CORS, new ByteArrayInputStream(new byte[0]));
    }
}
