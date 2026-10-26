/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;
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
 *
 * <p>A movie whose stream Proton's media source (wg_source.c) cannot decode as it comes: it looks for a decoder that
 * takes the stream's caps straight from the demuxer, with no parser before it, and MP3 audio in an MP4 file
 * ("audio/mpeg, mpegversion=(int)1", not yet "parsed") has none, though mpg123audiodec is there. Proton then hands the
 * file to Steam's media converter, which the app has not ({@link #NO_TRANSCODER}), and the movie does not open:
 * Tenants of the Dead (Unity 2019.4, Mali-G57, 26/10/2026) went black at a scene change, its VideoPlayer waiting for a
 * movie that never came ("WindowsVideoMedia error 0xc00d36bb ... CreateObjectFromByteStream"). Wine's old media source
 * (decodebin, which puts mpegaudioparse first) plays it; WINE_OLD_MEDIA_SOURCE=1 picks it ("media-old"), offered only
 * to a game whose movie failed so ({@link AgvnMovieWatch}, game-problems.json "movie-transcode"), and only on Proton 9:
 * Proton 10's winegstreamer has neither the switch nor this way of failing.
 */
public final class AgvnMediaFoundation {
    static final String NO_DXGI_MANAGER = "WINE_DO_NOT_CREATE_DXGI_DEVICE_MANAGER";
    static final String NEW_MEDIA_SOURCE = "WINE_NEW_MEDIA_SOURCE";
    /** How upstream's settings list spelled {@link #NEW_MEDIA_SOURCE}; Wine never read it. */
    static final String MISSPELLED_MEDIA_SOURCE = "WINE_NEW_MEDIASOURCE";
    static final String OLD_MEDIA_SOURCE = "WINE_OLD_MEDIA_SOURCE", FIX = "media-old";
    /** What Wine prints (always, logs on or off) when a movie needs Steam's converter: the movie then does not open. */
    static final String NO_TRANSCODER = "failed to create protondemuxer";

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

    /** True when the game reads movies the old way: WINE_OLD_MEDIA_SOURCE set to a number but 0, as Wine reads it. */
    static boolean oldSource(Shortcut s) {
        EnvVars env = new EnvVars(s.container.getEnvVars());
        env.putAll(s.getExtra("envVars"));
        return oldSource(env.has(OLD_MEDIA_SOURCE) ? env.get(OLD_MEDIA_SOURCE) : null);
    }

    /** Wine's own test: getenv("WINE_OLD_MEDIA_SOURCE") and atoi() of it. */
    static boolean oldSource(String value) {
        if (value == null) return false;
        String v = value.trim();
        int end = v.startsWith("-") || v.startsWith("+") ? 1 : 0;
        while (end < v.length() && Character.isDigit(v.charAt(end))) end++;
        try {
            return Integer.parseInt(v.substring(0, end)) != 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** True when the game can switch to the old way: it runs Proton 9 ({@link AgvnWine10}) and does not read so yet. */
    static boolean canReadOldWay(Shortcut s) {
        return !AgvnWine10.runs(s.container) && !oldSource(s);
    }

    /** "Đọc phim bằng cách cũ của Wine" for a game that can switch to it ({@link #canReadOldWay}); else null. */
    static AgvnFixes.Fix fix(Context a, Shortcut s) {
        return canReadOldWay(s) ? new AgvnFixes.Fix(FIX, a.getString(R.string.agvn_fix_media_old), "1") : null;
    }

    /** Puts WINE_OLD_MEDIA_SOURCE=1 among the game's own variables; the caller saves. */
    static void useOldSource(Shortcut s) {
        s.putExtra("envVars", AgvnLockFixes.withVar(s.getExtra("envVars"), OLD_MEDIA_SOURCE, "1"));
    }

    /** "âm thanh MP3" for "audio/mpeg, mpegversion=(int)1, …" (a caps Wine printed), else its kind; null for none. */
    static String kind(String caps) {
        if (caps == null) return null;
        String c = caps.trim();
        if (c.startsWith("audio/mpeg") && c.contains("mpegversion=(int)1")) return "âm thanh MP3";
        if (c.startsWith("audio/mpeg")) return "âm thanh AAC";
        int cut = c.indexOf(',');
        return cut > 0 ? c.substring(0, cut) : c;
    }
}
