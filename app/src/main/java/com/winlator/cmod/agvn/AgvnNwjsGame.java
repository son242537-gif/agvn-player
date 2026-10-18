/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An NW.js game made of web pages that RPG Maker and Tyrano do not account for: package.json beside the exe names an
 * HTML page as "main" (index.html, script.js, asset/...). Through Wine, NW.js draws with Chromium over DXVK: Love My
 * Back (realme RMX2103, Adreno 610, 14/10/2026) stayed black five times, DXVK made no swap chain, and took 2 GB of RAM,
 * while JoiPlay played it as a page. Such a game runs in "Chạy nhẹ" ({@link AgvnHtmlActivity}), with html-compat.js
 * standing in for require('fs'), process and nw. One that needs Node itself stays on Windows: a script run before the
 * page ("node-main", "bg-script"), Node modules (node_modules) or native addons (.node). Only a page beside
 * package.json is served (the game folder is the page's root). Pure Java (JVM-testable).
 */
final class AgvnNwjsGame {
    static final String PACKAGE = "package.json";
    /** package.json is a few hundred bytes; a longer one is read this far. */
    static final int MAX_PACKAGE = 64 * 1024;
    /** NW.js's own files beside its exe (the runtime a game ships), when package.json sets no window. */
    static final String[] RUNTIME = {"nw.dll", "nw_elf.dll", "nw.pak", "resources.pak"};
    private static final Pattern MAIN = Pattern.compile("\"main\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern NODE = Pattern.compile("\"(node-main|bg-script)\"\\s*:\\s*\"[^\"]+\"");
    private static final Pattern WINDOW = Pattern.compile("\"window\"\\s*:\\s*\\{");

    private AgvnNwjsGame() {}

    /**
     * The folder an NW.js game runs in (process.cwd()) when it is above the page's: the one with package.json, the
     * parent of an RPG Maker MV game's www/ ("main": "www/index.html"). A name the game reads relative to it is found
     * there: a game's rpg_managers.js read fs.readFileSync("lng.txt") as it loaded, the page's root www/ had no such
     * file, and the engine stopped loading, black (19/10/2026). Null when the page's folder has package.json itself.
     */
    static File appDir(File root) {
        File parent = root != null ? root.getParentFile() : null;
        if (parent == null || new File(root, PACKAGE).isFile() || !new File(parent, PACKAGE).isFile()) return null;
        return parent;
    }

    /** The page an NW.js game starts on, beside its package.json in {@code gameDir}; null for any other folder. */
    static File index(File gameDir) {
        File pkg = gameDir != null ? new File(gameDir, PACKAGE) : null;
        String json = pkg != null && pkg.isFile() ? read(pkg) : null;
        if (json == null || !nwjs(gameDir, json) || needsNode(gameDir, json)) return null;
        String page = page(main(json));
        if (page == null) return null;
        File index = new File(gameDir, page);
        if (index.isFile()) return index;
        File[] children = gameDir.listFiles(); // a page named "Index.html" on Windows
        if (children != null) for (File f : children) if (f.isFile() && f.getName().equalsIgnoreCase(page)) return f;
        return null;
    }

    /** package.json's "main", or null. */
    static String main(String json) {
        Matcher m = MAIN.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /**
     * The page file name {@code main} names in the game folder ("index.html", "app://./index.html", "./game.htm?x=1"),
     * or null: a script (an app on Node), or a page in another folder.
     */
    static String page(String main) {
        if (main == null) return null;
        String p = main.trim().replace('\\', '/');
        if (p.startsWith("app://")) p = p.substring(6);
        int cut = indexOfAny(p, '?', '#');
        if (cut >= 0) p = p.substring(0, cut);
        while (p.startsWith("./")) p = p.substring(2);
        String lower = p.toLowerCase(Locale.ROOT);
        if (p.isEmpty() || p.contains("/") || !(lower.endsWith(".html") || lower.endsWith(".htm"))) return null;
        return p;
    }

    /** True for NW.js's package.json: a window it opens, or NW.js's runtime beside it. */
    static boolean nwjs(File gameDir, String json) {
        if (WINDOW.matcher(json).find()) return true;
        for (String name : RUNTIME) if (new File(gameDir, name).isFile()) return true;
        return false;
    }

    /** True when the game runs Node itself: a script before the page, Node modules or native addons. */
    static boolean needsNode(File gameDir, String json) {
        if (NODE.matcher(json).find()) return true;
        if (new File(gameDir, "node_modules").isDirectory()) return true;
        File[] children = gameDir.listFiles();
        if (children != null) {
            for (File f : children) {
                if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".node")) return true;
            }
        }
        return false;
    }

    private static int indexOfAny(String s, char a, char b) {
        int i = s.indexOf(a), j = s.indexOf(b);
        return i < 0 ? j : j < 0 ? i : Math.min(i, j);
    }

    /** The start of {@code f} as text (UTF-8, a byte order mark dropped), or null when it cannot be read. */
    private static String read(File f) {
        try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
            byte[] buf = new byte[(int) Math.min(in.length(), MAX_PACKAGE)];
            in.readFully(buf);
            String s = new String(buf, StandardCharsets.UTF_8);
            return s.startsWith("﻿") ? s.substring(1) : s;
        } catch (IOException e) {
            return null;
        }
    }
}
