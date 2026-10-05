/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Readable game titles from folder names ("MASSAGE-MY-EX-BULLY-V118-GAMEHUB" -> "Massage My Ex Bully v118") and the
 * accent-free keys used to search and sort them. Pure Java (JVM-testable).
 */
public final class AgvnGameTitle {
    /** Uploader / pack tags that say nothing about the game. */
    static final Set<String> NOISE = new HashSet<>(Arrays.asList("gamehub", "agvn", "joiplay", "cheat", "pc", "viethoa"));
    /** Acronyms that stay upper case. */
    static final Set<String> ACRONYMS = new HashSet<>(Arrays.asList("rpg", "vn", "ntr", "hd", "dlc", "ai", "ui"));

    private static final Pattern SEPARATORS = Pattern.compile("[-_\\s\\[\\]]+");
    private static final Pattern CAMEL = Pattern.compile("(?<=\\p{Ll})(?=\\p{Lu})|(?<=\\p{Lu})(?=\\p{Lu}\\p{Ll})");
    private static final Pattern ROMAN = Pattern.compile("X{0,3}(IX|IV|V?I{0,3})");
    private static final Pattern VERSION = Pattern.compile("[vV]\\d+([._]\\d+)*[a-z]?");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private AgvnGameTitle() {}

    /** Display title for a game folder; falls back to the trimmed folder name when nothing is left. */
    public static String pretty(String folderName) {
        if (folderName == null) return "";
        List<String> words = new ArrayList<>();
        boolean light = false;
        String[] tokens = SEPARATORS.split(folderName.trim());
        for (int i = 0; i < tokens.length; i++) {
            String t = tokens[i];
            String lower = t.toLowerCase(Locale.ROOT);
            String next = i + 1 < tokens.length ? tokens[i + 1].toLowerCase(Locale.ROOT) : "";
            if (t.isEmpty() || NOISE.contains(lower)) continue;
            if (lower.equals("viet") && next.equals("hoa")) { i++; continue; }
            if (lower.equals("sieu") && next.equals("nhe")) { light = true; i++; continue; }
            if (lower.equals("sieunhe")) { light = true; continue; }
            for (String part : CAMEL.split(t)) if (!part.isEmpty()) words.add(caseWord(part));
        }
        if (words.isEmpty()) return folderName.trim();
        String title = String.join(" ", words);
        return light ? title + " (siêu nhẹ)" : title;
    }

    private static String caseWord(String w) {
        if (VERSION.matcher(w).matches()) return "v" + w.substring(1);
        if (ACRONYMS.contains(w.toLowerCase(Locale.ROOT))) return w.toUpperCase(Locale.ROOT);
        if (!w.equals(w.toUpperCase(Locale.ROOT))) {
            // mixed or lower case: only make sure the word starts with a capital
            return Character.isLowerCase(w.charAt(0)) ? w.substring(0, 1).toUpperCase(Locale.ROOT) + w.substring(1) : w;
        }
        if (ROMAN.matcher(w).matches()) return w;
        return w.charAt(0) + w.substring(1).toLowerCase(Locale.ROOT);
    }

    /** Lower-case key without Vietnamese accents: "Đường Về" -> "duong ve". */
    public static String searchKey(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        n = MARKS.matcher(n).replaceAll("").replace('đ', 'd').replace('Đ', 'D');
        return n.toLowerCase(Locale.ROOT).trim();
    }

    /** True when every space-separated word of {@code query} appears in {@code title}, ignoring accents and case. */
    public static boolean matches(String title, String query) {
        String hay = searchKey(title);
        for (String word : searchKey(query).split("\\s+")) if (!word.isEmpty() && !hay.contains(word)) return false;
        return true;
    }
}
