/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

/**
 * An adb start with container_id 1 for a game on Proton 10 ran it on Proton 9 (27/10/2026); a home-screen shortcut
 * keeps the container and file of a game that "Tự sửa lỗi" moved since.
 */
public class AgvnStartContainerTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void theContainerThatHoldsTheGamesFileIsItsOwn() throws Exception {
        File nine = tmp.newFolder("xuser-1", "Desktop"), ten = tmp.newFolder("xuser-2", "Desktop");
        File[] desktops = {nine, ten};
        File game = new File(ten, "Village Rhapsody.desktop");
        assertTrue(game.createNewFile());
        assertEquals("on Proton 10's desktop", 1, AgvnStartContainer.owner(desktops, game));

        // moved to Proton 10: the shortcut still names the file on Proton 9's desktop, which is gone
        assertEquals(1, AgvnStartContainer.owner(desktops, new File(nine, "Village Rhapsody.desktop")));
        assertEquals("gone everywhere: as it was named", -1,
                AgvnStartContainer.owner(desktops, new File(nine, "Other Game.desktop")));

        File temp = new File(tmp.getRoot(), "files/temp.desktop"); // a file manager's start
        assertEquals(-1, AgvnStartContainer.owner(desktops, temp));
        assertEquals("a gone file of no desktop is not looked for", -1,
                AgvnStartContainer.owner(desktops, new File(tmp.getRoot(), "files/Village Rhapsody.desktop")));
        File both = new File(nine, "Same.desktop");
        assertTrue(both.createNewFile());
        assertTrue(new File(ten, "Same.desktop").createNewFile());
        assertEquals("a file that is there stays where it is", 0, AgvnStartContainer.owner(desktops, both));
    }
}
