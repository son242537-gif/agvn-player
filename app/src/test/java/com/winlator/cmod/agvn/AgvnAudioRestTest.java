/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** While the game is stopped, pactl suspends the game's PulseAudio sink, and resumes it when the game goes on. */
public class AgvnAudioRestTest {
    @Test
    public void pactlSuspendsAndResumesTheDefaultSink() {
        assertEquals(Arrays.asList("/data/files/pulseaudio-gn/pactl", "suspend-sink", "@DEFAULT_SINK@", "1"),
                AgvnAudioRest.command("/data/files/pulseaudio-gn/pactl", true));
        assertEquals("0", AgvnAudioRest.command("pactl", false).get(3));
    }

    @Test
    public void pactlReachesTheGamesPulseAudioWithTheRuntimesLibraries() {
        Map<String, String> env = new HashMap<>();
        File dir = new File("/data/user/0/com.agvn.player/files/pulseaudio-gn");
        AgvnAudioRest.environment(env, dir, "/data/user/0/com.agvn.player/files/imagefs/tmp/.sound/AS0");
        assertEquals("/data/user/0/com.agvn.player/files/imagefs/tmp/.sound/AS0", env.get("PULSE_SERVER"));
        assertEquals("/system/lib64:" + new File(dir, "modules") + ":" + dir, env.get("LD_LIBRARY_PATH"));
        assertEquals(dir.getPath(), env.get("HOME"));
    }
}
