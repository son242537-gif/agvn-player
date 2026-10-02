/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** The Ren'Py engine is unpacked once per version, whole or not at all. */
public class AgvnRenpyFilesTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static ByteArrayInputStream zip(String... namesAndTexts) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (int i = 0; i < namesAndTexts.length; i += 2) {
                zip.putNextEntry(new ZipEntry(namesAndTexts[i]));
                zip.write(namesAndTexts[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return new ByteArrayInputStream(bytes.toByteArray());
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void unpacksOnceAndAgainForAnotherVersion() throws Exception {
        File dir = new File(tmp.getRoot(), "renpy8");
        assertFalse(AgvnRenpyFiles.ready(dir, "v1"));
        AgvnRenpyFiles.unpack(zip("main.py", "print(1)", "renpy/bootstrap.pyc", "x", "lib/python3.12/os.pyc", "y",
                "renpy/old.pyc", "z"), dir, "v1");
        assertEquals("print(1)", read(new File(dir, "main.py")));
        assertEquals("y", read(new File(dir, "lib/python3.12/os.pyc")));
        assertTrue(AgvnRenpyFiles.ready(dir, "v1"));
        assertFalse("an app update brings another engine", AgvnRenpyFiles.ready(dir, "v2"));

        AgvnRenpyFiles.unpack(zip("main.py", "print(2)", "renpy/bootstrap.pyc", "x2"), dir, "v2");
        assertTrue(AgvnRenpyFiles.ready(dir, "v2"));
        assertEquals("print(2)", read(new File(dir, "main.py")));
        assertFalse("files of the old engine are gone", new File(dir, "renpy/old.pyc").exists());
        assertFalse(new File(tmp.getRoot(), "renpy8.new").exists());
    }

    @Test
    public void aBrokenArchiveLeavesTheWorkingEngineAlone() throws Exception {
        File dir = new File(tmp.getRoot(), "renpy8");
        AgvnRenpyFiles.unpack(zip("main.py", "ok"), dir, "v1");
        try {
            AgvnRenpyFiles.unpack(zip("main.py", "new", "../escape.txt", "bad"), dir, "v2");
            fail("an entry outside the engine folder must stop the unpack");
        } catch (IOException expected) {
            // stopped before touching dir
        }
        assertFalse(new File(tmp.getRoot(), "escape.txt").exists());
        assertTrue(AgvnRenpyFiles.ready(dir, "v1"));
        assertEquals("ok", read(new File(dir, "main.py")));
    }

    @Test
    public void anEngineWithoutItsStartFileIsNotReady() throws Exception {
        File dir = new File(tmp.getRoot(), "renpy8");
        AgvnRenpyFiles.unpack(zip("main.py", "ok"), dir, "v1");
        assertTrue(new File(dir, "main.py").delete());
        assertFalse(AgvnRenpyFiles.ready(dir, "v1"));
    }
}
