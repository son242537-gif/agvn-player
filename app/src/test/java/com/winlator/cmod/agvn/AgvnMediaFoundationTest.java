/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.winlator.cmod.SettingsFragment;
import com.winlator.cmod.core.EnvVars;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class AgvnMediaFoundationTest {
    private static final String QUIET = "-all,err+module,err+mscoree,err+system,err+sync,trace+msgbox";
    private static final String NO_MANAGER = AgvnMediaFoundation.NO_DXGI_MANAGER;

    @Test
    public void everyGameGetsVideoFramesInNormalMemory() {
        EnvVars env = new EnvVars("WINEESYNC=1 DXVK_HUD=fps");
        AgvnMediaFoundation.apply(env);
        assertEquals("1", env.get(NO_MANAGER));
        assertEquals("fps", env.get("DXVK_HUD"));
    }

    @Test
    public void zeroTurnsTheSwitchOffInsteadOfOn() {
        // Wine turns it on for any non-empty value, so an unticked checkbox ("0") must not reach Wine
        for (String off : new String[]{"0", "false", " 0 ", ""}) {
            EnvVars env = new EnvVars();
            env.put(NO_MANAGER, off);
            AgvnMediaFoundation.apply(env);
            assertFalse("'" + off + "'", env.has(NO_MANAGER));
        }
        EnvVars ticked = new EnvVars(NO_MANAGER + "=true");
        AgvnMediaFoundation.apply(ticked);
        assertEquals("1", ticked.get(NO_MANAGER));
    }

    @Test
    public void oneGameCanTurnItOffAgainstItsContainer() {
        EnvVars env = new EnvVars();
        env.putAll(new EnvVars(NO_MANAGER + "=1")); // the container
        env.putAll(new EnvVars(NO_MANAGER + "=0")); // the game, merged after it
        AgvnMediaFoundation.apply(env);
        assertFalse(env.has(NO_MANAGER));
    }

    @Test
    public void theMisspelledMediaSourceVariableReachesWine() throws Exception {
        EnvVars saved = new EnvVars("WINE_NEW_MEDIASOURCE=0");
        AgvnMediaFoundation.apply(saved);
        assertEquals("0", saved.get("WINE_NEW_MEDIA_SOURCE"));
        assertFalse(saved.has("WINE_NEW_MEDIASOURCE"));

        EnvVars both = new EnvVars("WINE_NEW_MEDIASOURCE=0 WINE_NEW_MEDIA_SOURCE=1");
        AgvnMediaFoundation.apply(both);
        assertEquals("the correct name wins", "1", both.get("WINE_NEW_MEDIA_SOURCE"));
        assertFalse(both.has("WINE_NEW_MEDIASOURCE"));

        String[] lists = {"src/main/java/com/winlator/cmod/widget/EnvVarsView.java",
                "src/main/java/com/winlator/cmod/ui/settings/EnvironmentVariablesEditor.kt",
                "src/main/java/com/winlator/cmod/ui/container/ContainerAdvancedComposeDialog.kt"};
        for (String path : lists) {
            String source = new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
            assertTrue(path, source.contains("\"WINE_NEW_MEDIA_SOURCE\""));
            assertFalse(path, source.contains("\"WINE_NEW_MEDIASOURCE\""));
        }
    }

    @Test
    public void theOldMovieReaderOutlivesAWholeFixThatPredatesIt() {
        // Tenants of the Dead (27/10/2026): picked for its movies, then "Trả cấu hình chạy tốt" took it away again
        String picked = "DXVK_ASYNC=1 WINE_OLD_MEDIA_SOURCE=1";
        assertEquals("DXVK_ASYNC=0 WINE_OLD_MEDIA_SOURCE=1",
                AgvnMediaFoundation.keepOldSource(picked, "DXVK_ASYNC=0"));
        assertEquals("WINE_OLD_MEDIA_SOURCE=1", AgvnMediaFoundation.keepOldSource(picked, null));
        assertEquals("not picked: the fix puts back what it has", "DXVK_ASYNC=0",
                AgvnMediaFoundation.keepOldSource("DXVK_ASYNC=1", "DXVK_ASYNC=0"));
        assertEquals("turned off on purpose", "A=1", AgvnMediaFoundation.keepOldSource("WINE_OLD_MEDIA_SOURCE=0", "A=1"));
        assertEquals("already there", "WINE_OLD_MEDIA_SOURCE=1",
                AgvnMediaFoundation.keepOldSource(picked, "WINE_OLD_MEDIA_SOURCE=1"));

        java.util.Map<String, String> good = new java.util.LinkedHashMap<>();
        good.put("screenSize", "960x544");
        java.util.Map<String, String> now = new java.util.LinkedHashMap<>(good);
        now.put("envVars", "WINE_OLD_MEDIA_SOURCE=1");
        java.util.Map<String, String> back = AgvnGoodConfig.restored(good, now);
        assertEquals("WINE_OLD_MEDIA_SOURCE=1", back.get("envVars"));
        assertTrue("only the movie reader differs: nothing to put back", AgvnGoodConfig.changed(back, now).isEmpty());
        now.put("screenSize", "854x480");
        assertEquals(java.util.Collections.singletonList("screenSize"),
                AgvnGoodConfig.changed(AgvnGoodConfig.restored(good, now), now));
        now.put("envVars", "");
        assertFalse(AgvnGoodConfig.restored(good, now).containsKey("envVars"));
    }

    @Test
    public void wineDebugClassesCoverEveryChannel() {
        // the default pick must log warnings, e.g. "Transform failed to process output"
        assertEquals("warn+all,err+all,fixme+all,warn-heap,warn-file,warn-font,trace+msgbox",
                AgvnWineDebug.spec(true, SettingsFragment.DEFAULT_WINE_DEBUG_CHANNELS));
        assertEquals("warn+all,+mfplat,+d3d11,warn-heap,warn-file,warn-font,trace+msgbox", AgvnWineDebug.spec(true, "warn, mfplat,d3d11"));
        assertEquals(QUIET, AgvnWineDebug.spec(false, "warn,err,fixme"));
        assertEquals("-all,err+module,err+mscoree,err+system,err+sync,trace+msgbox", AgvnWineDebug.spec(true, ""));
        assertEquals("-all,err+module,err+mscoree,err+system,err+sync,trace+msgbox", AgvnWineDebug.spec(true, " , "));
        assertEquals("-all,err+module,err+mscoree,err+system,err+sync,trace+msgbox", AgvnWineDebug.spec(true, null));
    }
}
