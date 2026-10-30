/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class AgvnUnrealLogsTest {
    @Test
    public void findsTheProjectsLogsAndNewestCrashWhateverTheFolderIsCalled() throws Exception {
        // an Unreal Engine 5 game copied as "GAME-VIET-HOA": its project is Ursa, so 0.1.70 copied no engine log
        File root = Files.createTempDirectory("ue").toFile(), game = new File(root, "GAME-VIET-HOA");
        File profile = new File(root, "xuser");
        File exe = file(game, "Ursa/Binaries/Win64/Ursa-Win64-Shipping.exe", 0);
        assertTrue(new File(game, "Engine/Binaries/ThirdParty").mkdirs());
        File log = file(profile, "AppData/Local/Ursa/Saved/Logs/Ursa.log", 3000);
        File backup = file(profile, "AppData/Local/Ursa/Saved/Logs/Ursa-backup-2026.10.30-10.00.00.log", 2000);
        file(profile, "AppData/Local/Ursa/Saved/Logs/Ursa-backup-2026.10.29-10.00.00.log", 1000);
        file(profile, "AppData/Local/Ursa/Saved/Crashes/UECC-Windows-OLD/Ursa.log", 1000);
        File crashLog = file(profile, "AppData/Local/Ursa/Saved/Crashes/UECC-Windows-NEW/Ursa.log", 4000);
        File context = file(profile, "AppData/Local/Ursa/Saved/Crashes/UECC-Windows-NEW/CrashContext.runtime-xml", 4000);
        File dump = file(profile, "AppData/Local/Ursa/Saved/Crashes/UECC-Windows-NEW/UEMinidump.dmp", 4000);
        new File(profile, "AppData/Local/Ursa/Saved/Crashes/UECC-Windows-NEW").setLastModified(4000);
        new File(profile, "AppData/Local/Ursa/Saved/Crashes/UECC-Windows-OLD").setLastModified(1000);

        assertEquals("[Ursa, GAME-VIET-HOA]", AgvnUnrealLogs.projects(exe, game).keySet().toString());
        List<File> files = existing(AgvnEngineLogs.candidates(exe, game, "UNREAL", profile));
        assertTrue(files.toString(), files.contains(log));
        assertTrue(files.contains(backup));
        assertTrue(files.contains(crashLog));
        assertTrue(files.contains(context));
        assertFalse("no minidump", files.contains(dump));
        assertEquals("the newest crash and two logs", 4, files.size());

        // the game started from its bootstrap Ursa.exe at the top: the Ursa folder still names the project
        File bootstrap = file(game, "Ursa.exe", 0);
        assertEquals("[Ursa, GAME-VIET-HOA]", AgvnUnrealLogs.projects(bootstrap, game).keySet().toString());
        assertEquals(4, existing(AgvnEngineLogs.candidates(bootstrap, game, "UNREAL", profile)).size());
    }

    private static File file(File dir, String path, long time) throws Exception {
        File f = new File(dir, path);
        assertTrue(f.getParentFile().isDirectory() || f.getParentFile().mkdirs());
        Files.write(f.toPath(), new byte[]{1});
        if (time > 0) assertTrue(f.setLastModified(time));
        return f;
    }

    private static List<File> existing(List<File> files) {
        List<File> out = new ArrayList<>();
        for (File f : files) if (f.isFile() && !out.contains(f)) out.add(f);
        return out;
    }
}
