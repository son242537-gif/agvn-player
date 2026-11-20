/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/** Where the file of another app's address is, as far as the address tells. */
public class AgvnUriPathTest {
    private static final String ROOT = "/storage/emulated/0";

    @Test
    public void aStorageDocumentIdNamesThePath() {
        assertEquals(ROOT + "/Download/Game v1.2.zip", AgvnUriPath.fromDocumentId(AgvnUriPath.EXTERNAL_STORAGE, "primary:Download/Game v1.2.zip", ROOT));
        assertEquals("/storage/1A2B-3C4D/Games/a.rar", AgvnUriPath.fromDocumentId(AgvnUriPath.EXTERNAL_STORAGE, "1A2B-3C4D:Games/a.rar", ROOT));
        assertEquals(ROOT + "/Documents/a.7z", AgvnUriPath.fromDocumentId(AgvnUriPath.EXTERNAL_STORAGE, "home:a.7z", ROOT));
        assertEquals(ROOT + "/Download/a.zip", AgvnUriPath.fromDocumentId(AgvnUriPath.DOWNLOADS, "raw:" + ROOT + "/Download/a.zip", ROOT));
        assertNull("a number says nothing of the place", AgvnUriPath.fromDocumentId(AgvnUriPath.DOWNLOADS, "msf:1234", ROOT));
        assertNull(AgvnUriPath.fromDocumentId(AgvnUriPath.DOWNLOADS, "1234", ROOT));
        assertNull("the storage root is no file", AgvnUriPath.fromDocumentId(AgvnUriPath.EXTERNAL_STORAGE, "primary:", ROOT));
        assertNull("another app's ids are not read as paths", AgvnUriPath.fromDocumentId("com.example.files", "primary:Download/a.zip", ROOT));
        assertNull("an unknown volume", AgvnUriPath.fromDocumentId(AgvnUriPath.EXTERNAL_STORAGE, "weird:Download/a.zip", ROOT));
        assertNull("never out of the storage", AgvnUriPath.fromDocumentId(AgvnUriPath.EXTERNAL_STORAGE, "primary:../../data/a.zip", ROOT));
        assertNull(AgvnUriPath.fromDocumentId(AgvnUriPath.DOWNLOADS, "raw:/storage/emulated/0/../../data/a.zip", ROOT));
        assertNull(AgvnUriPath.fromDocumentId(null, null, ROOT));
    }

    @Test
    public void aMediaNumberIsRead() {
        assertEquals(1234, AgvnUriPath.mediaId("msf:1234"));
        assertEquals(77, AgvnUriPath.mediaId("document:77"));
        assertEquals(5, AgvnUriPath.mediaId("5"));
        assertEquals(-1, AgvnUriPath.mediaId("raw:/storage/a.zip"));
        assertEquals(-1, AgvnUriPath.mediaId("primary:Download/a.zip"));
        assertEquals(-1, AgvnUriPath.mediaId("image:12"));
        assertEquals(-1, AgvnUriPath.mediaId("msf:"));
        assertEquals(-1, AgvnUriPath.mediaId("msf:12a"));
        assertEquals(-1, AgvnUriPath.mediaId(null));
    }

    @Test
    public void guessesComeFromTheAddressAndTheName() {
        assertEquals("a file manager that shares the whole path", Arrays.asList(ROOT + "/Download/a.zip", ROOT + "/storage/emulated/0/Download/a.zip"),
                AgvnUriPath.guesses("/root/storage/emulated/0/Download/a.zip", "a.zip", ROOT).subList(0, 2));
        assertEquals("a provider's name for internal storage, then the path below it",
                Arrays.asList(ROOT + "/Games/x/a.7z", ROOT + "/Download/a.7z"), AgvnUriPath.guesses("/external_files/Games/x/a.7z", "a.7z", ROOT));
        assertEquals("a browser's downloads", Arrays.asList(ROOT + "/Download/a.rar", ROOT + "/a.rar"),
                AgvnUriPath.guesses("/downloads/a.rar", "a.rar", ROOT));
        assertTrue("only a number: the name, in Download",
                AgvnUriPath.guesses("/all_downloads/4321", "Game.zip", ROOT).contains(ROOT + "/Download/Game.zip"));
        assertEquals(Arrays.asList(ROOT + "/DCIM/a.zip"), AgvnUriPath.guesses("/x/sdcard/DCIM/a.zip", null, ROOT).subList(0, 1));
        assertTrue("nothing to go by", AgvnUriPath.guesses("/123", null, ROOT).isEmpty());
        assertTrue("never followed out of the storage", AgvnUriPath.guesses("/external/../../data/a.zip", "../a.zip", ROOT).isEmpty());
        assertTrue(AgvnUriPath.guesses(null, ".hidden", ROOT).isEmpty());
    }

    @Test
    public void aNameIsTakenFromAnAddressThatEndsInOne() {
        assertEquals("Game.zip", AgvnUriPath.nameIn("/document/primary:Download/Game.zip"));
        assertEquals("Game.zip", AgvnUriPath.nameIn("/document/primary:Game.zip"));
        assertNull(AgvnUriPath.nameIn("/all_downloads/4321"));
        assertNull(AgvnUriPath.nameIn(null));
        assertTrue(AgvnUriPath.climbs("a/../b"));
        assertTrue(AgvnUriPath.climbs(".."));
        assertFalse(AgvnUriPath.climbs("/Download/Game..v2/a.zip"));
    }
}
