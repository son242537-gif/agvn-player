/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Serves the page of an Electron game packed in app.asar ({@link AgvnElectronGame}) to "Chạy nhẹ", as
 * {@link AgvnHtmlFiles} serves a game folder: from the page's folder in the archive, names found ignoring case, the
 * entry page with html-compat.js first, and nothing from outside the archive (app.asar.unpacked/ aside) but
 * resources/patch/: a file there wins over the archive's, as the main script of an Electron 44 game served them
 * (31/10/2026).
 */
final class AgvnHtmlAsar {
    static final int MAX_PAGE = 16 << 20;
    private static final Map<String, String> CORS = Collections.singletonMap("Access-Control-Allow-Origin", "*");

    private final AgvnAsar asar;
    /** The page's folder in the archive, "" or "web-desktop/". */
    private final String root;
    /** resources/patch/ beside the archive, or null. */
    private final File patch;

    private AgvnHtmlAsar(AgvnAsar asar, String root) {
        this.asar = asar;
        this.root = root;
        File dir = new File(asar.file.getParentFile(), "patch");
        patch = dir.isDirectory() ? dir : null;
    }

    /** For a page inside an app.asar ({@code index}: "…/resources/app.asar/index.html"); null for any other page. */
    static AgvnHtmlAsar forPage(File index) {
        File archive = AgvnAsar.archiveOf(index);
        AgvnAsar asar = archive != null ? AgvnAsar.of(archive) : null;
        if (asar == null || asar.find(AgvnAsar.pathIn(archive, index)) == null) return null;
        String inner = AgvnAsar.pathIn(archive, index.getParentFile());
        return new AgvnHtmlAsar(asar, inner.isEmpty() ? "" : inner + "/");
    }

    /** The archive's path for URL path {@code urlPath} ("/res/a%20b.png"), or null when it has none. */
    String find(String urlPath) {
        String path = AgvnHtmlFiles.decode(urlPath == null ? "" : urlPath);
        if (path.isEmpty() || path.equals("/")) path = "/index.html";
        return asar.find(root + path);
    }

    /** URL path {@code urlPath} ("/res/a%20b.png", still encoded) from resources/patch/, as the page's folder is there. */
    private String inPatch(String urlPath) {
        String path = urlPath == null || urlPath.equals("/") ? "" : urlPath.startsWith("/") ? urlPath.substring(1) : urlPath;
        return "/" + root + (path.isEmpty() ? "index.html" : path);
    }

    /** The file of resources/patch/ that stands for URL path {@code urlPath}, or null when it has none. */
    File patched(String urlPath) {
        File f = patch != null ? AgvnHtmlFiles.resolve(patch, inPatch(urlPath)) : null;
        return f != null && f.isFile() ? f : null;
    }

    /** As {@link AgvnHtmlFiles#serve}: the file, the entry page with {@code compatJs}, or 404 told to {@code missing}. */
    WebResourceResponse serve(String urlPath, String entry, String compatJs, Consumer<String> missing) {
        if (AgvnHtmlFiles.COMPAT_PATH.equals(urlPath) && compatJs != null) return text("application/javascript", compatJs);
        if (patched(urlPath) != null) return AgvnHtmlFiles.serve(patch, entry, inPatch(urlPath), compatJs, null, missing);
        String real = find(urlPath);
        if (real == null) {
            Log.w("AGVN", "Electron game file missing: " + urlPath);
            if (missing != null) missing.accept(AgvnHtmlFiles.decode(urlPath == null ? "" : urlPath) + " (không có trong app.asar)");
            return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", CORS, new ByteArrayInputStream(new byte[0]));
        }
        String name = real.substring(real.lastIndexOf('/') + 1), mime = AgvnHtmlFiles.mimeType(name);
        if (compatJs != null && (name.equalsIgnoreCase("index.html") || name.equalsIgnoreCase(entry))) {
            String html = asar.text(real, MAX_PAGE);
            if (html != null) return text("text/html", AgvnHtmlFiles.inject(html));
        }
        try {
            InputStream in = asar.open(real);
            return new WebResourceResponse(mime, AgvnHtmlFiles.isText(mime) ? "UTF-8" : null, 200, "OK", CORS, in);
        } catch (IOException e) {
            return AgvnHtmlFiles.blocked();
        }
    }

    private static WebResourceResponse text(String mime, String body) {
        return new WebResourceResponse(mime, "UTF-8", 200, "OK", CORS,
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }
}
