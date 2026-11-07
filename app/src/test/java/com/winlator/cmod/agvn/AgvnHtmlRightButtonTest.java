/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** An RPG Maker MV action game (QABS) attacks on the right mouse button, which "Chạy nhẹ" had no way to hold (07/11/2026). */
public class AgvnHtmlRightButtonTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static AgvnLightLayout layout(String kind) throws Exception {
        String json = new String(Files.readAllBytes(new File("src/main/assets/" + AgvnLayouts.assetFor(kind)).toPath()),
                StandardCharsets.UTF_8);
        return AgvnLightLayout.parse(kind, json, AgvnHtmlGame.RUNNER_HTML);
    }

    @Test
    public void aGameWhosePluginsUseTheRightButtonGetsAKeyForIt() throws Exception {
        File plugins = new File(tmp.getRoot(), "www/js/plugins.js");
        plugins.getParentFile().mkdirs();
        Files.write(plugins.toPath(), "var $plugins = [{\"name\":\"QABS\",\"parameters\":{\"Attack\":\"{\\\"input\\\":[\\\"mouse2\\\"]}\"}}];"
                .getBytes(StandardCharsets.UTF_8));
        assertTrue(AgvnHtmlRightButton.wanted(new File(tmp.getRoot(), "www")));
        assertFalse(AgvnHtmlRightButton.wanted(tmp.getRoot()));
        assertFalse(AgvnHtmlRightButton.named("var $plugins = [{\"name\":\"YEP_CoreEngine\"}];"));

        AgvnLightLayout rpg = layout(AgvnLayouts.RPG);
        int keys = rpg.elements.size();
        assertTrue(AgvnHtmlRightButton.add(rpg, "Chuột phải"));
        assertEquals(keys + 1, rpg.elements.size());
        AgvnLightLayout.Element added = rpg.elements.get(keys);
        assertEquals("Chuột phải", added.text);
        assertEquals(AgvnHtmlRightButton.BINDING, added.bindings[0]);
        assertFalse("once", AgvnHtmlRightButton.add(rpg, "Chuột phải"));
        assertFalse("the VN key set's Menu is the right button already",
                AgvnHtmlRightButton.add(layout(AgvnLayouts.VN), "Chuột phải"));
    }

    @Test
    public void theRightButtonIsHeldAsLongAsItsKey() {
        String down = AgvnHtmlKeys.rightButton(true), up = AgvnHtmlKeys.rightButton(false);
        assertTrue(down.contains("m('mousedown',2,2)") && !down.contains("mouseup"));
        assertTrue(up.contains("m('mouseup',2,0)") && up.contains("contextmenu") && !up.contains("mousedown"));
        assertTrue("where the player last touched, on an RPG Maker page", down.contains("window.TouchInput&&window.__agvnPointer"));
    }
}
