/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.KeyValueSet;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * VKD3D-Proton for a game that draws with DirectX 12 only.
 *
 * <p>A container comes with no VKD3D ("None", DefaultVersion.VKD3D): the game then gets Wine's own d3d12.dll, which
 * cannot run such a game. Idol Ikusei Simulator (Magine, SDL3 GPU with DXIL shaders only, POCO F8 Pro, 22/11/2026)
 * loaded "d3d12.dll : builtin" and closed at once with its own box, "This PC's graphics device (GPU) could not start
 * the game. It needs a DirectX 12 capable GPU ... gpu_unsupported"; with VKD3D-Proton 2.14.1's d3d12.dll it ran,
 * about 30 FPS. That VKD3D is in the app (dxwrapper/vkd3d-2.14.1.tzst) and XServerDisplayActivity puts it in place when
 * the game's DXVK settings name it.
 *
 * <p>So at its start such a game gets {@link #VERSION} in those settings, for that start only, when they name none: a
 * Magine game, or one whose exe or a DLL beside it imports d3d12.dll and none imports a Direct3D before 12 or OpenGL.
 * A game that can also draw with DirectX 11 is left as it is: Unreal Engine 5 picks DirectX 12 when it works, and that
 * stopped UE 5.8 games as they started. A game the player gave a VKD3D keeps it. Pure Java but {@link #atLaunch}.
 */
public final class AgvnVkd3d {
    /** The VKD3D-Proton the app carries (assets/dxwrapper/vkd3d-2.14.1.tzst). */
    static final String VERSION = "2.14.1";
    static final String KEY = "vkd3dVersion";
    /** Imports of a game that can draw without DirectX 12 (as AgvnPeFacts.imports names them: no ".dll"). */
    static final List<String> OTHER_APIS = Arrays.asList("d3d11", "d3d10", "d3d10_1", "d3d9", "d3d8", "ddraw",
            "opengl32");
    /** A folder of more DLLs than this is read no further (a game's own runtime, not its renderer). */
    private static final int MAX_DLLS = 64;
    private static volatile String note;

    private AgvnVkd3d() {}

    /**
     * After the game's DXVK settings are read (XServerDisplayActivity): {@code config} gets {@link #VERSION} when the
     * game needs DirectX 12 and {@code dxwrapper} is DXVK with no VKD3D. Never throws.
     */
    public static void atLaunch(Shortcut s, String dxwrapper, KeyValueSet config) {
        try {
            if (s == null || config == null || dxwrapper == null || !dxwrapper.contains("dxvk")) return;
            if (!none(config.get(KEY))) return;
            String engine = s.getExtra(AgvnGameImporter.EXTRA_ENGINE);
            String path = AgvnExeRedirect.toUnixPath(s.path, s.container);
            String why = why(engine, path != null ? new File(path) : null);
            if (why == null) return;
            config.put(KEY, VERSION);
            note = "Game chỉ vẽ bằng DirectX 12 (" + why + "): dùng VKD3D " + VERSION + " cho lần chạy này, thay cho"
                    + " d3d12.dll của Wine";
        } catch (RuntimeException e) {
            // the game starts as its settings say
        }
    }

    /** True for a VKD3D setting that names none. */
    static boolean none(String version) {
        return version == null || version.trim().isEmpty() || version.trim().equalsIgnoreCase("None");
    }

    /** Why the game needs DirectX 12 ("Magine", "Game.exe nạp d3d12.dll"); null when it does not or cannot be told. */
    static String why(String engine, File exe) {
        if (GameExeResolver.Engine.MAGINE.name().equals(engine)) return "engine Magine";
        if (exe == null || !exe.isFile()) return null;
        String dx12 = null;
        for (File f : files(exe)) {
            List<String> imports = AgvnPeFacts.imports(f);
            if (imports == null) continue;
            for (String dll : imports) {
                String name = dll.toLowerCase(Locale.ROOT).replaceAll("\\.dll$", "");
                if (OTHER_APIS.contains(name)) return null;
                if (dx12 == null && name.equals("d3d12")) dx12 = f.getName() + " nạp d3d12.dll";
            }
        }
        return dx12;
    }

    /** {@code exe}, then the DLLs beside it, unless there are too many to be the game's own. */
    private static List<File> files(File exe) {
        List<File> all = new java.util.ArrayList<>();
        all.add(exe);
        File[] dlls = exe.getParentFile() != null
                ? exe.getParentFile().listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".dll")) : null;
        if (dlls != null && dlls.length <= MAX_DLLS) {
            Arrays.sort(dlls);
            all.addAll(Arrays.asList(dlls));
        }
        return all;
    }

    /** The line for the session's events, once; null when nothing was set. */
    static String takeNote() {
        String n = note;
        note = null;
        return n;
    }
}
