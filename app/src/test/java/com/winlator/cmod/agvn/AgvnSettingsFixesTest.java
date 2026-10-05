/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.core.WineRegistryEditor;
import com.winlator.cmod.core.WineUtils;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class AgvnSettingsFixesTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final int X = AgvnGamepadMode.XINPUT, D = AgvnGamepadMode.DINPUT;
    private static final String[] SERVICES = {"PlugPlay", "RpcSs", "winebus", "winehid", "MSIServer", "nsiproxy", "Spooler"};

    @Test
    public void gamepadIsOffOnlyWhenAsked() {
        assertFalse(AgvnGamepadMode.noGamepad(false, false, true, X));
        assertFalse(AgvnGamepadMode.noGamepad(false, false, true, D));
        assertFalse(AgvnGamepadMode.noGamepad(false, false, false, 0)); // without exclusive input both kinds are on
        assertTrue(AgvnGamepadMode.noGamepad(false, false, true, 0));
        assertTrue(AgvnGamepadMode.noGamepad(true, false, false, X | D));
        assertTrue(AgvnGamepadMode.noGamepad(false, true, false, X | D));
    }

    @Test
    public void xinputIsHiddenOnlyForDinputAlone() {
        assertTrue(AgvnGamepadMode.mapToXInput(true, X));
        assertTrue(AgvnGamepadMode.mapToXInput(false, D));
        assertTrue(AgvnGamepadMode.mapToXInput(false, 0));
        assertFalse(AgvnGamepadMode.mapToXInput(true, D));
    }

    @Test
    public void typedResolutionsAreCheckedBeforeLaunch() {
        assertEquals("1280x720", AgvnScreenSize.normalize("1280x720"));
        assertEquals("1280x720", AgvnScreenSize.normalize(" 1280 X 720 "));
        assertEquals("1024x576", AgvnScreenSize.normalize("1024*576"));
        assertEquals("1024x576", AgvnScreenSize.normalize("1024×576"));
        assertEquals("960x544", AgvnScreenSize.normalize("960x544 (16:9)"));
        assertNull(AgvnScreenSize.normalize("1280x"));
        assertNull(AgvnScreenSize.normalize("abc"));
        assertNull(AgvnScreenSize.normalize("99999x99999"));
        assertNull(AgvnScreenSize.normalize("100x100"));
        assertNull(AgvnScreenSize.normalize(null));
        assertEquals(Container.DEFAULT_SCREEN_SIZE, AgvnScreenSize.orDefault("1280X"));
    }

    @Test
    public void startupModesWriteDifferentServices() throws Exception {
        Assume.assumeTrue("rename-over needs a POSIX file system", File.separatorChar == '/');
        File reg = tmp.newFile("system.reg");
        StringBuilder body = new StringBuilder("WINE REGISTRY Version 2\n;; All keys relative to REGISTRY\\\\Machine\n\n#arch=win64\n");
        for (String name : SERVICES)
            body.append("\n[System\\\\CurrentControlSet\\\\Services\\\\").append(name).append("] 1700000000\n\"Start\"=dword:00000003\n");
        Files.write(reg.toPath(), body.toString().getBytes(StandardCharsets.UTF_8));

        WineUtils.changeServicesStatus(reg, "0"); // Bình thường: Wine's own start types
        assertEquals(2, start(reg, "PlugPlay"));
        assertEquals(3, start(reg, "RpcSs"));
        assertEquals(3, start(reg, "winebus"));

        WineUtils.changeServicesStatus(reg, "1"); // Thiết yếu: no Plug and Play or RPC
        assertEquals(4, start(reg, "PlugPlay"));
        assertEquals(4, start(reg, "RpcSs"));
        assertEquals(2, start(reg, "winebus"));

        WineUtils.changeServicesStatus(reg, "2"); // Tối giản turns off the installer service...
        assertEquals(4, start(reg, "MSIServer"));
        assertEquals(4, start(reg, "nsiproxy"));
        WineUtils.changeServicesStatus(reg, "1"); // ...and leaving it turns it back on
        assertEquals(3, start(reg, "MSIServer"));
        assertEquals(2, start(reg, "nsiproxy"));
        assertEquals(3, start(reg, "Spooler"));
    }

    private static int start(File reg, String service) {
        try (WineRegistryEditor editor = new WineRegistryEditor(reg)) {
            return editor.getDwordValue("System\\CurrentControlSet\\Services\\" + service, "Start", -1);
        }
    }
}
