/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Collections;

/**
 * Succubus Kiss (Unity, Mali-G610, 26/10/2026) was added by picking UnityCrashHandler64.exe, which closed after 4 s with
 * nothing shown at each of five starts: an exe that is never a game starts the folder's game instead.
 */
public class AgvnHelperExeTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File unity(boolean withGame) throws Exception {
        File dir = tmp.newFolder("succubus");
        new File(dir, "Succubus Kiss_Data").mkdirs();
        new File(dir, "Succubus Kiss_Data/globalgamemanagers").createNewFile();
        new File(dir, "UnityPlayer.dll").createNewFile();
        new File(dir, "UnityCrashHandler64.exe").createNewFile();
        if (withGame) new File(dir, "Succubus Kiss.exe").createNewFile();
        return dir;
    }

    @Test
    public void whatIsNeverAGame() {
        for (String exe : new String[]{"UnityCrashHandler64.exe", "unitycrashhandler32.exe", "CrashReportClient.exe",
                "vc_redist.x64.exe", "DXSETUP.exe", "unins000.exe", "UE4PrereqSetup_x64.exe"})
            assertTrue(exe, GameExeResolver.neverAGame(exe));
        for (String exe : new String[]{"Succubus Kiss.exe", "Game.exe", "Launcher.exe", "UnityGame.exe"})
            assertFalse(exe, GameExeResolver.neverAGame(exe));
    }

    @Test
    public void theCrashHandlerStartsTheGameBesideIt() throws Exception {
        File dir = unity(true);
        File handler = new File(dir, "UnityCrashHandler64.exe"), game = new File(dir, "Succubus Kiss.exe");
        assertEquals(game, AgvnExeMain.gameFor(handler, "UNITY"));
        assertEquals("an engine not kept yet is looked for", game, AgvnExeMain.gameFor(handler, ""));
        assertNull("the game's own exe stays", AgvnExeMain.gameFor(game, "UNITY"));
        // the launch command, as AgvnExeMain.apply changes it
        String launch = AgvnExe64.swap("\"D:\\Succubus\\UnityCrashHandler64.exe\" -popupwindow",
                Collections.singletonMap("D:", dir.getParent()), exe -> AgvnExeMain.gameFor(
                        new File(dir, exe.getName()), "UNITY"));
        assertEquals("\"D:\\Succubus\\Succubus Kiss.exe\" -popupwindow", launch);
    }

    @Test
    public void noGameBesideIt() throws Exception {
        File dir = unity(false);
        assertNull(AgvnExeMain.gameFor(new File(dir, "UnityCrashHandler64.exe"), "UNITY"));
        assertNull(GameExeResolver.resolveExe(dir, GameExeResolver.Engine.UNITY));
    }

    @Test
    public void tuSuaLoiSaysTheGamesExeIsMissing() throws Exception {
        java.io.Reader in = java.nio.file.Files.newBufferedReader(new File("src/main/assets/" + AgvnProblemCatalog.ASSET).toPath(),
                java.nio.charset.StandardCharsets.UTF_8);
        AgvnProblemCatalog catalog = AgvnProblemCatalog.parse(in);
        in.close();
        AgvnEvidence ev = new AgvnEvidence();
        ev.endedByGame = true; // closed by itself after 4 s, nothing drawn
        ev.seconds = 4;
        ev.helperExe = true;
        ev.params.put("exe", "UnityCrashHandler64.exe");
        AgvnProblemCatalog.Finding f = catalog.find(ev);
        assertEquals("not-a-game", f.id());
        assertEquals("Game đang mở bằng UnityCrashHandler64.exe, không phải file chạy của game", f.title());
        assertTrue(f.cause().startsWith("UnityCrashHandler64.exe là chương trình phụ"));
        ev.helperExe = false;
        assertEquals("no-start", catalog.find(ev).id());
    }
}
