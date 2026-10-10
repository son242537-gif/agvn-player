package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
        assertEquals(DeviceTier.YEU, rules.classify("Adreno (TM) 650", "SM8250", 11000));
        assertEquals(DeviceTier.YEU, rules.classify("Adreno (TM) 619", "SM6375", 7500));
        assertEquals(DeviceTier.YEU, rules.classify("Mali-G57", "MT6833", 7500));
        assertEquals(DeviceTier.TRUNG_BINH, rules.classify("Mali-G715", "MT6985", 11000));
    }

    /** Adreno numbers are not generations: 725-750 are the 8 Gen 1-3 class, 810 is mid-range, 70x entry level. */
    @Test
    public void adrenoByClassNotByFirstDigit() {
        for (String flagship : new String[]{"725", "730", "732", "735", "740", "750", "825", "830", "840"})
            assertEquals(flagship, DeviceTier.FLAGSHIP, rules.classify("Adreno (TM) " + flagship, "", 11000));
        for (String mid : new String[]{"710", "720", "722", "810"})
            assertEquals(mid, DeviceTier.TRUNG_BINH, rules.classify("Adreno (TM) " + mid, "", 11000));
        for (String weak : new String[]{"702", "506", "610", "644", "660"})
            assertEquals(weak, DeviceTier.YEU, rules.classify("Adreno (TM) " + weak, "", 11000));
        // a letter after the number (778G's 642L) no longer hides the GPU and leaves the tier to the SoC
        assertEquals(DeviceTier.YEU, rules.classify("Adreno (TM) 642L", "SM7325", 7500));
        assertEquals(DeviceTier.FLAGSHIP, rules.classify("Turnip Adreno (TM) 750", "", 11000));
    }

    @Test
    public void otherGpus() {
        assertEquals(DeviceTier.TRUNG_BINH, rules.classify("Mali-G610 MC6", "MT6895", 11000)); // Dimensity 8100
        assertEquals(DeviceTier.TRUNG_BINH, rules.classify("Mali-G615 MC6", "MT6897", 11000)); // Dimensity 8300
        assertEquals(DeviceTier.YEU, rules.classify("Mali-G610 MC4", "MT6886", 11000)); // Dimensity 7200
        assertEquals(DeviceTier.YEU, rules.classify("Mali-G615 MC2", "MT6878", 7500)); // Dimensity 7300
        assertEquals(DeviceTier.YEU, rules.classify("Mali-G68 MC4", "MT6877", 7500));
        assertEquals(DeviceTier.YEU, rules.classify("Mali-G78 MP20", "GS101", 11000)); // Tensor G1
        assertEquals(DeviceTier.TRUNG_BINH, rules.classify("Mali-G710 MC10", "MT6983", 11000));
        assertEquals(DeviceTier.TRUNG_BINH, rules.classify("Immortalis-G925 MC12", "MT6991", 11000));
        assertEquals(DeviceTier.TRUNG_BINH, rules.classify("Samsung Xclipse 940", "s5e9945", 11000));
        assertEquals(DeviceTier.TRUNG_BINH, rules.classify("Maleoon 910", "", 11000));
        assertEquals(DeviceTier.TRUNG_BINH, rules.classify("PowerVR D-Series DXT-48-1536", "", 11000)); // Tensor G5
        assertEquals(DeviceTier.YEU, rules.classify("PowerVR B-Series BXM-8-256", "MT6855", 7500));
        assertEquals(DeviceTier.YEU, rules.classify("PowerVR Rogue GE8320", "MT6765", 3700));
    }

    @Test
    public void fallsBackToSocWhenGpuUnknown() {
        assertEquals(DeviceTier.FLAGSHIP, rules.classify("", "SM8650", 11000));
        assertEquals(DeviceTier.FLAGSHIP, rules.classify("Unknown", "SM8450", 11000));
        assertEquals(DeviceTier.YEU, rules.classify(null, "SM8250", 11000)); // 865/870: Adreno 650
        assertEquals(DeviceTier.TRUNG_BINH, rules.classify(null, "SM6450", 11000)); // 6 Gen 1: Adreno 710
        assertEquals(DeviceTier.TRUNG_BINH, rules.classify(null, "SM7550", 11000));
        assertEquals(DeviceTier.YEU, rules.classify(null, "SM7325", 11000));
        assertEquals(DeviceTier.YEU, rules.classify(null, "SM6375", 11000));
        assertEquals(DeviceTier.TRUNG_BINH, rules.classify(null, "MT6991", 11000)); // Dimensity 9400
        assertEquals(DeviceTier.TRUNG_BINH, rules.classify(null, "MT6897", 11000)); // Dimensity 8300
        assertEquals(DeviceTier.YEU, rules.classify(null, "MT6789", 11000)); // Helio G99
        assertEquals(DeviceTier.TRUNG_BINH, rules.classify(null, "s5e9925", 11000)); // Exynos 2200
        assertEquals(DeviceTier.YEU, rules.classify(null, "s5e8835", 11000)); // Exynos 1380
        assertEquals(DeviceTier.YEU, rules.classify(null, "T606", 11000)); // Unisoc
        assertEquals(DeviceTier.TRUNG_BINH, rules.classify(null, "unknown-chip", 11000));
    }

    @Test
    public void explainsTheTier() {
        String why = rules.why("Adreno (TM) 830", "SM8750", 5600);
        assertEquals(DeviceTier.TRUNG_BINH, rules.classify("Adreno (TM) 830", "SM8750", 5600));
        assertTrue(why, why.startsWith("GPU \"Adreno (TM) 830\" → FLAGSHIP"));
        assertTrue(why, why.contains("RAM 5600 MB → TRUNG_BINH"));
        assertTrue(why, why.endsWith("= TRUNG_BINH"));
        String bySoc = rules.why("Unknown", "SM6375", 7500);
        assertTrue(bySoc, bySoc.contains("GPU \"Unknown\" không khớp; chip \"SM6375\" → YEU"));
        assertTrue(rules.why(null, null, 7500).contains("không rõ → TRUNG_BINH"));
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
        assertEquals(30, e.fps);
        assertEquals(768, e.texturePool);
    }
}
