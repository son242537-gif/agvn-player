/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AgvnSettingHelpTest {
    private static final File ASSET = new File("src/main/assets/" + AgvnSettingHelp.ASSET);
    /** Labels the screens build at run time ("Phiên bản $type") or rename (settingFieldLabel). */
    private static final Set<String> COMPUTED = new HashSet<>(Arrays.asList(
            "Driver OpenGL", "Driver Vulkan", "Phiên bản Box64", "Phiên bản WOWBox64"));

    @Test
    public void parsesTheThreeParts() {
        Map<String, AgvnSettingHelp.Entry> map = AgvnSettingHelp.parse(Arrays.asList(
                "# comment", "[A]", "Là gì: what", "Vì sao chỉnh: why", "Chỉnh thế nào: how", "",
                "[Missing]", "Là gì: only one part"));
        assertEquals(1, map.size());
        assertEquals("why", map.get("A").why);
        assertNull(map.get("Missing"));
    }

    @Test
    public void everyEntryIsCompleteAndShort() throws IOException {
        List<String> lines = Files.readAllLines(ASSET.toPath(), StandardCharsets.UTF_8);
        long labels = lines.stream().filter(l -> l.trim().startsWith("[")).count();
        Map<String, AgvnSettingHelp.Entry> map = AgvnSettingHelp.parse(lines);
        assertEquals("every [label] needs Là gì / Vì sao chỉnh / Chỉnh thế nào", labels, map.size());
        assertTrue(map.size() >= 100);
        for (Map.Entry<String, AgvnSettingHelp.Entry> e : map.entrySet()) {
            for (String part : new String[]{e.getValue().what, e.getValue().why, e.getValue().how})
                assertTrue(e.getKey() + " is too long for the dialog", part.length() <= 300);
        }
    }

    @Test
    public void everyLabelIsOnAScreen() throws IOException {
        StringBuilder code = new StringBuilder();
        try (Stream<java.nio.file.Path> files = Files.walk(new File("src/main/java").toPath())) {
            for (java.nio.file.Path p : files.filter(f -> f.toString().endsWith(".kt") || f.toString().endsWith(".java"))
                    .collect(Collectors.toList())) code.append(new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
        }
        for (File values : new File[]{new File("src/main/res/values/strings.xml"), new File("src/main/res/values/agvn_strings.xml")})
            code.append(new String(Files.readAllBytes(values.toPath()), StandardCharsets.UTF_8).replace("\\'", "'"));
        String all = code.toString();
        for (String label : AgvnSettingHelp.parse(Files.readAllLines(ASSET.toPath(), StandardCharsets.UTF_8)).keySet()) {
            boolean shown = COMPUTED.contains(label) || all.contains("\"" + label + "\"") || all.contains(">" + label + "<");
            assertTrue("no screen shows \"" + label + "\"", shown);
        }
    }
}
