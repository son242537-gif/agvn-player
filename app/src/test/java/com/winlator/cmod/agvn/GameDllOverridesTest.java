package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Collections;
import java.util.Map;

public class GameDllOverridesTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void detectsProxyDllsNextToExe() throws Exception {
        File dir = tmp.newFolder("bin");
        for (String n : new String[]{"game.exe", "DINPUT8.dll", "winmm.dll", "agvncheat.dll", "d3d11.dll", "dxgi.dll",
                "kernel32.dll", "api-ms-win-crt-runtime-l1-1-0.dll", "readme.txt"})
            assertTrue(new File(dir, n).createNewFile());
        assertTrue(new File(dir, "D3D12").mkdir());
        assertTrue(new File(dir, "D3D12/D3D12Core.dll").createNewFile());
        Map<String, String> found = GameDllOverrides.detect(dir);
        assertEquals("agvncheat=n,b;dinput8=n,b;winmm=n,b", GameDllOverrides.build(found, null));
        assertEquals("agvncheat=n,b;dinput8=n,b;winmm=b", GameDllOverrides.build(found, Collections.singletonMap("winmm.dll", "b")));
        assertTrue(GameDllOverrides.detect(tmp.newFolder("empty")).isEmpty());
    }

    @Test
    public void mergeKeepsExistingEntries() {
        Map<String, String> d = new java.util.LinkedHashMap<>();
        d.put("dinput8", "n,b");
        d.put("version", "n,b");
        assertEquals("dinput8=b;version=n,b", GameDllOverrides.merge("dinput8=b", d));
        assertEquals("dinput8=n,b;version=n,b", GameDllOverrides.merge("", d));
    }

    @Test
    public void validModes() {
        assertTrue(GameDllOverrides.isValid("dinput8", "n,b"));
        assertTrue(GameDllOverrides.isValid("version", ""));
        assertTrue(!GameDllOverrides.isValid("x;y", "n"));
        assertTrue(!GameDllOverrides.isValid("dinput8", "native"));
    }
}
