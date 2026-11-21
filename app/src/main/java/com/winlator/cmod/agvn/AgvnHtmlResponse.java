/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How "Chạy nhẹ" answers the page for a file of the game ({@link AgvnHtmlFiles}, {@link AgvnHtmlAsar}; audit of
 * 20/11/2026). Its headers: the page may open no connection WebView does not show the app first. A file is
 * always answered whole: WebView does a Range request's part itself ({@link #file}). The charset of a page, script or style sheet: none for one that is not UTF-8. Pure Java but the response.
 */
final class AgvnHtmlResponse {
    /**
     * WebView asks the app (shouldInterceptRequest) about every http(s) request, and the app answers from the game folder
     * or refuses; it does not ask about a WebSocket, which went straight to the network. This lets the page connect only
     * where the app is asked: http(s) addresses, the page's own data, and its own host. Nothing else is restricted
     * (inline scripts, eval, pictures and sound from anywhere stay as they were).
     */
    static final String CSP = "connect-src 'self' data: blob: http: https:";
    /** On every answer: a script may be run as a worker, whose connections follow its own headers, not the page's. */
    static final Map<String, String> HEADERS;
    /** A page, script or style sheet up to this size is read whole to see whether it is UTF-8. */
    static final long MAX_CHECKED = 4 << 20;
    private static final Pattern CHARSET = Pattern.compile("charset\\s*=\\s*[\"']?\\s*([a-z0-9_.:-]+)");

    static {
        Map<String, String> h = new HashMap<>();
        h.put("Access-Control-Allow-Origin", "*");
        h.put("Content-Security-Policy", CSP);
        HEADERS = Collections.unmodifiableMap(h);
    }

    private AgvnHtmlResponse() {}

    /**
     * The answer for a file read from {@code in} (at its first byte): always the whole file, 200, with no length and no
     * range of its own. WebView handles a Range request itself on the stream the app gives it: it skips to the first
     * byte asked and reads on, as it did before 0.1.99. From 0.1.99 to 0.1.103 the app answered a Range itself (206, a
     * stream already at the range's first byte, Content-Length and Content-Range): a video then failed to read
     * (PIPELINE_ERROR_READ) and a game whose title waits for one stood at "Now Loading" (Thiếu Nữ Tu Tiên Truyện,
     * RPG Maker MV, POCO, 22/11/2026).
     */
    static WebResourceResponse file(String mime, String encoding, InputStream in) {
        return new WebResourceResponse(mime, encoding, 200, "OK", new HashMap<>(HEADERS), in);
    }

    /** True for a page, script or style sheet small enough to be read whole ({@link #charset}). */
    static boolean checked(String mime, long size) {
        return size <= MAX_CHECKED
                && (mime.equals("text/html") || mime.equals("application/javascript") || mime.equals("text/css"));
    }

    /**
     * The answer for a page, script or style sheet read whole; {@code page}: the game's first page, which gets
     * html-compat.js ({@link AgvnHtmlFiles#inject}), put in without changing any other byte.
     */
    static WebResourceResponse text(String mime, byte[] bytes, boolean page) {
        String charset = charset(mime, bytes);
        if (page) bytes = withCompat(bytes, charset);
        return file(mime, charset, new ByteArrayInputStream(bytes));
    }

    /** The page {@code html} with html-compat.js's tag; one that is not UTF-8 ({@code charset} null) byte for byte. */
    static byte[] withCompat(byte[] html, String charset) {
        Charset as = charset == null ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8;
        return AgvnHtmlFiles.inject(new String(html, as)).getBytes(as);
    }

    /**
     * The charset the answer names for {@code bytes}: "UTF-8", as every text file was served, or null (none named) for a
     * script or style sheet that is not UTF-8, which the browser then reads as its page is written, and for a page that
     * is not UTF-8 and names another charset itself (&lt;meta charset="Shift_JIS"&gt;). The browser replaced every letter
     * of such a game with "�". A page that names none keeps UTF-8: the browser would guess.
     */
    static String charset(String mime, byte[] bytes) {
        if (utf8(bytes)) return "UTF-8";
        if (!mime.equals("text/html")) return null;
        String head = new String(bytes, 0, Math.min(bytes.length, 4096), StandardCharsets.ISO_8859_1)
                .toLowerCase(Locale.ROOT);
        Matcher m = CHARSET.matcher(head);
        return m.find() && !m.group(1).equals("utf-8") && !m.group(1).equals("utf8") ? null : "UTF-8";
    }

    static boolean utf8(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
