/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

/** "Tự sửa lỗi" for "Chạy nhẹ" games: how a light session ended, read through game-problems.json. */
public class AgvnLightDoctorTest {
    @BeforeClass
    public static void load() throws IOException {
        if (AgvnDoctorTest.catalog == null) AgvnDoctorTest.load();
    }

    private static Properties session(String runner, boolean ended) {
        Properties p = new Properties();
        p.setProperty(AgvnLightSession.RUNNER, runner);
        p.setProperty(AgvnLightSession.STARTED, "1");
        if (ended) p.setProperty(AgvnLightSession.ENDED, "1");
        return p;
    }

    private static AgvnProblemCatalog.Finding find(Properties p, AgvnExitReason.Exit exit, String renpyError) {
        return AgvnDoctorTest.catalog.find(AgvnLightDoctor.evidence(p, exit, renpyError));
    }

    @Test
    public void aRenpyTracebackDuringPlay() {
        AgvnProblemCatalog.Finding f = find(session(AgvnHtmlGame.RUNNER_RENPY, true), null, "NameError: name 'quest_log' is not defined");
        assertEquals("light-script-error", f.id());
        assertTrue(f.cause(), f.cause().contains("NameError: name 'quest_log' is not defined"));
        assertEquals("[run-windows, send-logs]", f.fixes().toString());
    }

    @Test
    public void anHtmlGameThatStoppedOrLostItsPage() {
        Properties stopped = session(AgvnHtmlGame.RUNNER_HTML, true);
        stopped.setProperty(AgvnLightSession.ERROR, "ReferenceError: Game_Map_custom is not defined");
        assertEquals("light-script-error", find(stopped, null, null).id());
        Properties lost = session(AgvnHtmlGame.RUNNER_HTML, true);
        lost.setProperty(AgvnLightSession.PAGE_CRASH, "1");
        assertEquals("light-page-crash", find(lost, null, null).id());
    }

    @Test
    public void howAndroidEndedTheRunner() {
        Properties rgss = session(AgvnHtmlGame.RUNNER_RGSS, false);
        AgvnProblemCatalog.Finding crash = find(rgss, new AgvnExitReason.Exit(AgvnLightDoctor.REASON_CRASH_NATIVE, 100, "crash"), null);
        assertEquals("light-crash", crash.id());
        assertTrue(crash.cause(), crash.cause().contains("mkxp-z (RPG Maker)") && crash.cause().contains("CRASH_NATIVE"));
        assertEquals("light-frozen", find(rgss, new AgvnExitReason.Exit(AgvnLightDoctor.REASON_ANR, 100, "anr"), null).id());
        assertEquals("killed-low-memory", find(rgss, new AgvnExitReason.Exit(AgvnEvidence.REASON_LOW_MEMORY, 125, "lmk"), null).id());
        assertEquals("killed-background", find(rgss, new AgvnExitReason.Exit(AgvnEvidence.REASON_SIGNALED, 125, "kill"), null).id());
        assertNull("swiped away by the player", find(rgss, new AgvnExitReason.Exit(10, 400, "user"), null));
    }

    @Test
    public void aNormalEndAsksNothing() {
        assertNull(find(session(AgvnHtmlGame.RUNNER_RENPY, true), null, null));
        assertNull(find(session(AgvnHtmlGame.RUNNER_HTML, true), null, null));
        assertEquals("Ren'Py", AgvnLightDoctor.runnerName(AgvnHtmlGame.RUNNER_RENPY));
    }

    @Test
    public void storedQuestionsComeBackWhole() {
        Properties p = new Properties();
        p.setProperty("container", "2");
        p.setProperty("shortcut", "/x/Game.desktop");
        p.setProperty("problem", "light-crash");
        p.setProperty("time", "1759500000000");
        p.setProperty(AgvnDoctorStore.PARAM + "runner", "Ren'Py");
        AgvnDoctorStore.Entry e = new AgvnDoctorStore.Entry(p);
        assertEquals(2, e.container);
        assertEquals("/x/Game.desktop", e.shortcut);
        assertEquals("light-crash", e.problem);
        assertEquals(1759500000000L, e.time);
        assertEquals("Ren'Py", e.params.get("runner"));
    }
}
