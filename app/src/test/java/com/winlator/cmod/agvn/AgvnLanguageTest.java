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
    public void everyLanguageHasEveryAutoFixText() throws Exception {
        java.util.regex.Pattern placeholder = java.util.regex.Pattern.compile("\\{[A-Za-z0-9_]+\\}");
        for (String lang : new String[]{"en", "th", "in", "tl", "zh"}) {
            AgvnProblemCatalog vi = catalog(), ours = catalog();
            try (java.io.Reader in = java.nio.file.Files.newBufferedReader(
                    new java.io.File("src/main/assets/agvn/game-problems-" + lang + ".json").toPath())) {
                ours.localize(in);
            }
            for (AgvnProblemCatalog.Problem p : vi.all()) {
                AgvnProblemCatalog.Problem t = ours.byId(p.id);
                assertTrue(lang + " " + p.id + " is translated", !t.title.equals(p.title) || !t.cause.equals(p.cause));
                for (String[] pair : new String[][]{{p.title, t.title}, {p.cause, t.cause}}) {
                    java.util.List<String> a = new java.util.ArrayList<>(), b = new java.util.ArrayList<>();
                    for (java.util.regex.Matcher m = placeholder.matcher(pair[0]); m.find(); ) a.add(m.group());
                    for (java.util.regex.Matcher m = placeholder.matcher(pair[1]); m.find(); ) b.add(m.group());
                    java.util.Collections.sort(a);
                    java.util.Collections.sort(b);
                    assertEquals(lang + " " + p.id + " keeps its placeholders", a, b);
                }
            }
        }
    }

    private static AgvnProblemCatalog catalog() throws Exception {
        try (java.io.Reader in = java.nio.file.Files.newBufferedReader(
                new java.io.File("src/main/assets/" + AgvnProblemCatalog.ASSET).toPath())) {
            return AgvnProblemCatalog.parse(in);
        }
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
