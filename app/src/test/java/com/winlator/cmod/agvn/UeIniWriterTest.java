package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UeIniWriterTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static Map<String, Map<String, String>> pool(int mb) throws Exception {
        return UeIniWriter.overrides(AgvnProfile.parse("{\"schemaVersion\":1,\"name\":\"g\"}"), mb);
    }

    @Test
    public void appendsSectionToEmptyFile() throws Exception {
        assertEquals("[SystemSettings]\nr.Streaming.PoolSize=512\n", UeIniWriter.merge("", pool(512)));
    }

    @Test
    public void keepsExistingKeysAndUpdatesInPlace() throws Exception {
        String old = "[Core.System]\nPaths=../../../Engine/Content\n\n[SystemSettings]\nr.VSync=0\nr.Streaming.PoolSize=2000\n";
        String merged = UeIniWriter.merge(old, pool(512));
        assertEquals("[Core.System]\nPaths=../../../Engine/Content\n\n[SystemSettings]\nr.VSync=0\nr.Streaming.PoolSize=512\n", merged);
    }

    @Test
    public void addsMissingKeyInsideExistingSection() throws Exception {
        String old = "[SystemSettings]\nr.VSync=0\n\n[Other]\na=b\n";
        assertEquals("[SystemSettings]\nr.VSync=0\nr.Streaming.PoolSize=768\n\n[Other]\na=b\n", UeIniWriter.merge(old, pool(768)));
    }

    @Test
    public void mergeIsIdempotent() throws Exception {
        String once = UeIniWriter.merge("[A]\nx=1\n", pool(512));
        assertEquals(once, UeIniWriter.merge(once, pool(512)));
    }

    @Test
    public void profileOverridesAreIncluded() throws Exception {
        AgvnProfile p = AgvnProfile.parse("{\"schemaVersion\":1,\"name\":\"g\",\"ueEngineIni\":{\"/Script/Engine.RendererSettings\":{\"r.DefaultFeature.MotionBlur\":\"False\"}}}");
        Map<String, Map<String, String>> o = UeIniWriter.overrides(p, 0);
        assertEquals(Collections.singletonMap("r.DefaultFeature.MotionBlur", "False"), o.get("/Script/Engine.RendererSettings"));
        assertEquals(1, o.size());
    }

    @Test
    public void projectNameFromExePath() {
        File dir = new File("/sdcard/AGVN/AVDirector");
        assertEquals("AVDirector", UeIniWriter.projectName(dir, "AVDirector/Binaries/Win64/AVDirector-Win64-Shipping.exe"));
        assertEquals("AVDirector", UeIniWriter.projectName(dir, "Game.exe"));
    }

    @Test
    public void writesBothPlatformConfigs() throws Exception {
        File user = tmp.newFolder("xuser");
        Map<String, Map<String, String>> o = new LinkedHashMap<>(pool(512));
        List<File> files = UeIniWriter.apply(user, "MyGame", o);
        assertEquals(2, files.size());
        for (File f : files) {
            assertTrue(f.getPath().contains("AppData/Local/MyGame/Saved/Config/"));
            assertTrue(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).contains("r.Streaming.PoolSize=512"));
        }
        assertTrue(UeIniWriter.apply(user, "MyGame", new LinkedHashMap<>()).isEmpty());
    }
}
