package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class AgvnGameScannerTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static void touch(File f) throws IOException {
        f.getParentFile().mkdirs();
        assertTrue(f.createNewFile());
    }

    @Test
    public void findsNestedGamesWithoutProfile() throws Exception {
        File sd = tmp.newFolder("sdcard");
        // PC layout copied as is: AGVN/Games/<game>/<Project>/Binaries/Win64/...-Shipping.exe
        touch(new File(sd, "AGVN/Games/LEGEND CLEANER GAMEHUB LITE/Cleaner_Densetsu.exe"));
        touch(new File(sd, "AGVN/Games/LEGEND CLEANER GAMEHUB LITE/Cleaner_Densetsu/Binaries/Win64/Cleaner_Densetsu-Win64-Shipping.exe"));
        touch(new File(sd, "AGVN/Games/LEGEND CLEANER GAMEHUB LITE/Engine/Binaries/ThirdParty/x.dll"));
        touch(new File(sd, "AGVN/Other/agvn-profile.json"));
        touch(new File(sd, "Download/setup.exe"));                  // lone installer: not a game
        touch(new File(sd, "Download/Unity Game/Game.exe"));
        touch(new File(sd, "Download/Unity Game/UnityPlayer.dll"));
        touch(new File(sd, "DCIM/Camera/x.exe"));                   // skipped folder

        List<File> games = AgvnGameScanner.scan(Arrays.asList(
                new AgvnGameScanner.Root(new File(sd, "AGVN"), 3),
                new AgvnGameScanner.Root(new File(sd, "Download"), 2),
                new AgvnGameScanner.Root(sd, 1)));
        StringBuilder names = new StringBuilder();
        for (File g : games) names.append(g.getName()).append('|');
        assertEquals("LEGEND CLEANER GAMEHUB LITE|Other|Unity Game|", names.toString());
    }

    @Test
    public void defaultProfileIsValidForDetectedGame() throws Exception {
        File dir = tmp.newFolder("My: Game");
        touch(new File(dir, "Game/Binaries/Win64/Game-Win64-Shipping.exe"));
        AgvnProfile p = AgvnProfile.defaultFor(dir.getName());
        assertEquals("My  Game", p.name);
        assertEquals("Game/Binaries/Win64/Game-Win64-Shipping.exe", AgvnProfileValidator.validate(p, dir));
        assertTrue(p.isSimulatedTouchscreen());
    }
}
