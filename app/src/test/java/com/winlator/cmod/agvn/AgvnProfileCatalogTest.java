package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

public class AgvnProfileCatalogTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String JSON = "{\"schemaVersion\":1,\"entries\":["
            + "{\"id\":\"ue\",\"profile\":{\"name\":\"UE Game\",\"exe\":\"UE/Binaries/Win64/UE-Win64-Shipping.exe\",\"fpsLimit\":30}},"
            + "{\"id\":\"mv\",\"match\":[\"Game.exe\",\"www/data/Special.json\"],\"profile\":{\"fpsLimit\":24,\"locale\":\"ja_JP\"}}]}";

    private File game(String name, String... files) throws Exception {
        File dir = tmp.newFolder(name);
        for (String f : files) {
            File file = new File(dir, f);
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), new byte[0]);
        }
        return dir;
    }

    @Test
    public void matchesByExeOrByEveryListedFile() throws Exception {
        AgvnProfileCatalog c = AgvnProfileCatalog.parse(JSON);
        assertEquals(2, c.size());
        AgvnProfile ue = c.find(game("ue", "UE/Binaries/Win64/UE-Win64-Shipping.exe"));
        assertEquals("UE Game", ue.name);
        assertEquals(30, ue.getFpsLimit());
        assertEquals(1, ue.schemaVersion);

        File mvDir = game("Some MV Game", "Game.exe", "www/data/Special.json");
        AgvnProfile mv = c.find(mvDir);
        assertEquals(AgvnProfile.defaultFor(mvDir.getName()).name, mv.name); // no name in the entry: folder title
        assertEquals("ja_JP", mv.locale);
        assertNull(c.find(game("other-mv", "Game.exe"))); // Game.exe alone is not enough
    }

    @Test
    public void eachFindReturnsAFreshProfile() throws Exception {
        AgvnProfileCatalog c = AgvnProfileCatalog.parse(JSON);
        File dir = game("ue2", "UE/Binaries/Win64/UE-Win64-Shipping.exe");
        AgvnProfile a = c.find(dir);
        a.name = "changed";
        AgvnProfile b = c.find(dir);
        assertNotSame(a, b);
        assertEquals("UE Game", b.name);
    }

    @Test
    public void genericOrEscapingMatchesAreRejected() {
        assertFalse(AgvnProfileCatalog.isSpecific(Collections.singletonList("Game.exe")));
        assertFalse(AgvnProfileCatalog.isSpecific(Collections.singletonList("../evil.exe")));
        assertFalse(AgvnProfileCatalog.isSpecific(Collections.emptyList()));
        assertTrue(AgvnProfileCatalog.isSpecific(Arrays.asList("Game.exe", "www/data/Special.json")));
        try {
            AgvnProfileCatalog.parse("{\"schemaVersion\":1,\"entries\":[{\"id\":\"x\",\"profile\":{\"exe\":\"Game.exe\"}}]}");
            fail();
        } catch (AgvnProfileException expected) {
        }
    }

    @Test
    public void bundledCatalogParsesAndEveryEntryIsValid() throws Exception {
        String json = new String(Files.readAllBytes(new File("src/main/assets/" + AgvnProfileCatalog.ASSET).toPath()), StandardCharsets.UTF_8);
        AgvnProfileCatalog c = AgvnProfileCatalog.parse(json);
        assertTrue(c.size() > 0);
    }

    @Test
    public void folderWithOwnProfileIgnoresCatalogAndFolderWithoutUsesIt() throws Exception {
        File dir = game("UE", "UE/Binaries/Win64/UE-Win64-Shipping.exe");
        AgvnGameImporter.Candidate c = AgvnGameImporter.load(dir, AgvnProfileCatalog.parse(JSON));
        assertEquals("UE Game", c.profile.name);
        assertEquals("UE/Binaries/Win64/UE-Win64-Shipping.exe", c.exe);
        Files.write(new File(dir, AgvnProfile.FILE_NAME).toPath(),
                "{\"schemaVersion\":1,\"name\":\"Own\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals("Own", AgvnGameImporter.load(dir, AgvnProfileCatalog.parse(JSON)).profile.name);
    }
}
