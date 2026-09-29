/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Serves a web game's files to the WebView. Games made on Windows often name files with the wrong upper/lower case
 * ("Actor1.png" vs "actor1.png"), which Windows ignores but Android does not, so a missing file is looked up again
 * ignoring case. Nothing outside the game folder is ever served.
 */
public final class AgvnHtmlFiles {
    private static final Map<String, String> MIME = new HashMap<>();
    private static final Map<String, String> CORS = Collections.singletonMap("Access-Control-Allow-Origin", "*");

    static {
        String[][] types = {
                {"html", "text/html"}, {"htm", "text/html"}, {"js", "application/javascript"}, {"mjs", "application/javascript"},
                {"json", "application/json"}, {"css", "text/css"}, {"txt", "text/plain"}, {"xml", "text/xml"},
                {"ks", "text/plain"}, {"tjs", "text/plain"}, {"csv", "text/csv"}, {"svg", "image/svg+xml"},
                {"png", "image/png"}, {"jpg", "image/jpeg"}, {"jpeg", "image/jpeg"}, {"gif", "image/gif"},
                {"webp", "image/webp"}, {"bmp", "image/bmp"}, {"ico", "image/x-icon"},
                {"ogg", "audio/ogg"}, {"m4a", "audio/mp4"}, {"mp3", "audio/mpeg"}, {"wav", "audio/wav"},
                {"opus", "audio/ogg"}, {"mp4", "video/mp4"}, {"webm", "video/webm"}, {"ogv", "video/ogg"},
                {"woff", "font/woff"}, {"woff2", "font/woff2"}, {"ttf", "font/ttf"}, {"otf", "font/otf"},
                {"wasm", "application/wasm"}};
        for (String[] t : types) MIME.put(t[0], t[1]);
    }

    private AgvnHtmlFiles() {}

    /** The response for URL path {@code urlPath}: the game file, or 404. */
    public static WebResourceResponse serve(File root, String urlPath) {
        File file = resolve(root, urlPath);
        if (file == null) return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", CORS, empty());
        String mime = mimeType(file.getName());
        try {
            WebResourceResponse r = new WebResourceResponse(mime, isText(mime) ? "UTF-8" : null, new FileInputStream(file));
            r.setResponseHeaders(CORS);
            return r;
        } catch (IOException e) {
            return blocked();
        }
    }

    public static WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", Collections.emptyMap(), empty());
    }

    private static InputStream empty() {
        return new ByteArrayInputStream(new byte[0]);
    }

    /** MIME type by extension; RPG Maker's encrypted .rpgmvp/.rpgmvo/.png_ files are plain bytes. */
    public static String mimeType(String name) {
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        String type = MIME.get(ext);
        return type != null ? type : "application/octet-stream";
    }

    public static boolean isText(String mimeType) {
        return mimeType.startsWith("text/") || mimeType.equals("application/javascript") || mimeType.equals("application/json")
                || mimeType.equals("image/svg+xml");
    }

    /** The file for URL path {@code urlPath} ("/img/a%20b.png") under {@code root}, or null. */
    public static File resolve(File root, String urlPath) {
        String path = decode(urlPath == null ? "" : urlPath);
        if (path.isEmpty() || path.equals("/")) path = "/index.html";
        File current = root;
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) return null;
            File exact = new File(current, part);
            if (exact.exists()) {
                current = exact;
                continue;
            }
            current = ignoringCase(current, part);
            if (current == null) return null;
        }
        return current.isFile() ? current : null;
    }

    private static File ignoringCase(File dir, String name) {
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File c : children) if (c.getName().equalsIgnoreCase(name)) return c;
        return null;
    }

    private static String decode(String path) {
        try {
            return URLDecoder.decode(path.replace("+", "%2B"), "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return path;
        }
    }
}
