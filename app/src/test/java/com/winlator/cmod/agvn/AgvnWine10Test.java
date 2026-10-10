/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Proton 10, the second Wine: what of its package is unpacked, and the name the app knows it by. */
public class AgvnWine10Test {
    @Test
    public void filesNothingLoadsAreLeftOut() {
        // Windows import libraries, copied into every container's system32 otherwise
        assertTrue(AgvnWine10.skip("lib/wine/i386-windows/libkernel32.a"));
        assertTrue(AgvnWine10.skip("./lib/wine/aarch64-windows/libmfplat.a"));
        // lib/ is not on the library path: its Wayland and Mesa libraries are for the package's Wayland driver
        assertTrue(AgvnWine10.skip("lib/libvulkan_freedreno_wayland_a8xx.so"));
        assertTrue(AgvnWine10.skip("lib/libgallium-26.3.0-devel.so"));
        assertTrue(AgvnWine10.skip("lib/libEGL.so.1"));
        assertTrue(AgvnWine10.skip("share/vulkan/icd.d/freedreno_icd.aarch64.json"));
        // Wine itself stays: its DLLs, its Unix libraries, its data, the prefix
        assertFalse(AgvnWine10.skip("lib/wine/i386-windows/mfplat.dll"));
        assertFalse(AgvnWine10.skip("lib/wine/aarch64-unix/winegstreamer.so"));
        assertFalse(AgvnWine10.skip("lib/wine/aarch64-unix/winex11.so"));
        assertFalse(AgvnWine10.skip("lib/wine/"));
        assertFalse(AgvnWine10.skip("bin/wine"));
        assertFalse(AgvnWine10.skip("share/wine/wine.inf"));
        assertFalse(AgvnWine10.skip("prefixPack.txz"));
        assertFalse(AgvnWine10.skip("profile.json"));
    }

    @Test
    public void theNameTheAppKnowsItBy() {
        // ContentsManager.getEntryName: type, versionName and versionCode of the package's profile.json
        assertEquals("Proton-10.0-4-arm64ec-9", AgvnWine10.IDENTIFIER);
        // WineInfo.fromIdentifier drops "-<versionCode>" (one digit) and reads proton-<version>-<arch>
        String wine = AgvnWine10.IDENTIFIER.substring(0, AgvnWine10.IDENTIFIER.length() - 2).toLowerCase(java.util.Locale.ROOT);
        assertTrue(wine.matches("^(wine|proton)-([0-9.]+)-?([0-9.]+)?-(x86|x86_64|arm64ec)$"));
    }
}
