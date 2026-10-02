/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/** Only this session's logs are copied, each once; loose logs leave the logs/ root after 3 days. */
public class AgvnEngineLogsCopyTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File file(File dir, String name) throws Exception {
        File f = new File(dir, name);
        Files.write(f.toPath(), name.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    @Test
    public void anOldTracebackIsNotCopiedButNamed() throws Exception {
        File game = tmp.newFolder("game"), session = tmp.newFolder("session");
        long start = System.currentTimeMillis();
        File log = file(game, "log.txt"), traceback = file(game, "traceback.txt");
        assertTrue(traceback.setLastModified(start - 41L * 24 * 3600 * 1000));
        AgvnEngineLogs.Copied copied = AgvnEngineLogs.copy(Arrays.asList(log, traceback, new File(game, "errors.txt")), session, start);
        assertEquals(Arrays.asList(log), copied.copied);
        assertEquals(Arrays.asList(traceback), copied.old);
        assertFalse(new File(session, "traceback.txt").exists());
        String note = AgvnEngineLogs.oldNote(copied.old);
        assertTrue(note, note.startsWith("traceback.txt (cũ, ") && note.endsWith(" – không phải lỗi phiên này)"));
        assertEquals("", AgvnEngineLogs.oldNote(copied.copied.subList(0, 0)));
    }

    @Test
    public void oneFileUnderTwoNamesIsCopiedOnce() throws Exception {
        // on /sdcard "log.txt" and "Log.txt" are one file; a hard link is one file on every system too
        File game = tmp.newFolder("game"), other = tmp.newFolder("other"), session = tmp.newFolder("session");
        File log = file(game, "log.txt");
        File link = Files.createLink(new File(other, "Log.txt").toPath(), log.toPath()).toFile();
        AgvnEngineLogs.Copied copied = AgvnEngineLogs.copy(Arrays.asList(log, link), session, 0);
        assertEquals(1, copied.copied.size());
        assertEquals(1, session.list().length);
    }

    @Test
    public void looseLogsGoAfterThreeDaysSessionFoldersStay() throws Exception {
        File root = tmp.newFolder("logs");
        long now = System.currentTimeMillis();
        File old = file(root, "lo_se_sb_2026-09-28_18-47-13.txt"), recent = file(root, "game_2026-10-02_18-47-13.txt");
        assertTrue(old.setLastModified(now - AgvnLogFolders.LOOSE_MS - 60_000));
        File sessions = new File(root, "Lust Village");
        assertTrue(sessions.mkdirs());
        assertTrue(sessions.setLastModified(now - AgvnLogFolders.LOOSE_MS * 2));
        AgvnLogFolders.pruneLoose(root, now);
        assertFalse(old.exists());
        assertTrue(recent.exists());
        assertTrue(sessions.isDirectory());
    }
}
