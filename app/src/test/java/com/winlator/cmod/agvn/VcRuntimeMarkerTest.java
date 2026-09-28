/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.winlator.cmod.core.WineRegistryEditor;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VcRuntimeMarkerTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final String HEADER = "WINE REGISTRY Version 2\n;; All keys relative to REGISTRY\\\\Machine\n\n#arch=win64\n";
    private static final String X64 = "Software\\Microsoft\\VisualStudio\\14.0\\VC\\Runtimes\\x64";

    private File reg(String body) throws Exception {
        File f = tmp.newFile("system.reg");
        Files.write(f.toPath(), (HEADER + body).getBytes(StandardCharsets.UTF_8));
        return f;
    }

    private static Map<String, String> runtime(String installed, String major, String minor, String bld) {
        Map<String, String> v = new HashMap<>();
        v.put("Installed", installed);
        v.put("Major", major);
        v.put("Minor", minor);
        v.put("Bld", bld);
        v.put("Version", "\"v14.x\"");
        return v;
    }

    @Test
    public void runtimeVersionComparison() {
        assertTrue(VcRuntimeMarker.isCurrentRuntime(runtime("dword:00000001", "dword:0000000e", "dword:0000002c", "dword:0000898b")));
        assertTrue(VcRuntimeMarker.isCurrentRuntime(runtime("dword:00000001", "dword:0000000e", "dword:00000032", "dword:00000001")));
        assertFalse(VcRuntimeMarker.isCurrentRuntime(runtime("dword:00000001", "dword:0000000e", "dword:00000028", "dword:0000898b")));
        assertFalse(VcRuntimeMarker.isCurrentRuntime(runtime("dword:00000000", "dword:0000000e", "dword:0000002c", "dword:0000898b")));
        assertFalse(VcRuntimeMarker.isCurrentRuntime(runtime("\"1\"", "dword:0000000e", "dword:0000002c", "dword:0000898b")));
        assertFalse(VcRuntimeMarker.isCurrentRuntime(null));
        assertEquals(35211, VcRuntimeMarker.dword("dword:0000898b"));
        assertArrayEquals(new long[]{14, 44, 35211, 0}, VcRuntimeMarker.parseVersion("v14.44.35211.00"));
        assertEquals(0, VcRuntimeMarker.compare(VcRuntimeMarker.parseVersion("14.44.35211"), VcRuntimeMarker.parseVersion("v14.44.35211.00")));
        assertEquals(-1, VcRuntimeMarker.compare(VcRuntimeMarker.parseVersion("junk"), VcRuntimeMarker.parseVersion("14.0")));
    }

    @Test
    public void readsExistingMarkersInOnePass() throws Exception {
        File f = reg("\n[Software\\\\Microsoft\\\\VisualStudio\\\\14.0\\\\VC\\\\Runtimes\\\\x64] 1746463204\n#time=1dbbddc5a299bf0\n"
                + "\"Bld\"=dword:0000898b\n\"Installed\"=dword:00000001\n\"Major\"=dword:0000000e\n\"Minor\"=dword:0000002c\n"
                + "\"Version\"=\"v14.44.35211.00\"\n\n[Software\\\\Microsoft\\\\VisualStudio\\\\14.0\\\\VC\\\\Runtimes\\\\x64\\\\Sub] 1\n"
                + "\"Installed\"=dword:00000000\n");
        Map<String, Map<String, String>> values = VcRuntimeMarker.readValues(f);
        assertEquals("dword:00000001", values.get(X64).get("Installed"));
        assertEquals("\"v14.44.35211.00\"", values.get(X64).get("Version"));
        List<String> stale = VcRuntimeMarker.staleKeys(values);
        assertFalse(stale.contains(X64));
        assertEquals(5, stale.size());
    }

    @Test
    public void applyWritesMissingMarkersOnce() throws Exception {
        // WineRegistryEditor renames over existing files, which java.io.File cannot do on Windows.
        Assume.assumeTrue("rename-over needs a POSIX file system", File.separatorChar == '/');
        File f = reg("\n[Software\\\\Microsoft] 1746463204\n#time=1dbbddc5a299bf0\n\n"
                + "[Software\\\\Wow6432Node\\\\Microsoft] 1746463204\n#time=1dbbddc5a299bf0\n");
        VcRuntimeMarker.apply(f);
        assertTrue(VcRuntimeMarker.staleKeys(VcRuntimeMarker.readValues(f)).isEmpty());
        try (WineRegistryEditor editor = new WineRegistryEditor(f)) {
            for (String key : VcRuntimeMarker.RUNTIME_KEYS) {
                assertEquals(Integer.valueOf(1), editor.getDwordValue(key, "Installed"));
                assertEquals(Integer.valueOf(35211), editor.getDwordValue(key, "Bld"));
                assertEquals("v14.44.35211.00", editor.getStringValue(key, "Version"));
            }
            assertEquals("14.44.35211", editor.getStringValue(VcRuntimeMarker.SERVICING_KEYS[0], "Version"));
        }
        byte[] once = Files.readAllBytes(f.toPath());
        VcRuntimeMarker.apply(f);
        assertTrue(Arrays.equals(once, Files.readAllBytes(f.toPath())));
        assertEquals(1, tmp.getRoot().list().length);
    }

    @Test
    public void missingFileIsIgnored() {
        VcRuntimeMarker.apply(new File(tmp.getRoot(), "none.reg"));
        VcRuntimeMarker.apply(null);
        assertEquals(0, tmp.getRoot().list().length);
    }
}
