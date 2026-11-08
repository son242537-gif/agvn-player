/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.StringReader;
import java.util.Collections;
import java.util.Locale;

/** The app's languages (AgvnLanguage) and "Tự sửa lỗi"'s texts in them (game-problems-&lt;lang&gt;.json). */
public class AgvnLanguageTest {
    @Test
    public void eachChoiceGetsTheLocaleOfItsFolder() {
        Locale phone = new Locale("vi", "VN");
        assertEquals("en", AgvnLanguage.locale("en", phone).getLanguage());
        assertEquals("th", AgvnLanguage.locale("th", phone).getLanguage());
        // Android keeps "in" (values-in); a JVM from 17 calls the same locale "id": compared as locales
        assertEquals(new Locale("in"), AgvnLanguage.locale("in", phone));
        assertEquals("tl", AgvnLanguage.locale("tl", phone).getLanguage());
        assertEquals(Locale.SIMPLIFIED_CHINESE, AgvnLanguage.locale("zh-CN", phone));
        assertEquals("vi", AgvnLanguage.locale("vi", Locale.ENGLISH).getLanguage());
    }

    @Test
    public void thePhonesLanguageUsesTheAppsCodes() {
        assertEquals("tl", AgvnLanguage.locale(AgvnLanguage.SYSTEM, new Locale("fil", "PH")).getLanguage());
        assertEquals(new Locale("in", "ID"), AgvnLanguage.locale(AgvnLanguage.SYSTEM, new Locale("in", "ID")));
        assertEquals(Locale.GERMAN, AgvnLanguage.locale(AgvnLanguage.SYSTEM, Locale.GERMAN)); // shows Vietnamese
    }

    @Test
    public void autoFixTextsComeInTheLanguageTheyHave() {
        AgvnProblemCatalog catalog = AgvnProblemCatalog.parse(new StringReader("{\"problems\": ["
                + "{\"id\": \"crash\", \"when\": [\"crash\"], \"title\": \"Game bị lỗi\", \"cause\": \"Lỗi ({crash}).\", \"fixes\": [\"send-logs\"]},"
                + "{\"id\": \"slow\", \"when\": [\"live\"], \"title\": \"Game chậm\", \"cause\": \"Chậm.\", \"fixes\": [\"send-logs\"]}]}"));
        catalog.localize(new StringReader("{\"problems\": {\"crash\": {\"title\": \"The game crashed\", "
                + "\"cause\": \"It crashed ({crash}).\"}}}"));
        AgvnProblemCatalog.Finding f = catalog.finding("crash", Collections.singletonMap("crash", "0xC0000005"));
        assertEquals("The game crashed", f.title());
        assertEquals("It crashed (0xC0000005).", f.cause());
        assertEquals("one it lacks stays Vietnamese", "Game chậm", catalog.finding("slow", Collections.emptyMap()).title());
        catalog.localize(new StringReader("not json"));
        assertTrue("a broken file changes nothing", catalog.finding("crash", Collections.emptyMap()).title().startsWith("The"));
    }
}
