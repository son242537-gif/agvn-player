/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** "Chạy game không có mod": the mod loaders beside a game's exe, and the game started without them. */
public class AgvnModsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File dir(String... names) throws Exception {
        File dir = tmp.newFolder();
        for (String name : names) {
            File f = new File(dir, name);
            assertTrue(name.endsWith("/") ? f.mkdirs() : f.createNewFile());
        }
        return dir;
    }

    @Test
    public void aLoaderIsItsFolderAndItsDll() throws Exception {
        // Rina, 07/10/2026: BepInEx for IL2CPP, loaded through winhttp.dll
        Map<String, List<String>> rina = AgvnMods.found(dir("Game.exe", "winhttp.dll", "GameAssembly.dll",
                "UnityPlayer.dll", "baselib.dll", "BepInEx/", "dotnet/"));
        assertEquals(Collections.singletonMap("BepInEx", Collections.singletonList("winhttp")), rina);
        assertEquals(Collections.singletonList("winhttp"), AgvnMods.proxies(rina));
        // MelonLoader's version.dll; UE4SS beside a game that also has its own dinput8.dll
        assertEquals(Collections.singletonList("version"),
                AgvnMods.proxies(AgvnMods.found(dir("Game.exe", "version.dll", "MelonLoader/"))));
        assertEquals(Collections.singletonList("dwmapi"),
                AgvnMods.proxies(AgvnMods.found(dir("Game.exe", "dwmapi.dll", "dinput8.dll", "ue4ss/"))));
        assertTrue("a folder without its DLL loads nothing", AgvnMods.found(dir("Game.exe", "BepInEx/")).isEmpty());
        assertTrue("a DLL without a loader's folder is the game's own",
                AgvnMods.found(dir("Game.exe", "winhttp.dll", "version.dll")).isEmpty());
    }

    @Test
    public void theLoadersDllIsWinesOwnAndTheRestStays() {
        String env = "WINEDLLOVERRIDES=baselib=n,b;gameassembly=n,b;unityplayer=n,b;winhttp=n,b TU_DEBUG=noconform";
        String off = AgvnMods.withBuiltin(env, Collections.singletonList("winhttp"));
        assertEquals("WINEDLLOVERRIDES=baselib=n,b;gameassembly=n,b;unityplayer=n,b;winhttp=b TU_DEBUG=noconform", off);
        assertTrue(AgvnMods.off(off, Collections.singletonList("winhttp")));
        assertFalse(AgvnMods.off(env, Collections.singletonList("winhttp")));
        assertEquals("once is enough", off, AgvnMods.withBuiltin(off, Collections.singletonList("winhttp")));
        // names sharing one mode; a game added from the file manager has its DLLs added only as it starts
        assertEquals("WINEDLLOVERRIDES=dinput8=n,b;version=b;winmm=b",
                AgvnMods.withBuiltin("WINEDLLOVERRIDES=dinput8,version=n,b", Arrays.asList("version", "winmm")));
        assertEquals("WINEDLLOVERRIDES=winhttp=b", AgvnMods.withBuiltin("", Collections.singletonList("winhttp")));
        assertEquals("the launch keeps it", "winhttp=b;gameassembly=n,b",
                GameDllOverrides.merge("winhttp=b", Collections.singletonMap("gameassembly", "n,b")));
        assertFalse("no loader, nothing to turn off", AgvnMods.off("", Collections.emptyList()));
    }
}
