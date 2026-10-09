/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.xenvironment.components.PulseAudioComponent;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The game's sound output rests while the game is stopped (the player left the app or the screen went off). The
 * activity stops Wine's processes then, but PulseAudio is not one of them: its AAudio stream went on playing silence,
 * so the phone's audio path and CPU never slept while AGVN was in the background (players, 09/10/2026). The bundled
 * PulseAudio's pactl suspends its sink: module-aaudio-sink then stops the AAudio stream, and starts it again when the
 * sink resumes. One worker applies each wish in order, so a quick leave and return never leaves the game silent. A
 * PulseAudio without pactl (the older runtime) plays on, as before.
 */
public final class AgvnAudioRest {
    private static final String TAG = "AGVN";
    private static final long WAIT_MS = 3000;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> new Thread(r, "AgvnAudioRest"));
    /** True once a rest was asked for, until pactl resumed the sink: a return then never skips it. On the worker only. */
    private static boolean resting;

    private AgvnAudioRest() {}

    /** {@code rest}: the game stopped; else it goes on. Never blocks; nothing happens without the game's PulseAudio. */
    public static void set(PulseAudioComponent audio, boolean rest) {
        if (audio == null) return;
        File dir = audio.agvnRuntimeDir();
        String socket = audio.agvnSocketPath();
        WORKER.execute(() -> apply(dir, socket, rest));
    }

    private static void apply(File dir, String socket, boolean rest) {
        if (!rest && !resting) return; // nothing was put to rest
        File pactl = new File(dir, "pactl");
        if (!pactl.isFile()) return;
        if (rest) resting = true; // even if pactl fails or hangs: it may have suspended the sink
        try {
            FileUtils.chmod(pactl, 0771);
            ProcessBuilder builder = new ProcessBuilder(command(pactl.getAbsolutePath(), rest)).directory(dir).redirectErrorStream(true);
            environment(builder.environment(), dir, socket);
            Process process = builder.start();
            try (InputStream out = process.getInputStream()) {
                while (out.read() != -1) ; // pactl prints at most an error line
            }
            if (!process.waitFor(WAIT_MS, TimeUnit.MILLISECONDS)) {
                process.destroy();
                Log.w(TAG, "sound output: pactl did not answer");
                return;
            }
            boolean done = process.exitValue() == 0;
            if (done && !rest) resting = false;
            Log.i(TAG, "sound output " + (rest ? "rests" : "back") + (done ? "" : ": pactl exit " + process.exitValue()));
        } catch (Exception e) {
            Log.w(TAG, "sound output not changed", e);
        }
    }

    /** pactl's arguments: the default sink (the game's only one) suspended or resumed. */
    static List<String> command(String pactl, boolean rest) {
        return Arrays.asList(pactl, "suspend-sink", "@DEFAULT_SINK@", rest ? "1" : "0");
    }

    /** What pactl needs: the game's PulseAudio socket and the runtime's own libraries, as the server got them. */
    static void environment(Map<String, String> env, File dir, String socket) {
        env.put("PULSE_SERVER", socket);
        env.put("LD_LIBRARY_PATH", "/system/lib64:" + new File(dir, "modules") + ":" + dir);
        env.put("HOME", dir.getAbsolutePath());
    }
}
