/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An RPG Maker MV/MZ game's title picture, from img/titles1 (under www/ for MV): the one data/System.json names
 * (title1Name) first, then the others from the largest. Encrypted pictures (.rpgmvp, .png_) are read too
 * ({@link AgvnArt#restorePng}). Pure Java (JVM-testable).
 */
final class AgvnMvArt {
    private static final String[] EXTS = {".png", ".jpg", ".rpgmvp", ".png_"};
    private static final int MAX_SYSTEM_JSON = 8 << 20;

    private AgvnMvArt() {}

    static void addCovers(File gameDir, List<AgvnArt> out) {
        if (gameDir == null) return;
        File www = new File(gameDir, "www");
        File root = www.isDirectory() ? www : gameDir;
        File[] files = new File(root, "img/titles1").listFiles(File::isFile);
        if (files == null) return;
        String title = titleName(new File(root, "data/System.json"));
        List<AgvnArt> pictures = new ArrayList<>();
        for (File f : files) {
            String base = baseName(f.getName());
            if (base == null) continue;
            if (base.equals(title)) AgvnCoverSources.add(out, AgvnArt.file(f));
            pictures.add(AgvnArt.file(f));
        }
        AgvnCoverSources.addLargest(out, pictures);
    }

    /** The name without a picture extension MV/MZ uses, or null when it is not such a picture. */
    static String baseName(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String ext : EXTS) if (lower.endsWith(ext)) return fileName.substring(0, fileName.length() - ext.length());
        return null;
    }

    /** title1Name of data/System.json, or null. */
    static String titleName(File systemJson) {
        if (!systemJson.isFile() || systemJson.length() > MAX_SYSTEM_JSON) return null;
        try {
            String text = new String(Files.readAllBytes(systemJson.toPath()), StandardCharsets.UTF_8);
            if (text.startsWith("﻿")) text = text.substring(1);
            JsonElement name = JsonParser.parseString(text).getAsJsonObject().get("title1Name");
            return name != null && name.isJsonPrimitive() && !name.getAsString().isEmpty() ? name.getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
