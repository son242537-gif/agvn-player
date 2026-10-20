/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One key of a Wine registry file (system.reg, user.reg) read as text, its string values changed in memory and the
 * file's text given back with every other line as it was. Value names are compared without case, as Windows does.
 */
final class AgvnRegKey {
    private final String text, header;
    private final int bodyStart, bodyEnd;
    private final List<String> lines = new ArrayList<>();
    private boolean changed;

    /** {@code key} as the file writes it, e.g. {@code Software\\Wine\\Fonts} with doubled backslashes. */
    AgvnRegKey(String text, String key) {
        this.text = text;
        header = "[" + key + "]";
        int at = text.toLowerCase(Locale.ROOT).indexOf("\n" + header.toLowerCase(Locale.ROOT));
        int start = at < 0 ? -1 : text.indexOf('\n', at + 1);
        bodyStart = start < 0 ? -1 : start + 1;
        int next = bodyStart < 0 ? -1 : text.indexOf("\n[", bodyStart - 1);
        bodyEnd = bodyStart < 0 ? -1 : next < 0 ? text.length() : next + 1;
        if (bodyStart >= 0) for (String line : text.substring(bodyStart, bodyEnd).split("\n", -1)) lines.add(line);
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
    }

    /** The string values, in file order: name as written (unescaped) to data (unescaped). */
    Map<String, String> strings() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : lines) {
            String[] v = parse(line);
            if (v != null) out.put(v[0], v[1]);
        }
        return out;
    }

    /** A string value's data, its name in any case; null when there is none. */
    String get(String name) {
        int i = find(name);
        String[] v = i >= 0 ? parse(lines.get(i)) : null;
        return v != null ? v[1] : null;
    }

    /** Sets a string value; a value already there keeps its name as written. */
    void set(String name, String data) {
        int i = find(name);
        String[] now = i >= 0 ? parse(lines.get(i)) : null;
        if (now != null && now[1].equals(data)) return;
        String line = "\"" + AgvnFontFallback.escape(now != null ? now[0] : name) + "\"=\""
                + AgvnFontFallback.escape(data) + "\"";
        if (i >= 0) lines.set(i, line);
        else {
            int end = lines.size();
            while (end > 0 && lines.get(end - 1).trim().isEmpty()) end--;
            lines.add(end, line);
        }
        changed = true;
    }

    void remove(String name) {
        int i = find(name);
        if (i < 0) return;
        lines.remove(i);
        changed = true;
    }

    /** The file's new text, or null when nothing changed. */
    String text() {
        if (!changed) return null;
        StringBuilder body = new StringBuilder();
        for (String line : lines) body.append(line).append('\n');
        if (bodyStart >= 0) return text.substring(0, bodyStart) + body + text.substring(bodyEnd);
        return text + (text.endsWith("\n") ? "" : "\n") + "\n" + header + " 1700000000\n" + body;
    }

    private int find(String name) {
        for (int i = 0; i < lines.size(); i++) {
            String[] v = parse(lines.get(i));
            if (v != null && v[0].equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    /** {"name", "data"} of a line {@code "name"="data"}; null for any other line. */
    static String[] parse(String line) {
        if (!line.startsWith("\"")) return null;
        StringBuilder name = new StringBuilder(), data = new StringBuilder();
        int i = quoted(line, 1, name);
        if (i < 0 || i + 1 >= line.length() || line.charAt(i) != '=' || line.charAt(i + 1) != '"') return null;
        int end = quoted(line, i + 2, data);
        return end == line.length() ? new String[]{name.toString(), data.toString()} : null;
    }

    /** Reads a quoted string from {@code at} (after the opening quote) into {@code out}; the index past it. */
    private static int quoted(String s, int at, StringBuilder out) {
        int i = at;
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') return i;
            if (c != '\\' || i >= s.length()) {
                out.append(c);
                continue;
            }
            char e = s.charAt(i++);
            if (e == 'x') {
                int digits = 0, code = 0;
                while (digits < 4 && i < s.length() && Character.digit(s.charAt(i), 16) >= 0) {
                    code = code * 16 + Character.digit(s.charAt(i++), 16);
                    digits++;
                }
                out.append(digits > 0 ? (char) code : 'x');
            } else {
                out.append(e == 'n' ? '\n' : e == 't' ? '\t' : e == 'r' ? '\r' : e == '0' ? '\0' : e);
            }
        }
        return -1;
    }
}
