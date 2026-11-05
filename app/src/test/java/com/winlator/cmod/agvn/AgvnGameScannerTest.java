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
    public void wrapperFolderAtTheDepthLimitIsLookedInto() throws Exception {
        File sd = tmp.newFolder("sd3");
        // copied from the PC as is: E:\NINJA DISGRACE\Shinobi\Shinobi.exe
        touch(new File(sd, "NINJA DISGRACE/Shinobi/Shinobi.exe"));
        touch(new File(sd, "NINJA DISGRACE/Shinobi/UnityPlayer.dll"));
        touch(new File(sd, "NINJA DISGRACE/Shinobi/UnityCrashHandler64.exe"));
        touch(new File(sd, "NINJA DISGRACE/read me.txt"));                    // files beside the folder are fine
        touch(new File(sd, "Pack/A/B/Game/Game.exe"));                         // two wrappers in a row
        touch(new File(sd, "Pack/A/B/Game/UnityPlayer.dll"));
        touch(new File(sd, "Deep/1/2/3/Game/Game.exe"));                       // three wrappers: too deep
        touch(new File(sd, "Deep/1/2/3/Game/UnityPlayer.dll"));
        touch(new File(sd, "Two/Left/Game.exe"));                              // two folders: not a wrapper
        touch(new File(sd, "Two/Left/UnityPlayer.dll"));
        touch(new File(sd, "Two/Right/x.txt"));
        touch(new File(sd, "Setup/setup.exe"));                                // has an exe: not a wrapper
        touch(new File(sd, "Setup/Game/Game.exe"));
        touch(new File(sd, "Setup/Game/UnityPlayer.dll"));
        List<File> games = AgvnGameScanner.scan(Arrays.asList(new AgvnGameScanner.Root(sd, 1)));
        StringBuilder names = new StringBuilder();
        for (File g : games) names.append(g.getParentFile().getName()).append('/').append(g.getName()).append('|');
        assertEquals("NINJA DISGRACE/Shinobi|", names.toString());
        // one more level of depth reaches the others that sit two folders down
        games = AgvnGameScanner.scan(Arrays.asList(new AgvnGameScanner.Root(sd, 2)));
        names.setLength(0);
        for (File g : games) names.append(g.getName()).append('|');
        assertEquals("Shinobi|Game|Game|Left|", names.toString()); // Deep (3 wrappers) stays out
    }

    @Test
    public void defaultProfileIsValidForDetectedGame() throws Exception {
        File dir = tmp.newFolder("My Game");
        touch(new File(dir, "Game/Binaries/Win64/Game-Win64-Shipping.exe"));
        // characters Windows forbids in names are replaced (tested on the string: Windows cannot create such a folder)
        assertEquals("My Game", AgvnProfile.defaultFor("My: Game").name);
        AgvnProfile p = AgvnProfile.defaultFor(dir.getName());
        assertEquals("My Game", p.name);
        assertEquals("Game/Binaries/Win64/Game-Win64-Shipping.exe", AgvnProfileValidator.validate(p, dir));
        assertTrue(p.isSimulatedTouchscreen());
    }

    @Test
    public void pickedFolderCountsItselfAndEachGameIsListedOnce() throws Exception {
        File sd = tmp.newFolder("sd2");
        touch(new File(sd, "Games PC/One Game/Game.exe"));
        touch(new File(sd, "Games PC/One Game/UnityPlayer.dll"));
        touch(new File(sd, "AGVN/a/b/Deep Game/agvn-profile.json"));
        File picked = new File(sd, "Games PC/One Game");
        List<File> games = AgvnGameScanner.scan(Arrays.asList(
                new AgvnGameScanner.Root(picked, 3, true),
                new AgvnGameScanner.Root(sd, 2),                        // reaches AGVN/a/b but not Deep Game
                new AgvnGameScanner.Root(new File(sd, "AGVN"), 3),       // AGVN again with a bigger budget
                new AgvnGameScanner.Root(new File(sd, "Games PC"), 2)));  // One Game again
        StringBuilder names = new StringBuilder();
        for (File g : games) names.append(g.getName()).append('|');
        assertEquals("One Game|Deep Game|", names.toString());
    }

    @Test
    public void phoneCopiesWithoutExeAreGamesForChayNhe() throws Exception {
        File sd = tmp.newFolder("sd-phone");
        // JoiPlay-style copies: no Windows .exe, still runnable on "Chạy nhẹ"
        File ace = new File(sd, "RPG/Ace Viet hoa");
        touch(new File(ace, "Game.rgss3a"));
        java.nio.file.Files.write(new File(ace, "Game.ini").toPath(), "[Game]\nLibrary=System\\RGSS301.dll\n".getBytes("UTF-8"));
        touch(new File(sd, "RPG/MV Game/www/index.html"));
        touch(new File(sd, "RPG/MV Game/www/js/rpg_core.js"));
        touch(new File(sd, "RPG/Tyrano Game/index.html"));
        new File(sd, "RPG/Tyrano Game/tyrano").mkdirs();
        touch(new File(sd, "RPG/Web page/index.html"));                    // just a page: not a game
        touch(new File(sd, "RPG/RM2003/RPG_RT.ini"));                      // 2003 without exe: Wine only, so not a game
        touch(new File(sd, "RPG/RM2003/RPG_RT.ldb"));
        List<File> games = AgvnGameScanner.scan(Arrays.asList(new AgvnGameScanner.Root(sd, 2)));
        StringBuilder names = new StringBuilder();
        for (File g : games) names.append(g.getName()).append('|');
        assertEquals("Ace Viet hoa|MV Game|Tyrano Game|", names.toString());
    }

    @Test
    public void srpgStudioIsAGameWithoutAnyDll() throws Exception {
        // report of 05/11/2026: an SRPG Studio folder has no DLL, and "Thêm game" left it out without a word
        File game = tmp.newFolder("srpg");
        for (String n : new String[]{"game.exe", "game.ini", "data.dts", "runtime.rts", "environment.evs", "system.dat",
                "Plugin/a.js", "Save/save01.sav"}) touch(new File(game, n));
        GameExeResolver.Engine srpg = GameExeResolver.Engine.SRPG_STUDIO;
        assertEquals(srpg, GameExeResolver.detectEngine(game));
        assertTrue(AgvnGameScanner.isGameDir(game));
        assertEquals("game.exe", GameExeResolver.resolveExe(game, srpg));
        assertEquals(AgvnLayouts.RPG, AgvnLayouts.kindFor(srpg));
        assertTrue("its time runs per frame", AgvnFrameLock.frameLocked(srpg.name()));
        assertEquals(new File(game, "Save"), AgvnSaveLocations.inGameFolder(game, srpg.name()).get(0).dir);
    }

    @Test
    public void aFolderWithAnExeNoRuleKnowsIsListedApart() throws Exception {
        File sd = tmp.newFolder("sd-unsure");
        touch(new File(sd, "AGVN/Plain Game/Game.exe"));            // an engine of its own: no DLL beside the exe
        touch(new File(sd, "AGVN/Plain Game/assets/level1.bin"));
        touch(new File(sd, "AGVN/Unity Game/Game.exe"));
        touch(new File(sd, "AGVN/Unity Game/UnityPlayer.dll"));
        touch(new File(sd, "AGVN/Setup Only/setup.exe"));           // an installer
        touch(new File(sd, "AGVN/Setup Only/data.bin"));
        touch(new File(sd, "AGVN/Zip/Game.exe"));                   // an exe and archives only
        touch(new File(sd, "AGVN/Zip/Game.zip"));
        touch(new File(sd, "AGVN/Pack/Menu.exe"));                  // above a game: the game is listed, not it
        touch(new File(sd, "AGVN/Pack/readme.dat"));
        touch(new File(sd, "AGVN/Pack/Inner/Game.exe"));
        touch(new File(sd, "AGVN/Pack/Inner/UnityPlayer.dll"));
        touch(new File(sd, "Download/game.exe"));                   // the search's own folder
        touch(new File(sd, "Download/video.mp4"));
        AgvnGameScanner.Found found = AgvnGameScanner.find(Arrays.asList(
                new AgvnGameScanner.Root(new File(sd, "AGVN"), 3),
                new AgvnGameScanner.Root(new File(sd, "Download"), 2),
                new AgvnGameScanner.Root(sd, 1)));
        assertEquals("Inner|Unity Game|", names(found.games));
        assertEquals("Plain Game|", names(found.unsure));
    }

    private static String names(List<File> dirs) {
        StringBuilder names = new StringBuilder();
        for (File d : dirs) names.append(d.getName()).append('|');
        return names.toString();
    }
}
