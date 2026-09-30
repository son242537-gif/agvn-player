package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class AgvnGlDriverTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void zinkMarkerChangesSoOldContainersReinstall() {
        assertNotEquals("zink", AgvnGlDriver.marker("zink")); // what containers stored for Ludashi's build
        assertEquals("zink@" + AgvnGlDriver.ZINK_REVISION, AgvnGlDriver.marker("zink"));
        assertEquals("freedreno", AgvnGlDriver.marker("freedreno"));
    }

    @Test
    public void onlyLibgalliumFilesAreRemoved() throws Exception {
        File lib = tmp.newFolder("usr", "lib");
        for (String n : new String[]{"libgallium-24.3.0.so", "libgallium-26.3.0-devel.so", "libGL.so.1.5.0", "libglapi.so.0.0.0", "libgallium.txt"})
            assertTrue(new File(lib, n).createNewFile());
        AgvnGlDriver.removeOldGallium(tmp.getRoot());
        assertFalse(new File(lib, "libgallium-24.3.0.so").exists());
        assertFalse(new File(lib, "libgallium-26.3.0-devel.so").exists());
        assertTrue(new File(lib, "libGL.so.1.5.0").exists());
        assertTrue(new File(lib, "libglapi.so.0.0.0").exists());
        assertTrue(new File(lib, "libgallium.txt").exists());
        AgvnGlDriver.removeOldGallium(tmp.newFolder("empty")); // no usr/lib: nothing to do
    }
}
