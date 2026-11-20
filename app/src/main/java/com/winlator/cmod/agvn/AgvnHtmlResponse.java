/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
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
 * 20/11/2026). Its headers: the page may open no connection WebView does not show the app first. A part of a file for a
 * Range request, with the file's length, so a video or a sound can be moved through and an MP4 whose index is at its end
 * plays. The charset of a page, script or style sheet: none for one that is not UTF-8. Pure Java but the response.
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
    /** {@link #range}'s answer for a range the file does not have. */
    static final long[] UNSATISFIABLE = new long[0];
    /** A page, script or style sheet up to this size is read whole to see whether it is UTF-8. */
    static final long MAX_CHECKED = 4 << 20;
    private static final Pattern RANGE = Pattern.compile("bytes=(\\d{0,18})-(\\d{0,18})");
    private static final Pattern CHARSET = Pattern.compile("charset\\s*=\\s*[\"']?\\s*([a-z0-9_.:-]+)");

    static {
        Map<String, String> h = new HashMap<>();
        h.put("Access-Control-Allow-Origin", "*");
        h.put("Content-Security-Policy", CSP);
        HEADERS = Collections.unmodifiableMap(h);
    }

    private AgvnHtmlResponse() {}

    /** The request's Range header, whatever its case, or null. */
    static String rangeOf(Map<String, String> requestHeaders) {
        if (requestHeaders != null) for (Map.Entry<String, String> e : requestHeaders.entrySet()) {
            if ("range".equalsIgnoreCase(e.getKey())) return e.getValue();
        }
        return null;
    }

    /**
     * The first and last byte a Range header asks of a file of {@code size} bytes: "bytes=0-99", "bytes=100-" (to the
     * end), "bytes=-50" (the last 50). Null for no header, one that is not understood (several ranges, a last byte before
     * the first) or an empty file: the whole file is the answer. {@link #UNSATISFIABLE} when it starts past the end.
     */
    static long[] range(String header, long size) {
        Matcher m = header == null ? null : RANGE.matcher(header.trim());
        if (m == null || !m.matches() || size <= 0) return null;
        String first = m.group(1), last = m.group(2);
        if (first.isEmpty() && last.isEmpty()) return null;
        long n = first.isEmpty() ? Long.parseLong(last) : 0; // "-n": the last n bytes
        if (first.isEmpty()) return n == 0 ? UNSATISFIABLE : new long[]{Math.max(0, size - n), size - 1};
        long from = Long.parseLong(first), to = last.isEmpty() ? size - 1 : Math.min(Long.parseLong(last), size - 1);
        if (!last.isEmpty() && Long.parseLong(last) < from) return null;
        return from >= size ? UNSATISFIABLE : new long[]{from, to};
    }

    /**
     * The answer for a file of {@code size} bytes read from {@code in} (at its first byte): all of it (200) or the part
     * {@code rangeHeader} asks for (206), with its length either way; 416 for a part it does not have.
     */
    static WebResourceResponse file(String mime, String encoding, InputStream in, long size, String rangeHeader) {
        long[] r = range(rangeHeader, size);
        Map<String, String> headers = new HashMap<>(HEADERS);
        headers.put("Accept-Ranges", "bytes");
        try {
            if (r == UNSATISFIABLE) {
                in.close();
                headers.put("Content-Range", "bytes */" + size);
                return new WebResourceResponse(mime, encoding, 416, "Range Not Satisfiable", headers,
                        new ByteArrayInputStream(new byte[0]));
            }
            long length = r == null ? size : r[1] - r[0] + 1;
            headers.put("Content-Length", String.valueOf(length));
            if (r == null) return new WebResourceResponse(mime, encoding, 200, "OK", headers, in);
            headers.put("Content-Range", "bytes " + r[0] + "-" + r[1] + "/" + size);
            return new WebResourceResponse(mime, encoding, 206, "Partial Content", headers, part(in, r[0], length));
        } catch (IOException e) {
            return AgvnHtmlFiles.blocked();
        }
    }

    /** {@code length} bytes of {@code in}, from its byte {@code from} on. */
    static InputStream part(InputStream in, long from, long length) throws IOException {
        for (long left = from; left > 0; ) { // skip() may stop short
            long skipped = in.skip(left);
            if (skipped <= 0 && in.read() < 0) break;
            left -= Math.max(1, skipped);
        }
        return new Part(in, length);
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
    static WebResourceResponse text(String mime, byte[] bytes, boolean page, String rangeHeader) {
        String charset = charset(mime, bytes);
        if (page) bytes = withCompat(bytes, charset);
        return file(mime, charset, new ByteArrayInputStream(bytes), bytes.length, page ? null : rangeHeader);
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

    /** The first {@code left} bytes of a stream. */
    private static final class Part extends FilterInputStream {
        private long left;

        Part(InputStream in, long length) {
            super(in);
            left = length;
        }

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
    }
}
