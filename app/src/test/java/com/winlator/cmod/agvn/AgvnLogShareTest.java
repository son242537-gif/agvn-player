/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** "Gửi nhật ký" while the game still runs: finished sessions, the running one as it is now, logcat and Ren'Py logs. */
public class AgvnLogShareTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void zipsSessionsAndWhatTheGameWritesNow() throws Exception {
        File game = tmp.newFolder("logs", "Game");
        File done = write(new File(game, "20261002-100000/tom-tat.txt"), "done").getParentFile();
        File running = write(new File(game, "20261002-120000/dang-chay.txt"), "start=1").getParentFile();
        File live = tmp.newFolder("live");
        write(new File(live, "20261002-120000/tom-tat.txt"), "running");
        write(new File(live, "20261002-120000/Player.log"), "unity");
        write(new File(live, "20261002-120000/dang-chay.txt"), "a second copy is skipped");
        write(new File(live, "app/logcat.txt"), "I mkxp: ...");
        write(new File(live, "renpy/log.txt"), "Ren'Py");
        write(new File(live, "renpy/traceback.txt"), ""); // empty: left out

        File zip = new File(tmp.getRoot(), "out.zip");
        assertEquals(6, AgvnLogShare.zip(new File[]{done, running}, live, zip));
        assertEquals(Arrays.asList("20261002-100000/tom-tat.txt", "20261002-120000/Player.log",
                "20261002-120000/dang-chay.txt", "20261002-120000/tom-tat.txt", "app/logcat.txt", "renpy/log.txt"), names(zip));
        try (ZipFile z = new ZipFile(zip)) {
            assertEquals("start=1", new String(z.getInputStream(z.getEntry("20261002-120000/dang-chay.txt")).readAllBytes(),
                    StandardCharsets.UTF_8));
        }
    }

    @Test
    public void aGameWithoutSessionsStillGetsTheLiveLogs() throws Exception {
        File live = tmp.newFolder("live");
        write(new File(live, "app/logcat.txt"), "I SDL: ...");
        File zip = new File(tmp.getRoot(), "out.zip");
        assertEquals(1, AgvnLogShare.zip(null, live, zip));
        assertEquals(Arrays.asList("app/logcat.txt"), names(zip));
    }

    private static File write(File f, String text) throws Exception {
        Files.createDirectories(f.getParentFile().toPath());
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    private static List<String> names(File zip) throws Exception {
        List<String> out = new ArrayList<>();
        try (ZipFile z = new ZipFile(zip)) {
            for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) out.add(e.nextElement().getName());
        }
        Collections.sort(out); // file order differs between Linux and Windows
        return out;
    }
}
