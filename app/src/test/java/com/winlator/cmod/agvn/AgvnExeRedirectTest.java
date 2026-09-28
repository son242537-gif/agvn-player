/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AgvnExeRedirectTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static File touch(File f) throws IOException {
        f.getParentFile().mkdirs();
        assertTrue(f.createNewFile());
        return f;
    }

    private static String norm(String path) {
        return path == null ? null : path.replace(File.separatorChar, '/');
    }

    /** The owner's AV Director Life layout: root bootstrap, Shipping exe under <Name>/Binaries/Win64, Engine folder. */
    private File unrealGame() throws IOException {
        File root = tmp.newFolder("Games", "AV");
        touch(new File(root, "AVDirectorLife.exe"));
        touch(new File(root, "AVDirectorLife/Binaries/Win64/AVDirectorLife-Win64-Shipping.exe"));
        touch(new File(root, "AVDirectorLife/Binaries/Win64/winmm.dll"));
        touch(new File(root, "Engine/Binaries/ThirdParty/x.dll"));
        return root;
    }

    @Test
    public void bootstrapRedirectsToShipping() throws Exception {
        File root = unrealGame();
        File shipping = new File(root, "AVDirectorLife/Binaries/Win64/AVDirectorLife-Win64-Shipping.exe");
        assertEquals(shipping, AgvnExeRedirect.redirectUnrealBootstrap(new File(root, "AVDirectorLife.exe")));
        assertEquals(shipping, AgvnExeRedirect.redirectUnrealBootstrap(shipping));
    }

    @Test
    public void bootstrapWithOtherNameNeedsEngineFolder() throws Exception {
        File root = tmp.newFolder("ue");
        File launcher = touch(new File(root, "Play Game.exe"));
        File shipping = touch(new File(root, "Proj/Binaries/Win64/Proj-Win64-Shipping.exe"));
        assertEquals(launcher, AgvnExeRedirect.redirectUnrealBootstrap(launcher));
        assertTrue(new File(root, "Engine").mkdir());
        assertEquals(shipping, AgvnExeRedirect.redirectUnrealBootstrap(launcher));
        // tools next to the game keep launching themselves
        for (String tool : new String[]{"Config.exe", "Launcher.exe", "GameSettings.exe", "unins000.exe", "Setup.exe"}) {
            File exe = touch(new File(root, tool));
            assertEquals(tool, exe, AgvnExeRedirect.redirectUnrealBootstrap(exe));
        }
        File big = touch(new File(root, "Other.exe"));
        grow(big, AgvnExeRedirect.BOOTSTRAP_MAX_BYTES + 1);
        assertEquals(big, AgvnExeRedirect.redirectUnrealBootstrap(big));
    }

    @Test
    public void exactProjectNameRedirectsWhateverTheSize() throws Exception {
        File root = unrealGame();
        File bootstrap = new File(root, "AVDirectorLife.exe");
        grow(bootstrap, 2 * AgvnExeRedirect.BOOTSTRAP_MAX_BYTES);
        assertEquals(new File(root, "AVDirectorLife/Binaries/Win64/AVDirectorLife-Win64-Shipping.exe"),
                AgvnExeRedirect.redirectUnrealBootstrap(bootstrap));
    }

    private static void grow(File f, long length) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            raf.setLength(length);
        }
    }

    @Test
    public void devExeInWin64PicksShippingOfSameProject() throws Exception {
        File win64 = tmp.newFolder("Proj", "Binaries", "Win64");
        File shipping = touch(new File(win64, "Proj-Win64-Shipping.exe"));
        File dev = touch(new File(win64, "Proj.exe"));
        File debug = touch(new File(win64, "Proj-Win64-DebugGame.exe"));
        File crash = touch(new File(win64, "CrashReportClient.exe"));
        assertEquals(shipping, AgvnExeRedirect.redirectUnrealBootstrap(dev));
        assertEquals(shipping, AgvnExeRedirect.redirectUnrealBootstrap(debug));
        assertEquals(crash, AgvnExeRedirect.redirectUnrealBootstrap(crash));
    }

    @Test
    public void unrelatedExeUnchanged() throws Exception {
        File dir = tmp.newFolder("gm");
        File game = touch(new File(dir, "game.exe"));
        touch(new File(dir, "data.win"));
        assertEquals(game, AgvnExeRedirect.redirectUnrealBootstrap(game));
        File missing = new File(dir, "missing.exe");
        assertEquals(missing, AgvnExeRedirect.redirectUnrealBootstrap(missing));
        assertNull(AgvnExeRedirect.redirectUnrealBootstrap(null));
    }

    @Test
    public void toUnixPathMapsDosDrives() {
        Map<String, String> drives = new HashMap<>();
        drives.put("D:", "/storage/emulated/0/Download");
        drives.put("F", "/storage/emulated/0/");
        String expected = "/storage/emulated/0/Download/Games/x.exe";
        assertEquals(expected, AgvnExeRedirect.toUnixPath("D:\\Games\\x.exe", drives));
        assertEquals(expected, AgvnExeRedirect.toUnixPath("\"D:\\Games\\x.exe\"", drives));
        assertEquals(expected, AgvnExeRedirect.toUnixPath("d:\\Games\\\\x.exe -windowed", drives));
        assertEquals("/storage/emulated/0/My Game/y.exe", AgvnExeRedirect.toUnixPath("F:\\My Game\\y.exe", drives));
        assertNull(AgvnExeRedirect.toUnixPath("Q:\\Games\\x.exe", drives));
        assertNull(AgvnExeRedirect.toUnixPath("D:x.exe", drives));
        assertNull(AgvnExeRedirect.toUnixPath("x.exe", drives));
        assertNull(AgvnExeRedirect.toUnixPath(null, drives));
        assertNull(AgvnExeRedirect.toUnixPath("D:\\Games\\x.exe", (Map<String, String>) null));
        assertEquals("/a b/c.exe", AgvnExeRedirect.toUnixPath("\"/a b/c.exe\"", Collections.<String, String>emptyMap()));
    }

    @Test
    public void effectivePathKeepsDosFormAndArgs() throws Exception {
        File games = unrealGame().getParentFile();
        Map<String, String> drives = Collections.singletonMap("D", games.getAbsolutePath());
        String expected = "\"D:\\AV\\AVDirectorLife\\Binaries\\Win64\\AVDirectorLife-Win64-Shipping.exe\"";
        assertEquals(expected, AgvnExeRedirect.effectivePath("D:\\AV\\AVDirectorLife.exe", drives));
        assertEquals(expected + " -dx11", AgvnExeRedirect.effectivePath("\"D:\\AV\\AVDirectorLife.exe\" -dx11", drives));
        String shipping = "D:\\AV\\AVDirectorLife\\Binaries\\Win64\\AVDirectorLife-Win64-Shipping.exe";
        assertSame(shipping, AgvnExeRedirect.effectivePath(shipping, drives));
        String lnk = "C:\\users\\xuser\\Desktop\\AV.lnk";
        assertSame(lnk, AgvnExeRedirect.effectivePath(lnk, drives));
        String unmapped = "E:\\AV\\AVDirectorLife.exe";
        assertSame(unmapped, AgvnExeRedirect.effectivePath(unmapped, drives));
    }

    @Test
    public void effectivePathUnixForm() throws Exception {
        Assume.assumeTrue("unix paths only", File.separatorChar == '/');
        File root = unrealGame();
        String bootstrap = "\"" + new File(root, "AVDirectorLife.exe").getAbsolutePath() + "\"";
        String expected = "\"" + root.getAbsolutePath() + "/AVDirectorLife/Binaries/Win64/AVDirectorLife-Win64-Shipping.exe\"";
        assertEquals(expected, AgvnExeRedirect.effectivePath(bootstrap, Collections.<String, String>emptyMap()));
    }

    @Test
    public void dllOverridesUseEffectiveDosPath() throws Exception {
        File games = unrealGame().getParentFile();
        Map<String, String> drives = Collections.singletonMap("D", games.getAbsolutePath());
        String effective = AgvnExeRedirect.effectivePath("D:\\AV\\AVDirectorLife.exe", drives);
        com.winlator.cmod.core.EnvVars env = new com.winlator.cmod.core.EnvVars();
        GameDllOverrides.applyAtLaunch(env, effective, drives);
        assertEquals("winmm=n,b", env.get("WINEDLLOVERRIDES"));
        com.winlator.cmod.core.EnvVars untouched = new com.winlator.cmod.core.EnvVars();
        GameDllOverrides.applyAtLaunch(untouched, "C:\\users\\xuser\\Desktop\\AV.lnk", drives);
        assertEquals("", untouched.get("WINEDLLOVERRIDES"));
        assertEquals(norm(new File(games, "AV/x.exe").getAbsolutePath()),
                norm(AgvnExeRedirect.toUnixPath("D:\\AV\\x.exe", drives)));
    }
}
