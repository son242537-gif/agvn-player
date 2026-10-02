/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;
import android.webkit.WebResourceResponse;

import com.winlator.cmod.core.FileUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serves a web game's files to the WebView. Games made on Windows often name files with the wrong upper/lower case
 * ("Actor1.png" vs "actor1.png"), which Windows ignores but Android does not, so a missing file is looked up again
 * ignoring case. A sound or video asked for in a format the game does not ship is served in the one it does
 * ({@link #sibling}). Nothing outside the game folder is ever served.
 */
public final class AgvnHtmlFiles {
    private static final Map<String, String> MIME = new HashMap<>();
    /** Served from the app, not the game folder. */
    public static final String COMPAT_PATH = "/__agvn/compat.js";
    private static final Pattern HEAD = Pattern.compile("<head(\\s[^>]*)?>", Pattern.CASE_INSENSITIVE);
    /** 1x1 fully transparent PNG. */
    static final byte[] TRANSPARENT_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");
    private static final Map<String, String> CORS = Collections.singletonMap("Access-Control-Allow-Origin", "*");
    /**
     * Formats RPG Maker picks between: .m4a/.mp4 on a phone, .ogg/.webm on a PC; encrypted .rpgmvm/.rpgmvo (MV) or with
     * a trailing "_" (MZ). A game ships one of each pair, and the browser tells the formats apart by their content.
     */
    private static final String[][] SIBLINGS = {{".m4a", ".ogg"}, {".rpgmvm", ".rpgmvo"}, {".m4a_", ".ogg_"}, {".mp4", ".webm"}};

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

    /**
     * The response for URL path {@code urlPath}: the game file (index.html with {@code compatJs} loaded first, see
     * assets/agvn/html-compat.js), a transparent picture for a missing .png so the game does not stop, or 404.
     */
    public static WebResourceResponse serve(File root, String urlPath, String compatJs) {
        if (COMPAT_PATH.equals(urlPath) && compatJs != null)
            return text("application/javascript", compatJs);
        File file = fileFor(root, urlPath);
        if (file == null) {
            Log.w("AGVN", "HTML game file missing: " + urlPath);
            if (urlPath != null && urlPath.toLowerCase(Locale.ROOT).endsWith(".png"))
                return new WebResourceResponse("image/png", null, 200, "OK", CORS, new ByteArrayInputStream(TRANSPARENT_PNG));
            return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", CORS, empty());
        }
        String mime = mimeType(file.getName());
        if (compatJs != null && file.getName().equalsIgnoreCase("index.html")) {
            String html = FileUtils.readString(file);
            if (html != null) return text("text/html", inject(html));
        }
        try {
            WebResourceResponse r = new WebResourceResponse(mime, isText(mime) ? "UTF-8" : null, new FileInputStream(file));
            r.setResponseHeaders(CORS);
            return r;
        } catch (IOException e) {
            return blocked();
        }
    }

    /** The file for {@code urlPath}, else the same sound or video in its other format ({@link #sibling}), else null. */
    static File fileFor(File root, String urlPath) {
        File file = resolve(root, urlPath);
        if (file != null) return file;
        String other = sibling(urlPath);
        return other != null ? resolve(root, other) : null;
    }

    /** {@code urlPath} with the other extension of its format pair ("/audio/a.m4a" -> "/audio/a.ogg"), or null. */
    static String sibling(String urlPath) {
        if (urlPath == null) return null;
        String lower = urlPath.toLowerCase(Locale.ROOT);
        for (String[] pair : SIBLINGS) {
            for (int i = 0; i < 2; i++) {
                if (lower.endsWith(pair[i])) return urlPath.substring(0, urlPath.length() - pair[i].length()) + pair[1 - i];
            }
        }
        return null;
    }

    /** {@code html} with the compatibility script as the very first script, before the engine's. */
    static String inject(String html) {
        String tag = "<script src=\"" + COMPAT_PATH + "\"></script>";
        Matcher head = HEAD.matcher(html);
        if (head.find()) return html.substring(0, head.end()) + tag + html.substring(head.end());
        int script = html.toLowerCase(Locale.ROOT).indexOf("<script");
        return script >= 0 ? html.substring(0, script) + tag + html.substring(script) : tag + html;
    }

    private static WebResourceResponse text(String mime, String body) {
        WebResourceResponse r = new WebResourceResponse(mime, "UTF-8",
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        r.setResponseHeaders(CORS);
        return r;
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
