/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.Locale;

/**
 * The "agvn-update.txt" that tools/agvn/dang-ban-cap-nhat.ps1 attaches to a GitHub release: one "key=value" per
 * line, "#" for comments, one "notes=" line per line of release notes. Example:
 * <pre>
 * versionCode=5
 * versionName=0.1.3
 * apk=https://github.com/son242537-gif/agvn-player/releases/download/v0.1.3/AGVN-Player-0.1.3.apk
 * size=452345678
 * sha256=64 hex digits
 * notes=Sửa 9 cài đặt
 * </pre>
 */
public final class AgvnUpdateInfo {
    public static final String REPO = "son242537-gif/agvn-player";
    static final String RELEASES = "https://github.com/" + REPO + "/releases/";
    /** The newest release. One channel: every phone gets the same version. */
    public static final String LATEST_URL = RELEASES + "latest/download/agvn-update.txt";
    /** Only APKs attached to this repository's releases are downloaded. */
    static final String APK_PREFIX = RELEASES + "download/";
    static final long DAY_MS = 24L * 60 * 60 * 1000;

    public final int versionCode;
    public final String versionName;
    public final String apkUrl;
    public final long size;
    public final String sha256;
    public final String notes;

    private AgvnUpdateInfo(int versionCode, String versionName, String apkUrl, long size, String sha256, String notes) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.apkUrl = apkUrl;
        this.size = size;
        this.sha256 = sha256;
        this.notes = notes;
    }

    /** The update described by {@code text}, or null when a field is missing or wrong. */
    public static AgvnUpdateInfo parse(String text) {
        if (text == null) return null;
        int code = 0;
        long size = 0;
        String name = null, apk = null, sha = null;
        StringBuilder notes = new StringBuilder();
        for (String raw : text.split("\r?\n")) {
            String line = raw.trim();
            int eq = line.indexOf('=');
            if (line.isEmpty() || line.startsWith("#") || eq <= 0) continue;
            String key = line.substring(0, eq).trim(), value = line.substring(eq + 1).trim();
            try {
                switch (key) {
                    case "versionCode": code = Integer.parseInt(value); break;
                    case "versionName": name = value; break;
                    case "apk": apk = value; break;
                    case "size": size = Long.parseLong(value); break;
                    case "sha256": sha = value.toLowerCase(Locale.ROOT); break;
                    case "notes": if (notes.length() > 0) notes.append('\n'); notes.append(value); break;
                    default: break;
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
        boolean ok = code > 0 && name != null && !name.isEmpty() && size > 0
                && apk != null && apk.startsWith(APK_PREFIX) && apk.endsWith(".apk") && !apk.contains("..")
                && sha != null && sha.matches("[0-9a-f]{64}");
        return ok ? new AgvnUpdateInfo(code, name, apk, size, sha, notes.toString()) : null;
    }

    public boolean isNewerThan(long installedVersionCode) {
        return versionCode > installedVersionCode;
    }

    /** True when the daily check is due: a day has passed, or the clock went back. */
    static boolean dueForCheck(long lastCheckMs, long nowMs) {
        return lastCheckMs <= 0 || nowMs < lastCheckMs || nowMs - lastCheckMs >= DAY_MS;
    }

    /** "452 MB" for the dialogs. */
    public String sizeText() {
        return Math.max(1, Math.round(size / 1_000_000d)) + " MB";
    }
}
