/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.core.EnvVars;

/**
 * Video through Media Foundation: Unity's VideoPlayer, Unreal's WmfMedia and many visual novels' MP4 movies.
 *
 * <p>Wine decodes H.264 with GStreamer on the CPU either way, since DXVK has no video decoder. When the game hands
 * Media Foundation a DXGI device manager, Wine then copies every frame into a D3D11 texture through a staging
 * texture, from a pool of 10. If that step fails, Wine's source reader only logs a warning and never answers the game,
 * which waits for a frame that never comes. Unity gives up after 15 s ("WindowsVideoMedia error 0x80004004 ...
 * IMFSourceReader::WaitForSample") and tries again; on the POCO F8 Pro each try added about 288 MB of graphics memory.
 *
 * <p>So Wine is told not to create the manager, for every game, as Proton does per game with its "nomfdxgiman" switch.
 * The game then gets frames in normal memory and uploads them itself. Wine turns the switch on for any non-empty value,
 * "0" included, which is what the settings checkbox writes when unticked. So "0" is removed here, and that is how a
 * player turns the switch off for one game or container.
 */
public final class AgvnMediaFoundation {
    static final String NO_DXGI_MANAGER = "WINE_DO_NOT_CREATE_DXGI_DEVICE_MANAGER";
    static final String NEW_MEDIA_SOURCE = "WINE_NEW_MEDIA_SOURCE";
    /** How upstream's settings list spelled {@link #NEW_MEDIA_SOURCE}; Wine never read it. */
    static final String MISSPELLED_MEDIA_SOURCE = "WINE_NEW_MEDIASOURCE";

    private AgvnMediaFoundation() {}

    /** Call on the final environment, after the container's and the game's own variables. */
    public static void apply(EnvVars env) {
        String value = env.has(NO_DXGI_MANAGER) ? env.get(NO_DXGI_MANAGER).trim() : "1";
        if (value.isEmpty() || value.equals("0") || value.equalsIgnoreCase("false")) env.remove(NO_DXGI_MANAGER);
        else env.put(NO_DXGI_MANAGER, "1");

        if (env.has(MISSPELLED_MEDIA_SOURCE)) {
            if (!env.has(NEW_MEDIA_SOURCE)) env.put(NEW_MEDIA_SOURCE, env.get(MISSPELLED_MEDIA_SOURCE));
            env.remove(MISSPELLED_MEDIA_SOURCE);
        }
    }
}
