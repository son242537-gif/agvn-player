/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

/** An Electron game added as KiriKiri before 0.1.68 still ran as KiriKiri, in Japanese, on 0.1.68 (26/10/2026). */
public class AgvnEngineRecheckTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static void touch(File f) throws IOException {
        f.getParentFile().mkdirs();
        f.createNewFile();
    }

    @Test
    public void anElectronGameStoredAsKiriKiriGetsItsEngineBack() throws Exception {
        File electron = tmp.newFolder("electron");
        touch(new File(electron, "Game.exe"));
        touch(new File(electron, "v8_context_snapshot.bin"));
        touch(new File(electron, "resources/app/package.json"));
        touch(new File(electron, "data.xp3"));
        assertEquals("UNKNOWN", AgvnEngineRecheck.engineNow("KIRIKIRI", electron));
        assertEquals("UNKNOWN", AgvnEngineRecheck.engineNow("NSCRIPTER", electron));
        assertNull("an engine no stray file gives stays", AgvnEngineRecheck.engineNow("UNITY", electron));
        assertNull(AgvnEngineRecheck.engineNow("UNKNOWN", electron));
    }

    @Test
    public void aRealKiriKiriGameKeepsItsEngine() throws Exception {
        File krkr = tmp.newFolder("krkr");
        touch(new File(krkr, "game.exe"));
        touch(new File(krkr, "data.xp3"));
        assertNull(AgvnEngineRecheck.engineNow("KIRIKIRI", krkr));
        assertNull("a folder no longer on the phone", AgvnEngineRecheck.engineNow("KIRIKIRI", new File(krkr, "gone")));
    }

    @Test
    public void theJapaneseLocaleGoesUnlessSomethingElseAsksForIt() {
        assertNull(AgvnEngineRecheck.locale(AgvnLocale.JAPANESE, null, "UNKNOWN", "Game", "Game.exe"));
        assertEquals("a kana name", AgvnLocale.JAPANESE,
                AgvnEngineRecheck.locale(AgvnLocale.JAPANESE, null, "UNKNOWN", "ゲーム", "Game.exe"));
        AgvnProfile p = AgvnProfile.defaultFor("Game");
        p.locale = "ja_JP";
        assertEquals("the profile's", AgvnLocale.JAPANESE, AgvnEngineRecheck.locale(AgvnLocale.JAPANESE, p, "UNKNOWN", "Game", "Game.exe"));
        assertEquals("the player's own", "zh_CN.UTF-8", AgvnEngineRecheck.locale("zh_CN.UTF-8", null, "UNKNOWN", "Game", "Game.exe"));
        assertNull(AgvnEngineRecheck.locale("", null, "UNKNOWN", "Game", "Game.exe"));
    }

    @Test
    public void theExeNameOfAWindowsOrUnixPath() {
        assertEquals("Game.exe", AgvnEngineRecheck.exeName("\"F:\\Games\\My Game\\Game.exe\""));
        assertEquals("Game.exe", AgvnEngineRecheck.exeName("/storage/emulated/0/Game.exe"));
        assertEquals("Game.exe", AgvnEngineRecheck.exeName("Game.exe"));
    }
}
