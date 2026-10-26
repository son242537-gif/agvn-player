/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which page an Electron game's main script opens ({@link AgvnElectronGame}), read from its text: the strings of each
 * win.loadFile(...) or win.loadURL(...) call. Pure Java (JVM-testable).
 */
final class AgvnElectronMain {
    private static final Pattern MAIN = Pattern.compile("\"main\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern LOAD = Pattern.compile("\\.load(File|URL)\\s*\\(");

    private AgvnElectronMain() {}

    /** package.json's "main" (Electron's default, index.js, when it names none), without "./". */
    static String mainOf(String json) {
        Matcher m = MAIN.matcher(json);
        String main = m.find() ? m.group(1).trim().replace('\\', '/') : "";
        while (main.startsWith("./")) main = main.substring(2);
        return main.isEmpty() ? "index.js" : main;
    }

    /**
     * The pages main script {@code main} may open, the likeliest first: each loadFile or loadURL call that names a page
     * (one under __dirname found from the script's folder, which loadFile's own relative path is not), then index.html.
     */
    static List<String> pages(String main, String script) {
        List<String> pages = new ArrayList<>();
        String dir = main.contains("/") ? main.substring(0, main.lastIndexOf('/') + 1) : "";
        Matcher load = LOAD.matcher(script == null ? "" : script);
        while (load.find()) {
            String args = args(script, load.end());
            String page = pageIn(args);
            if (page == null) continue;
            if (args.contains("__dirname")) add(pages, normalize(dir + page));
            add(pages, normalize(page));
        }
        add(pages, dir + "index.html");
        add(pages, "index.html");
        pages.remove(null);
        return pages;
    }

    /** The text of a call's arguments, from {@code start} to its closing parenthesis (at most 400 characters). */
    private static String args(String script, int start) {
        int depth = 1, end = Math.min(script.length(), start + 400);
        for (int i = start; i < end; i++) {
            char c = script.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return script.substring(start, i);
        }
        return script.substring(start, end);
    }

    /**
     * The page path the strings in a call's arguments make, up to the first that ends in .html or .htm ("www",
     * "index.html" → "www/index.html"; `file://${__dirname}/index.html` → "index.html"), or null. Web addresses and
     * url.format's "file:" are left out; the path may start with "../".
     */
    static String pageIn(String args) {
        StringBuilder page = new StringBuilder();
        for (String s : strings(args)) {
            String lower = s.toLowerCase(Locale.ROOT);
            if (lower.startsWith("http:") || lower.startsWith("https:")) continue; // a web address (a dev server)
            if (lower.startsWith("file:")) s = s.substring(5);
            int cut = indexOfAny(s, '?', '#');
            if (cut >= 0) s = s.substring(0, cut);
            if (s.replace("/", "").isEmpty()) continue;
            page.append('/').append(s);
            String l = s.toLowerCase(Locale.ROOT);
            if (l.endsWith(".html") || l.endsWith(".htm")) return page.toString().replaceAll("/+", "/").substring(1);
        }
        return null;
    }

    /** The string literals of {@code code} in order: '…', "…", and the text and inner strings of a `…${…}…` template. */
    static List<String> strings(String code) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < code.length(); i++) {
            char q = code.charAt(i);
            if (q != '\'' && q != '"' && q != '`') continue;
            StringBuilder text = new StringBuilder();
            int j = i + 1;
            for (; j < code.length() && code.charAt(j) != q; j++) {
                char c = code.charAt(j);
                if (c == '\\' && j + 1 < code.length()) text.append(code.charAt(++j));
                else if (q == '`' && c == '$' && j + 1 < code.length() && code.charAt(j + 1) == '{') {
                    int close = code.indexOf('}', j);
                    if (close < 0) break;
                    if (text.length() > 0) out.add(text.toString());
                    text.setLength(0);
                    out.addAll(strings(code.substring(j + 2, close)));
                    j = close;
                } else text.append(c);
            }
            if (text.length() > 0) out.add(text.toString());
            i = j;
        }
        return out;
    }

    /** "electron/../build/index.html" → "build/index.html"; null for a path above the app. */
    static String normalize(String path) {
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (parts.isEmpty()) return null;
                parts.remove(parts.size() - 1);
            } else parts.add(part);
        }
        return parts.isEmpty() ? null : String.join("/", parts);
    }

    private static int indexOfAny(String s, char a, char b) {
        int i = s.indexOf(a), j = s.indexOf(b);
        return i < 0 ? j : j < 0 ? i : Math.min(i, j);
    }

    private static void add(List<String> pages, String page) {
        if (!pages.contains(page)) pages.add(page);
    }
}
