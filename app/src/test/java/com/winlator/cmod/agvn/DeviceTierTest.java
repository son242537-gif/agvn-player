package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class DeviceTierTest {
    private static DeviceTierRules rules;

    @BeforeClass
    public static void load() throws Exception {
        rules = DeviceTierRules.parse(new String(Files.readAllBytes(
                Paths.get("src/main/assets/agvn/device-tiers.json")), StandardCharsets.UTF_8));
    }

    @Test
    public void classifiesByGpu() {
        assertEquals(DeviceTier.FLAGSHIP, rules.classify("Adreno (TM) 830", "SM8750", 11000));
        assertEquals(DeviceTier.TRUNG_BINH, rules.classify("Adreno (TM) 730", "SM8450", 11000));
        assertEquals(DeviceTier.YEU, rules.classify("Adreno (TM) 650", "SM8250", 11000));
        assertEquals(DeviceTier.YEU, rules.classify("Adreno (TM) 619", "SM6375", 7500));
        assertEquals(DeviceTier.YEU, rules.classify("Mali-G57", "MT6833", 7500));
        assertEquals(DeviceTier.TRUNG_BINH, rules.classify("Mali-G715", "MT6985", 11000));
    }

    @Test
    public void fallsBackToSocWhenGpuUnknown() {
        assertEquals(DeviceTier.FLAGSHIP, rules.classify("", "SM8650", 11000));
        assertEquals(DeviceTier.YEU, rules.classify(null, "SM6450", 11000));
        assertEquals(DeviceTier.TRUNG_BINH, rules.classify(null, "unknown-chip", 11000));
    }

    @Test
    public void ramCapsTheTier() {
        assertEquals(DeviceTier.TRUNG_BINH, rules.classify("Adreno (TM) 830", "SM8750", 5600));
        assertEquals(DeviceTier.YEU, rules.classify("Adreno (TM) 830", "SM8750", 3700));
    }

    @Test
    public void presetsMatchTable() {
        assertEquals(24, rules.preset(DeviceTier.YEU).fps);
        assertEquals("854x480", rules.preset(DeviceTier.YEU).resolution);
        assertEquals(512, rules.preset(DeviceTier.YEU).texturePool);
        assertEquals(30, rules.preset(DeviceTier.FLAGSHIP).fps);
        assertEquals(768, DeviceTierRules.parse("{}").preset(DeviceTier.TRUNG_BINH).texturePool);
    }

    @Test
    public void weakTierCapsProfile() throws Exception {
        AgvnProfile p = AgvnProfile.parse("{\"schemaVersion\":1,\"name\":\"g\",\"resolution\":\"1280x720\",\"fpsLimit\":30,\"texturePool\":1024}");
        LaunchPresetResolver.Effective weak = LaunchPresetResolver.resolve(p, DeviceTier.YEU, rules.preset(DeviceTier.YEU));
        assertEquals("854x480", weak.resolution);
        assertEquals(24, weak.fps);
        assertEquals(512, weak.texturePool);

        LaunchPresetResolver.Effective strong = LaunchPresetResolver.resolve(p, DeviceTier.FLAGSHIP, rules.preset(DeviceTier.FLAGSHIP));
        assertEquals("1280x720", strong.resolution);
        assertEquals(30, strong.fps);
        assertEquals(1024, strong.texturePool);
    }

    @Test
    public void weakDeviceBlockWinsOnWeakTier() throws Exception {
        AgvnProfile p = AgvnProfile.parse("{\"schemaVersion\":1,\"name\":\"g\",\"fpsLimit\":30,"
                + "\"weakDevice\":{\"resolution\":\"960x544\",\"fpsLimit\":20}}");
        LaunchPresetResolver.Effective e = LaunchPresetResolver.resolve(p, DeviceTier.YEU, rules.preset(DeviceTier.YEU));
        assertEquals("960x544", e.resolution);
        assertEquals(20, e.fps);
        assertEquals(512, e.texturePool);
    }

    @Test
    public void missingProfileValuesUseTierPreset() throws Exception {
        AgvnProfile p = AgvnProfile.parse("{\"schemaVersion\":1,\"name\":\"g\"}");
        LaunchPresetResolver.Effective e = LaunchPresetResolver.resolve(p, DeviceTier.TRUNG_BINH, rules.preset(DeviceTier.TRUNG_BINH));
        assertEquals("960x544", e.resolution);
        assertEquals(27, e.fps);
        assertEquals(768, e.texturePool);
    }
}
