/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.container.Container;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks a typed resolution. ScreenInfo only understands "WIDTHxHEIGHT" with a small "x" and crashes the launch on
 * anything else, so the editors store only what passes here and the launch falls back to the default.
 */
public final class AgvnScreenSize {
    static final int MIN = 200, MAX = 7680;
    /** "1280x720", "1280 X 720", "1280*720" or "1280×720", with an optional " (16:9)" label as in the list. */
    private static final Pattern SIZE = Pattern.compile("(\\d{1,5})\\s*[xX*×]\\s*(\\d{1,5})(\\s*\\(.*\\))?");

    private AgvnScreenSize() {}

    /** "WIDTHxHEIGHT", or null when the text is not a usable resolution. */
    public static String normalize(String value) {
        if (value == null) return null;
        Matcher m = SIZE.matcher(value.trim());
        if (!m.matches()) return null;
        int width = Integer.parseInt(m.group(1)), height = Integer.parseInt(m.group(2));
        if (width < MIN || height < MIN || width > MAX || height > MAX) return null;
        return width + "x" + height;
    }

    public static String orDefault(String value) {
        String size = normalize(value);
        return size != null ? size : Container.DEFAULT_SCREEN_SIZE;
    }
}
