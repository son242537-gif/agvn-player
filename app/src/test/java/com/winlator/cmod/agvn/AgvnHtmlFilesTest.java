/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** How "Chạy nhẹ" answers the page ({@link AgvnHtmlResponse}): parts of a file, charsets, its limit on connections. */
public class AgvnHtmlFilesTest {
    private static final String TAG = "<script src=\"" + AgvnHtmlFiles.COMPAT_PATH + "\"></script>";

    @Test
    public void aPageThatIsNotUtf8KeepsItsOwnCharset() {
        Charset sjis = Charset.forName("Shift_JIS");
        byte[] utf8Page = "<html><head><meta charset=\"UTF-8\"><title>ゲーム</title></head></html>".getBytes(StandardCharsets.UTF_8);
        byte[] sjisPage = "<html><head><meta charset=\"Shift_JIS\"><title>ゲーム</title></head></html>".getBytes(sjis);
        byte[] oldPage = "<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=gbk\">中</head>".getBytes(Charset.forName("GBK"));
        assertTrue(AgvnHtmlResponse.utf8(utf8Page));
        assertFalse(AgvnHtmlResponse.utf8(sjisPage));
        assertTrue("plain ASCII", AgvnHtmlResponse.utf8("var a = 1;".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("as every text file was served", "UTF-8", AgvnHtmlResponse.charset("text/html", utf8Page));
        assertNull("the page's own <meta> decides", AgvnHtmlResponse.charset("text/html", sjisPage));
        assertNull(AgvnHtmlResponse.charset("text/html", oldPage));
        // a page that names no charset, or names UTF-8 with a stray byte in it, is read as before: the browser would guess
        assertEquals("UTF-8", AgvnHtmlResponse.charset("text/html", "<html><title>ゲーム</title></html>".getBytes(sjis)));
        assertEquals("UTF-8", AgvnHtmlResponse.charset("text/html", "<meta charset=\"utf-8\"><title>ゲーム</title>".getBytes(sjis)));
        // a script or style sheet that is not UTF-8 names none: it is read as its page is written
        assertNull(AgvnHtmlResponse.charset("application/javascript", "var s = 'ゲーム';".getBytes(sjis)));
        assertNull(AgvnHtmlResponse.charset("text/css", "/* 日本語 */".getBytes(sjis)));
        assertEquals("UTF-8", AgvnHtmlResponse.charset("application/javascript", "var s = 'ゲーム';".getBytes(StandardCharsets.UTF_8)));
        assertTrue(AgvnHtmlResponse.checked("text/html", 10) && AgvnHtmlResponse.checked("application/javascript", 10)
                && AgvnHtmlResponse.checked("text/css", 10));
        assertFalse("XMLHttpRequest reads JSON as UTF-8 whatever is named", AgvnHtmlResponse.checked("application/json", 10));
        assertFalse(AgvnHtmlResponse.checked("image/png", 10));
        assertFalse("too large to read whole: served as before", AgvnHtmlResponse.checked("application/javascript", AgvnHtmlResponse.MAX_CHECKED + 1));

        // html-compat.js's tag goes in and every other byte stays
        byte[] served = AgvnHtmlResponse.withCompat(sjisPage, null);
        assertEquals("<html><head>" + TAG + "<meta charset=\"Shift_JIS\"><title>ゲーム</title></head></html>", new String(served, sjis));
        assertEquals("a UTF-8 page as before", AgvnHtmlFiles.inject(new String(utf8Page, StandardCharsets.UTF_8)),
                new String(AgvnHtmlResponse.withCompat(utf8Page, "UTF-8"), StandardCharsets.UTF_8));
    }

    @Test
    public void thePageConnectsOnlyWhereTheAppIsAsked() {
        // WebView shows the app http(s) requests (shouldInterceptRequest) and not WebSockets: ws: and wss: to another host
        // are not in the list, and nothing but connections is limited (no default-src, script-src, img-src, media-src)
        String csp = AgvnHtmlResponse.HEADERS.get("Content-Security-Policy");
        assertEquals("connect-src 'self' data: blob: http: https:", csp);
        assertFalse(csp.contains("ws:") || csp.contains("wss:") || csp.contains("*"));
        assertEquals(1, csp.split(";").length);
        assertEquals("*", AgvnHtmlResponse.HEADERS.get("Access-Control-Allow-Origin"));
    }

    private static byte[] all(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[7];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
