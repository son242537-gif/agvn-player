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
        for (String n : new String[]{"game.exe", "DINPUT8.dll", "winhttp.dll", "d3d11.dll", "steam_api64.dll"})
            assertTrue(new File(dir, n).createNewFile());
        Map<String, String> found = GameDllOverrides.detect(dir);
        assertEquals("dinput8=n,b;winhttp=n,b", GameDllOverrides.build(found, null));
        assertEquals("dinput8=n,b;winhttp=b", GameDllOverrides.build(found, Collections.singletonMap("winhttp.dll", "b")));
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
