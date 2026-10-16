/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.PackageInstaller;

import org.junit.Test;

/** The other way to install an update (OnePlus, OPPO, realme and vivo installers, 10/10/2026). */
public class AgvnUpdateRetryTest {
    private static final long NOW = 1_791_660_000_000L, MINUTE = 60_000L;

    @Test
    public void offeredOnlyRightAfterAnInstallThatLeftTheOldVersion() {
        assertTrue("0.1.37 handed over a minute ago, 0.1.35 still in",
                AgvnUpdateRetry.offer(39, 37, NOW - MINUTE, NOW, true));
        assertFalse("installed", AgvnUpdateRetry.offer(39, 39, NOW - MINUTE, NOW, true));
        assertFalse("its file is gone", AgvnUpdateRetry.offer(39, 37, NOW - MINUTE, NOW, false));
        assertFalse("long ago: not coming back from the installer",
                AgvnUpdateRetry.offer(39, 37, NOW - AgvnUpdateRetry.WINDOW_MS, NOW, true));
        assertFalse("the clock went back", AgvnUpdateRetry.offer(39, 37, NOW + MINUTE, NOW, true));
    }

    @Test
    public void androidsReasonInThePlayersWords() {
        String storage = "INSTALL_FAILED_INSUFFICIENT_STORAGE";
        assertEquals("máy không đủ bộ nhớ trống để cài (" + storage + ")",
                AgvnSessionInstall.reason(PackageInstaller.STATUS_FAILURE_STORAGE, storage));
        String noCerts = "INSTALL_PARSE_FAILED_NO_CERTIFICATES: no signature";
        assertEquals("Android thấy tệp cài không hợp lệ (" + noCerts + ")",
                AgvnSessionInstall.reason(PackageInstaller.STATUS_FAILURE_INVALID, noCerts));
        assertEquals("Android không cài được bản mới",
                AgvnSessionInstall.reason(PackageInstaller.STATUS_FAILURE, null));
        assertEquals("máy đang chặn việc cài app",
                AgvnSessionInstall.reason(PackageInstaller.STATUS_FAILURE_BLOCKED, ""));
    }
}
