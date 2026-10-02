/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AgvnUpdateInfoTest {
    private static final String SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String APK = "https://github.com/son242537-gif/agvn-player/releases/download/v0.1.3/AGVN-Player-0.1.3.apk";

    private static String manifest(String apk, String sha) {
        return "# AGVN Player\r\nversionCode=7\nversionName=0.1.3\napk=" + apk + "\nsize=452345678\nsha256=" + sha
                + "\nnotes=Sửa 9 cài đặt\nnotes=Thêm nút ?\nunknown=ignored\n";
    }

    @Test
    public void readsTheScriptsFile() {
        AgvnUpdateInfo info = AgvnUpdateInfo.parse(manifest(APK, SHA.toUpperCase()));
        assertNotNull(info);
        assertEquals(7, info.versionCode);
        assertEquals("0.1.3", info.versionName);
        assertEquals(APK, info.apkUrl);
        assertEquals(452345678L, info.size);
        assertEquals(SHA, info.sha256);
        assertEquals("Sửa 9 cài đặt\nThêm nút ?", info.notes);
        assertEquals("452 MB", info.sizeText());
        assertTrue(info.isNewerThan(6));
        assertFalse(info.isNewerThan(7));
    }

    @Test
    public void refusesApksFromElsewhereAndBrokenFiles() {
        assertNull(AgvnUpdateInfo.parse(manifest("http://github.com/son242537-gif/agvn-player/releases/download/v1/a.apk", SHA)));
        assertNull(AgvnUpdateInfo.parse(manifest("https://github.com/someone/agvn-player/releases/download/v1/a.apk", SHA)));
        assertNull(AgvnUpdateInfo.parse(manifest("https://example.com/a.apk", SHA)));
        assertNull(AgvnUpdateInfo.parse(manifest(APK.replace("v0.1.3/", "v0.1.3/../../x/"), SHA)));
        assertNull(AgvnUpdateInfo.parse(manifest(APK, "abc")));
        assertNull(AgvnUpdateInfo.parse(manifest(APK, SHA).replace("size=452345678", "size=lots")));
        assertNull(AgvnUpdateInfo.parse(manifest(APK, SHA).replace("versionName=0.1.3\n", "")));
        assertNull(AgvnUpdateInfo.parse("<html>Not Found</html>"));
        assertNull(AgvnUpdateInfo.parse(null));
    }

    @Test
    public void oneChannelTheNewestRelease() {
        assertEquals("https://github.com/son242537-gif/agvn-player/releases/latest/download/agvn-update.txt",
                AgvnUpdateInfo.LATEST_URL);
    }

    @Test
    public void checksAtMostOnceADay() {
        long day = AgvnUpdateInfo.DAY_MS, now = 100 * day;
        assertTrue(AgvnUpdateInfo.dueForCheck(0, now));
        assertFalse(AgvnUpdateInfo.dueForCheck(now - day + 1, now));
        assertTrue(AgvnUpdateInfo.dueForCheck(now - day, now));
        assertTrue(AgvnUpdateInfo.dueForCheck(now + 5000, now)); // the clock went back
    }
}
