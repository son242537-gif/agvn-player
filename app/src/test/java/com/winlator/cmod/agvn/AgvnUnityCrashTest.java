/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** A crash Unity's own crash handler caught, read from the lines Become A Vtuber (Unity 6) really wrote. */
public class AgvnUnityCrashTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    private static AgvnProblemCatalog catalog;

    /** The game's start, as in its Player.log on a Redmi K30 5G (Adreno 620, Proton 10). */
    private static final List<String> START = Arrays.asList(
            "Initialize engine version: 6000.0.30f1 (62b05ba0686a)",
            "    Renderer: Wrapper(Turnip Adreno (TM) 620) (ID=0x6020001)",
            "<RI> Initialized touch support.\r",
            "Texture 'mini game1_great' has dimensions 328 x 139 which is not supported for format 'RGBA Compressed "
                    + "DXT5|BC3' (requires multiple-of-four dimensions). Falling back to 'RGBA 32 bit'.");
    private static final String SYM = "SymInit: Symbol-SearchPath: '.;D:\\G1\\BECOME-A-VTUBER-GAMEHUB;C:\\windows;"
            + "C:\\windows\\system32;', symOptions: 534, UserName: 'xuser'";
    /** The modules Unity listed (some of them), twice: after "Crash!!!" and before the stack. */
    private static final List<String> MODULES = Arrays.asList(SYM, "OS-Version: 10.0.0",
            "D:\\G1\\BECOME-A-VTUBER-GAMEHUB\\V-Lover.exe:V-Lover.exe (0000000140000000), size: 696320 (result: 0), "
                    + "SymType: '-deferred-', PDB: '', fileVersion: 6000.0.30.45147",
            "C:\\windows\\system32\\libarm64ecfex.dll:libarm64ecfex.dll (0000007FFF5A0000), size: 9474048 (result: 0), "
                    + "SymType: '-deferred-', PDB: ''",
            "D:\\G1\\BECOME-A-VTUBER-GAMEHUB\\UnityPlayer.dll:UnityPlayer.dll (0000007FE9750000), size: 34529280 "
                    + "(result: 0), SymType: '-deferred-', PDB: '', fileVersion: 6000.0.30.45147",
            "D:\\G1\\BECOME-A-VTUBER-GAMEHUB\\GameAssembly.dll:GameAssembly.dll (0000007FC0F30000), size: 74260480 "
                    + "(result: 0), SymType: '-deferred-', PDB: ''");
    private static final List<String> STACK = Arrays.asList("",
            "========== OUTPUTTING STACK TRACE ==================", "",
            "RtlVirtualUnwind produced an access violation. Aborting stack walk.",
            "  ERROR: SymGetSymFromAddr64, GetLastError: 'No more files.' (Address: 0000007FEA028B1A)",
            "0x0000007FEA028B1A (unityplayer) (function-name not available)",
            "  ERROR: SymGetSymFromAddr64, GetLastError: 'Success.' (Address: 0000007FE9BB8F00)",
            "0x0000007FE9BB8F00 (unityplayer) (function-name not available)",
            "<Missing stacktrace information>", "",
            "========== END OF STACKTRACE ===========", "");
    private static final List<String> REPORT = Arrays.asList("A crash has been intercepted by the crash handler. For "
                    + "call stack and other details, see the latest crash report generated in:",
            " * C:/users/xuser/AppData/Local/Temp/Hentopia/V-Lover/Crashes");
    /** Unity's error.log, as its crash handler writes one. */
    private static final String ERROR_LOG = "V-Lover.exe caused an Access Violation (0xc0000005)\r\n"
            + "  in module UnityPlayer.dll at 0033:ea028b1a.\r\n\r\nError occurred at 2026-10-06_200552.\r\n"
            + "Read from location 0000000000000000 caused an access violation.\r\n";

    @BeforeClass
    public static void load() throws IOException {
        try (Reader in = Files.newBufferedReader(new File("src/main/assets/" + AgvnProblemCatalog.ASSET).toPath(),
                StandardCharsets.UTF_8)) {
            catalog = AgvnProblemCatalog.parse(in);
        }
    }

    private static List<String> log(boolean caught) {
        List<String> lines = new ArrayList<>(START);
        lines.add("Crash!!!");
        lines.addAll(MODULES);
        lines.addAll(STACK.subList(0, 4));
        lines.addAll(MODULES); // the stack walk lists them again
        lines.addAll(STACK.subList(4, STACK.size()));
        if (caught) lines.addAll(REPORT);
        return lines;
    }

    @Test
    public void whereUnityCaughtTheCrash() {
        List<String> lines = log(true);
        assertTrue(AgvnUnityCrash.crashed(lines) && AgvnUnityCrash.complete(lines));
        assertEquals("the same spot in all five runs, whatever the module's base",
                "UnityPlayer.dll+0x8d8b1a", AgvnUnityCrash.where(lines));
        assertEquals("Unity báo crash trong UnityPlayer.dll+0x8d8b1a", AgvnUnityCrash.describe(lines));
        assertEquals("users/xuser/AppData/Local/Temp/Hentopia/V-Lover/Crashes", AgvnUnityCrash.reportFolder(lines));
        assertEquals("Access Violation 0xc0000005, đọc 0x0", AgvnUnityCrash.exception(ERROR_LOG));
        assertNull(AgvnUnityCrash.exception("nothing here"));

        assertFalse("a game that loads: no crash", AgvnUnityCrash.crashed(START));
        assertNull(AgvnUnityCrash.describe(START));
        List<String> midway = new ArrayList<>(START);
        midway.add("Crash!!!");
        midway.addAll(MODULES);
        assertTrue(AgvnUnityCrash.crashed(midway));
        assertFalse("Unity still writes it", AgvnUnityCrash.complete(midway));
        assertEquals("Unity báo crash", AgvnUnityCrash.describe(midway));
        assertTrue("Unity before 2019 ends with the stack", AgvnUnityCrash.complete(log(false)));
        List<String> longCrash = lines.subList(lines.indexOf("========== OUTPUTTING STACK TRACE =================="),
                lines.size()); // "Crash!!!" and the first module list are further back than the end read of the log
        assertEquals("UnityPlayer.dll+0x8d8b1a", AgvnUnityCrash.where(longCrash));

        List<String> named = new ArrayList<>(midway);
        named.add("0x0000007FC1234567 (GameAssembly) Player_Update_m1234");
        assertEquals("GameAssembly!Player_Update_m1234", AgvnUnityCrash.where(named));
        List<String> unlisted = new ArrayList<>(midway);
        unlisted.add("0x0000007FAB000010 (mono-2.0-bdwgc) (function-name not available)");
        assertEquals("mono-2.0-bdwgc", AgvnUnityCrash.where(unlisted));
        assertNull("drive C only", AgvnUnityCrash.reportFolder(Collections.singletonList(" * C:/users/../../x")));
        assertNull(AgvnUnityCrash.reportFolder(Collections.singletonList(" * D:/G1/Crashes")));
    }

    @Test
    public void theReportGoesWithTheSessionLogs() throws IOException {
        long start = System.currentTimeMillis();
        File driveC = tmp.newFolder("xuser-2", ".wine", "drive_c");
        File unity = new File(driveC, "users/xuser/AppData/LocalLow/Hentopia/V-Lover");
        assertTrue(unity.mkdirs());
        File log = write(new File(unity, "Player.log"), String.join("\r\n", log(true)) + "\r\n");
        File prev = write(new File(unity, "Player-prev.log"), String.join("\n", log(true)));
        File report = new File(driveC, "users/xuser/AppData/Local/Temp/Hentopia/V-Lover/Crashes/Crash_2026-10-06_1305");
        assertTrue(report.mkdirs());
        write(new File(report, "error.log"), ERROR_LOG);
        List<File> logs = Arrays.asList(log, prev);
        assertEquals(driveC, AgvnUnityCrashFiles.driveC(log));
        assertFalse("the last session's log, moved aside as Unity starts", AgvnUnityCrashFiles.session(prev, start));

        assertEquals("Unity báo crash trong UnityPlayer.dll+0x8d8b1a", AgvnUnityCrashFiles.inLogs(logs, start));
        File dir = tmp.newFolder("session");
        assertEquals("Game bị lỗi (crash) – Unity báo crash trong UnityPlayer.dll+0x8d8b1a: Access Violation "
                        + "0xc0000005, đọc 0x0 (chi tiết: Player.log, unity-crash.txt)",
                AgvnCrashScan.sessionError(Collections.emptyList(), logs, start, dir));
        assertEquals(ERROR_LOG, new String(Files.readAllBytes(new File(dir, AgvnUnityCrashFiles.REPORT_FILE).toPath()),
                StandardCharsets.UTF_8));

        assertTrue(log.setLastModified(start - 60_000));
        assertNull("a log of an earlier session", AgvnUnityCrashFiles.inLogs(logs, start));
        write(log, String.join("\n", START));
        assertNull("a game that loads", AgvnUnityCrashFiles.inLogs(logs, start));
    }

    @Test
    public void theDoctorAsksAboutTheCrashUnityCaught() {
        AgvnEvidence ev = new AgvnEvidence();
        ev.lines.addAll(log(true));
        assertNull("no crash Wine saw, and the game drew: nothing to ask", catalog.find(withStart(ev)));
        AgvnUnityCrash.into(ev, AgvnUnityCrash.describe(ev.lines));
        AgvnProblemCatalog.Finding f = catalog.find(ev);
        assertEquals("unity-crash", f.id());
        assertEquals(Arrays.asList("emulator-stable", "wine-old", "dxvk-other", "driver-other", "reset", "send-logs"),
                f.fixes());
        assertTrue(f.cause().contains("Unity báo crash trong UnityPlayer.dll+0x8d8b1a"));
        assertFalse("a crash is never a good run", ev.good());
        ev.changed = true;
        assertEquals("crash-after-change", catalog.find(ev).id());

        AgvnEvidence wine = new AgvnEvidence(); // a crash Wine printed keeps its own words and problem
        wine.crashed = true;
        wine.params.put("crash", "page fault đọc 0x0 trong game.exe+0x1234");
        AgvnUnityCrash.into(wine, "Unity báo crash");
        assertEquals("page fault đọc 0x0 trong game.exe+0x1234", wine.params.get("crash"));
        assertEquals("crash", catalog.find(wine).id());
    }

    private static AgvnEvidence withStart(AgvnEvidence ev) {
        ev.started = ev.bigWindowSeen = ev.playerQuit = true;
        ev.seconds = 30;
        return ev;
    }

    private static File write(File f, String text) throws IOException {
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return f;
    }
}
