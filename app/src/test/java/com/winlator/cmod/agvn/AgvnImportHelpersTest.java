package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AgvnImportHelpersTest {
    @Test
    public void pickedFoldersNewestFirstWithoutDuplicatesAndCapped() {
        List<File> known = new ArrayList<>();
        for (int i = 0; i < AgvnGameRoots.MAX; i++) known.add(new File("/sd/f" + i));
        List<String> paths = AgvnGameRoots.remember(known, new File("/sd/f3"));
        assertEquals(AgvnGameRoots.MAX, paths.size());
        assertEquals("/sd/f3", paths.get(0));
        assertEquals("/sd/f0", paths.get(1));

        paths = AgvnGameRoots.remember(known, new File("/sd/new"));
        assertEquals(AgvnGameRoots.MAX, paths.size());
        assertEquals("/sd/new", paths.get(0));
        assertEquals("/sd/f8", paths.get(AgvnGameRoots.MAX - 1));
    }

    @Test
    public void shortcutExePathIsReadFromTheExecLine() {
        assertEquals("/storage/emulated/0/AGVN/My Game/Game.exe",
                AgvnLibraryIndex.unixExe("\"/storage/emulated/0/AGVN/My Game/Game.exe\""));
        assertEquals("/sdcard/G/g.EXE", AgvnLibraryIndex.unixExe("\"/sdcard/G/g.EXE\" -dx11"));
        assertNull(AgvnLibraryIndex.unixExe("\"D:\\\\Games\\\\g.exe\""));
        assertNull(AgvnLibraryIndex.unixExe(null));
    }
}
