/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

/** What is removed from an unpacked folder whatever the unpacking program did. */
public class AgvnUnpack7zzSweepTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void linksAreRemovedAndNeverFollowed() throws Exception {
        Assume.assumeTrue("links need a POSIX file system", File.separatorChar == '/');
        File root = tmp.newFolder("game"), outside = tmp.newFolder("outside");
        File secret = AgvnUnpackFixtures.write(new File(outside, "secret.txt"), AgvnUnpackFixtures.bytes("mine"));
        AgvnUnpackFixtures.write(new File(root, "a.txt"), AgvnUnpackFixtures.bytes("a"));
        AgvnUnpackFixtures.write(new File(root, "data/b.txt"), AgvnUnpackFixtures.bytes("b"));
        Files.createSymbolicLink(new File(root, "to-file").toPath(), secret.toPath());
        Files.createSymbolicLink(new File(root, "to-folder").toPath(), outside.toPath());
        Files.createSymbolicLink(new File(root, "data/inside").toPath(), new File(root, "a.txt").toPath());
        Files.createSymbolicLink(new File(root, "dangling").toPath(), new File(root, "nothing").toPath());
        Files.createLink(new File(root, "data/second-name").toPath(), secret.toPath());

        AgvnUnpack7zzSweep sweep = AgvnUnpack7zzSweep.run(root);
        assertEquals("a.txt and data/b.txt", 2, sweep.files);
        assertEquals(5, sweep.removed);
        assertEquals(2, root.list().length);
        assertEquals(1, new File(root, "data").list().length);
        assertEquals("what the links pointed to is untouched", "mine", AgvnUnpackFixtures.read(secret));
        assertTrue(outside.isDirectory());
        assertFalse(new File(root, "to-folder").exists());
    }

    @Test
    public void aPlainFolderIsKept() throws Exception {
        File root = tmp.newFolder("game");
        AgvnUnpackFixtures.write(new File(root, "www/js/a.js"), AgvnUnpackFixtures.bytes("a"));
        new File(root, "saves").mkdirs();
        AgvnUnpack7zzSweep sweep = AgvnUnpack7zzSweep.run(root);
        assertEquals(1, sweep.files);
        assertEquals(0, sweep.removed);
        assertTrue(new File(root, "saves").isDirectory());
    }
}
