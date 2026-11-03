/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A page that sizes itself for the screen and asks to be shown smaller ({@code <meta name="viewport"
 * content="width=device-width, initial-scale=0.8">}, a React visual novel, POCO F8 Pro 2510×1156, 03/11/2026) was laid
 * out a quarter wider than the screen and not shown smaller: its title sat at x≈1570 instead of 1255, its long lines
 * ran off the right edge. Android's WebView does this without its "wide viewport": the layout takes the page's scale,
 * the display does not (Chromium's quirks for WebView). Such a page gets the wide viewport, as Chrome does; a page
 * that asks for no scale below 1, RPG Maker's and Tyrano's among them, stays as it is. Pure Java (JVM-testable).
 */
final class AgvnHtmlViewport {
    /** How much of the page is read for its meta tags. */
    static final int HEAD = 64 * 1024;
    private static final Pattern META = Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern VIEWPORT = Pattern.compile("name\\s*=\\s*[\"']?viewport", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENT = Pattern.compile("content\\s*=\\s*(\"([^\"]*)\"|'([^']*)')", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCALE = Pattern.compile("initial-scale\\s*=\\s*([0-9]*\\.?[0-9]+)", Pattern.CASE_INSENSITIVE);

    private AgvnHtmlViewport() {}

    /** True when the page at {@code index} asks to be shown smaller than 1 (its viewport's initial-scale). */
    static boolean zoomsOut(File index) {
        Float scale = initialScale(head(index));
        return scale != null && scale > 0 && scale < 1;
    }

    /** The initial-scale of {@code html}'s viewport meta tag, or null when it sets none. */
    static Float initialScale(String html) {
        if (html == null) return null;
        Matcher meta = META.matcher(html);
        while (meta.find()) {
            String tag = meta.group();
            if (!VIEWPORT.matcher(tag).find()) continue;
            Matcher content = CONTENT.matcher(tag);
            if (!content.find()) continue;
            String value = content.group(2) != null ? content.group(2) : content.group(3);
            Matcher scale = SCALE.matcher(value);
            if (!scale.find()) return null;
            try {
                return Float.parseFloat(scale.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String head(File index) {
        if (index == null || !index.isFile()) return null;
        byte[] buf = new byte[HEAD];
        try (InputStream in = new FileInputStream(index)) {
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            return new String(buf, 0, n, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
