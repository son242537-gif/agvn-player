/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.EnvVars;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A Unity game that plays movies with AVPro Video (RenderHeads): its Windows plugins are in
 * {@code <game>_Data/Plugins/x86_64/} (or {@code x86/}, or {@code Plugins/} itself). NZGY (Unity 2022, POCO F8 Pro,
 * 30/10/2026) showed what such a game needs from Wine:
 * - Media Foundation with hardware decoding, AVPro's default, needs a DXGI device manager, so the game gets one
 *   ({@link AgvnMediaFoundation#apply(EnvVars, boolean)}).
 * - Its WinRT player ({@link #WINRT_DLL}) uses Windows.Media.Playback, which Wine has not: the game crashed in
 *   kernelbase.dll, called from that DLL. Wine is told not to load it ("d"), so AVPro reports that it cannot load the
 *   DLL instead of taking the game down. Its Media Foundation and DirectShow players are other DLLs.
 * Pure Java (JVM-testable).
 */
public final class AgvnAvPro {
    static final String DLL = "avprovideo.dll", WINRT_DLL = "avprovideowinrt.dll";
    /** WINEDLLOVERRIDES entry: Wine does not load AVPro's WinRT player. */
    static final String NO_WINRT = "avprovideowinrt";

    private AgvnAvPro() {}

    /** Call on the final environment, after the container's and the game's own variables. */
    public static void apply(EnvVars env, Shortcut shortcut) {
        String path = shortcut != null ? AgvnExeRedirect.toUnixPath(shortcut.path, shortcut.container) : null;
        apply(env, path != null ? dlls(new File(path)) : Collections.emptySet());
    }

    /** {@code dlls}: the AVPro plugins of the game, as {@link #dlls(File)} lists them. */
    static void apply(EnvVars env, Set<String> dlls) {
        AgvnMediaFoundation.apply(env, dlls.contains(DLL));
        if (!dlls.contains(WINRT_DLL)) return;
        String existing = env.has("WINEDLLOVERRIDES") ? env.get("WINEDLLOVERRIDES") : "";
        String merged = GameDllOverrides.merge(existing, Collections.singletonMap(NO_WINRT, "d"));
        if (!merged.equals(existing)) env.put("WINEDLLOVERRIDES", merged);
    }

    /** The AVPro plugins ({@link #DLL}, {@link #WINRT_DLL}, lower case) in a {@code *_Data/Plugins} folder beside exe. */
    static Set<String> dlls(File exe) {
        Set<String> found = new HashSet<>();
        File dir = exe.getParentFile();
        File[] data = dir != null ? dir.listFiles((d, n) -> n.endsWith("_Data")) : null;
        if (data == null) return found;
        for (File d : data) {
            File plugins = new File(d, "Plugins");
            add(plugins, found);
            File[] sub = plugins.listFiles(File::isDirectory);
            if (sub != null) for (File s : sub) add(s, found);
        }
        return found;
    }

    private static void add(File dir, Set<String> found) {
        String[] names = dir.list();
        if (names == null) return;
        for (String n : names) {
            String low = n.toLowerCase(Locale.ROOT);
            if (low.equals(DLL) || low.equals(WINRT_DLL)) found.add(low);
        }
    }
}
