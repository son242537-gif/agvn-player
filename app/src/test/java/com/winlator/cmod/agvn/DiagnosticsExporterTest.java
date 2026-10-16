/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The export of the crash prompt carries the newest game sessions: it had only the phone's facts and logcat, though
 * the app had closed while a game played (OPPO PLE110, 16/10/2026).
 */
public class DiagnosticsExporterTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File session(File root, String game, String name) {
        File dir = new File(new File(root, game), name);
        assertTrue(dir.mkdirs());
        return dir;
    }

    @Test
    public void theNewestSessionsOfAnyGameComeFirst() throws IOException {
        File root = tmp.newFolder("logs");
        session(root, "Happy Island Fantasy", "20261015-100000");
        File older = session(root, "Happy Island Fantasy", "20261016-090000");
        File newest = session(root, "Song Tu Vo Lam - SongTu_GameHub", "20261016-155300");
        File middle = session(root, "Rebirth Pub", "20261016-120000");
        assertTrue(new File(root, "Empty Game").mkdirs());
        assertTrue(new File(root, "old-log.txt").createNewFile()); // a loose log, not a session
        assertTrue(new File(new File(root, "Rebirth Pub"), "notes").mkdirs()); // not a session's name

        assertEquals(Arrays.asList(newest, middle, older), DiagnosticsExporter.newestSessions(root, 3));
        assertEquals(Collections.singletonList(newest), DiagnosticsExporter.newestSessions(root, 1));
    }

    @Test
    public void noLogsYetIsNoSession() {
        assertEquals(new ArrayList<File>(), DiagnosticsExporter.newestSessions(new File(tmp.getRoot(), "missing"), 3));
        List<File> none = DiagnosticsExporter.newestSessions(tmp.getRoot(), 3);
        assertTrue(none.isEmpty());
    }
}
