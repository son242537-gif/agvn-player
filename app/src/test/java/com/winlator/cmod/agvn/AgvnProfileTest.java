package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

public class AgvnProfileTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File ueGame() throws IOException {
        File dir = tmp.newFolder("MyGame");
        touch(new File(dir, "MyGame.exe"));
        touch(new File(dir, "MyGame/Binaries/Win64/MyGame-Win64-Shipping.exe"));
        return dir;
    }

    private static void touch(File f) throws IOException {
        f.getParentFile().mkdirs();
        assertTrue(f.createNewFile());
    }

    private static AgvnProfile profile(String extra) throws AgvnProfileException {
        return AgvnProfile.parse("{\"schemaVersion\":1,\"name\":\"My Game\"" + extra + "}");
    }

    private static void assertRejected(AgvnProfile p, File dir) {
        try {
            AgvnProfileValidator.validate(p, dir);
            fail("expected AgvnProfileException");
        } catch (AgvnProfileException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }

    @Test
    public void validProfileResolvesShippingExe() throws Exception {
        File dir = ueGame();
        AgvnProfile p = profile(",\"resolution\":\"1280x720\",\"fpsLimit\":30,\"texturePool\":1024,\"args\":[\"-dx11\"]");
        assertEquals("MyGame/Binaries/Win64/MyGame-Win64-Shipping.exe", AgvnProfileValidator.validate(p, dir));
        assertTrue(p.isSimulatedTouchscreen());
    }

    @Test
    public void ueBootstrapExeIsSwitchedToShipping() throws Exception {
        File dir = ueGame();
        assertEquals("MyGame/Binaries/Win64/MyGame-Win64-Shipping.exe",
                AgvnProfileValidator.validate(profile(",\"exe\":\"MyGame.exe\""), dir));
    }

    @Test
    public void rejectsPathsOutsideGameFolder() throws Exception {
        File dir = ueGame();
        touch(new File(tmp.getRoot(), "evil.exe"));
        assertRejected(profile(",\"exe\":\"../evil.exe\""), dir);
        assertRejected(profile(",\"exe\":\"/sdcard/evil.exe\""), dir);
        assertRejected(profile(",\"exe\":\"C:\\\\evil.exe\""), dir);
        assertRejected(profile(",\"exe\":\"missing.exe\""), dir);
        assertRejected(profile(",\"exe\":\"MyGame/readme.txt\""), dir);
    }

    @Test
    public void rejectsShellMetacharacters() throws Exception {
        File dir = ueGame();
        assertRejected(profile(",\"args\":[\"$(rm -rf /)\"]"), dir);
        assertRejected(profile(",\"args\":[\"a;b\"]"), dir);
        assertRejected(profile(",\"args\":[\"a|b\"]"), dir);
        assertRejected(profile(",\"args\":[\"`id`\"]"), dir);
        assertRejected(profile(",\"env\":{\"A\":\"x && y\"}"), dir);
        assertRejected(profile(",\"env\":{\"A B\":\"1\"}"), dir);
    }

    @Test
    public void rejectsOutOfRangeValues() throws Exception {
        File dir = ueGame();
        assertRejected(profile(",\"resolution\":\"1280*720\""), dir);
        assertRejected(profile(",\"resolution\":\"320x240\""), dir);
        assertRejected(profile(",\"fpsLimit\":5"), dir);
        assertRejected(profile(",\"texturePool\":8192"), dir);
        assertRejected(profile(",\"weakDevice\":{\"fpsLimit\":200}"), dir);
        assertRejected(profile(",\"ueEngineIni\":{\"[Evil]\":{\"a\":\"b\"}}"), dir);
        assertRejected(AgvnProfile.parse("{\"schemaVersion\":2,\"name\":\"x\"}"), dir);
        assertRejected(AgvnProfile.parse("{\"schemaVersion\":1,\"name\":\"a/b\"}"), dir);
    }

    /** Same example file is validated by tools/agvn/tests/test_toolkit.py (Python toolkit). */
    @Test
    public void sharedExampleProfileIsValid() throws Exception {
        String json = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("../docs/agvn/agvn-profile.example.json")), java.nio.charset.StandardCharsets.UTF_8);
        AgvnProfile p = AgvnProfile.parse(json);
        File dir = tmp.newFolder("AVDirector");
        touch(new File(dir, p.exe));
        assertEquals(p.exe, AgvnProfileValidator.validate(p, dir));
    }

    @Test
    public void rejectsWineInFolderName() throws Exception {
        File dir = tmp.newFolder("Red wine 2");
        touch(new File(dir, "game.exe"));
        assertRejected(profile(""), dir);
    }

    @Test
    public void validatesDllOverrides() throws Exception {
        File dir = ueGame();
        AgvnProfileValidator.validate(profile(",\"dllOverrides\":{\"dinput8\":\"n,b\"}"), dir);
        assertRejected(profile(",\"dllOverrides\":{\"dinput8\":\"n;rm\"}"), dir);
        assertRejected(profile(",\"dllOverrides\":{\"a b\":\"n\"}"), dir);
    }

    @Test(expected = AgvnProfileException.class)
    public void rejectsBrokenJson() throws Exception {
        AgvnProfile.parse("{not json");
    }

    @Test
    public void envVarsIncludeFpsCap() {
        assertEquals("DXVK_HUD=0 DXVK_FRAME_RATE=24",
                AgvnGameImporter.buildEnvVars(Collections.singletonMap("DXVK_HUD", "0"), 24, ""));
        assertEquals("", AgvnGameImporter.buildEnvVars(Collections.emptyMap(), 0, null));
        assertEquals("WINEDLLOVERRIDES=dinput8=n,b DXVK_FRAME_RATE=30",
                AgvnGameImporter.buildEnvVars(Collections.emptyMap(), 30, "dinput8=n,b"));
        assertEquals("WINEDLLOVERRIDES=mscoree=b;winhttp=n,b",
                AgvnGameImporter.buildEnvVars(Collections.singletonMap("WINEDLLOVERRIDES", "mscoree=b"), 0, "winhttp=n,b"));
    }
}
