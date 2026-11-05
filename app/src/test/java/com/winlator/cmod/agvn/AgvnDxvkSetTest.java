/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

/** DXVK 2.3.1 unpacked over 1.10.3 kept 1.10.3's d3d10.dll (SRPG Studio, "B-Phese 2", 05/11/2026). */
public class AgvnDxvkSetTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static File write(File dir, String name, String text) throws Exception {
        File f = new File(dir, name);
        assertTrue(f.getParentFile().isDirectory() || f.getParentFile().mkdirs());
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    private static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void anOlderSetsD3d10GoesBackToWines() throws Exception {
        File wine = tmp.newFolder("wine");
        File lib = new File(wine, "lib/wine");
        for (String arch : new String[]{"aarch64-windows", "i386-windows"}) {
            write(new File(lib, arch), "d3d10.dll", "wine " + arch + " d3d10");
            write(new File(lib, arch), "d3d10_1.dll", "wine " + arch + " d3d10_1");
        }
        File windows = tmp.newFolder("windows");
        write(windows, "syswow64/d3d10.dll", "dxvk 1.10.3 d3d10, imports D3D10CoreCreateDevice@20");
        write(windows, "system32/d3d10.dll", "wine aarch64-windows d3d10");
        // system32/d3d10_1.dll and syswow64/d3d10_1.dll are missing: put back, not named

        File arm = new File(lib, "aarch64-windows"), x86 = new File(lib, "i386-windows");
        List<File[]> back = AgvnDxvkSet.putBack(windows, arm, x86);
        assertEquals("wine i386-windows d3d10", read(new File(windows, "syswow64/d3d10.dll")));
        assertEquals("wine i386-windows d3d10_1", read(new File(windows, "syswow64/d3d10_1.dll")));
        assertEquals("wine aarch64-windows d3d10_1", read(new File(windows, "system32/d3d10_1.dll")));
        assertEquals(Collections.singletonList("syswow64/d3d10.dll"), AgvnDxvkSet.kept(back, windows));

        assertTrue("nothing left over the second time", AgvnDxvkSet.putBack(windows, arm, x86).isEmpty());

        // DXVK 1.10.3, unpacked next, puts its own back: nothing to note
        write(windows, "syswow64/d3d10.dll", "dxvk 1.10.3 d3d10, imports D3D10CoreCreateDevice@20");
        assertTrue(AgvnDxvkSet.kept(back, windows).isEmpty());
    }

    @Test
    public void aWineOnX86UsesItsOwn64BitFolderAndAMissingWineCopyChangesNothing() throws Exception {
        File lib = tmp.newFolder("lib");
        write(new File(lib, "x86_64-windows"), "d3d10.dll", "wine x86_64 d3d10");
        File windows = tmp.newFolder("win");
        write(windows, "system32/d3d10.dll", "dxvk 1.10.3 64-bit");
        write(windows, "syswow64/d3d10.dll", "dxvk 1.10.3 32-bit");

        List<File[]> back = AgvnDxvkSet.putBack(windows, new File(lib, "x86_64-windows"),
                new File(lib, "i386-windows"));
        assertEquals(Collections.singletonList("system32/d3d10.dll"), AgvnDxvkSet.kept(back, windows));
        assertEquals("no Wine copy to put back", "dxvk 1.10.3 32-bit", read(new File(windows, "syswow64/d3d10.dll")));
    }

    @Test
    public void theNoteSaysWhatTheSetLeftAsWines() throws Exception {
        File wine = tmp.newFolder("w");
        write(new File(wine, "lib/wine/i386-windows"), "d3d10.dll", "wine d3d10");
        File windows = tmp.newFolder("prefix");
        write(windows, "syswow64/d3d10.dll", "dxvk 1.10.3 d3d10");

        AgvnDxvkSet.putWineOwnBack(windows, wine.getPath(), true);
        AgvnDxvkSet.noteKept(windows); // DXVK 2.3.1 unpacked: no d3d10.dll of its own
        String note = AgvnDxvkSet.takeNote();
        assertTrue(note, note.startsWith("Bộ DXVK: syswow64/d3d10.dll của bản DXVK trước còn lại"));
        assertNull("once", AgvnDxvkSet.takeNote());
        AgvnDxvkSet.noteKept(windows);
        assertNull("a set unpacked again has nothing to note", AgvnDxvkSet.takeNote());
    }
}
