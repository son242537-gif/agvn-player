/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.ui.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

/** "Chọn nhiều game": picking games of the library to remove them at once (26/10/2026). */
public class LibrarySelectionTest {
    @Test
    public void theGameWhoseMenuStartedItIsPicked() {
        LibrarySelection s = new LibrarySelection();
        assertFalse(s.isPicked("a.desktop"));
        s.start("a.desktop");
        assertTrue(s.getActive());
        assertTrue(s.isPicked("a.desktop"));
        s.start("a.desktop");
        assertEquals(1, s.getPicked().size());
    }

    @Test
    public void aTapPicksAndUnpicks() {
        LibrarySelection s = new LibrarySelection();
        s.start("a.desktop");
        s.toggle("b.desktop");
        s.toggle("a.desktop");
        assertEquals(Collections.singletonList("b.desktop"), s.getPicked());
        s.toggle("b.desktop");
        assertTrue("unpicking them all by hand keeps the bar", s.getActive());
        s.end();
        assertFalse(s.getActive());
        assertTrue(s.getPicked().isEmpty());
    }

    @Test
    public void itEndsOnceThePickedGamesAreGone() {
        LibrarySelection s = new LibrarySelection();
        s.start("a.desktop");
        s.toggle("b.desktop");
        s.keepOnly(new HashSet<>(Arrays.asList("a.desktop", "b.desktop", "c.desktop")));
        assertEquals(2, s.getPicked().size());
        s.keepOnly(new HashSet<>(Arrays.asList("b.desktop", "c.desktop")));
        assertEquals(Collections.singletonList("b.desktop"), s.getPicked());
        assertTrue(s.getActive());
        s.keepOnly(Collections.singleton("c.desktop"));
        assertFalse("removed: back to the library", s.getActive());
    }
}
