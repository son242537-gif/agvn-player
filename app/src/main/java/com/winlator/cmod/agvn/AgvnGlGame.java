/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A game that draws with OpenGL alone: its exe or a DLL beside it imports opengl32, and none imports Direct3D, DXGI or
 * DirectDraw. DXVK never runs for it, so the fixes that change DXVK (its version, async, WineD3D) change nothing.
 * The Agnietta (cocos2d-x with glew32 and SpiderMonkey, Mali-G57, 17/10/2026) got "Đổi DXVK sang bản 2.3.1" as a
 * fix to try. Only for an engine AGVN does not know: Unity, Unreal and the other engines it knows load Direct3D
 * themselves, which no import shows.
 */
final class AgvnGlGame {
    /** DLLs beside the exe read at most. */
    static final int MAX_DLLS = 64;
    private static final Map<String, Boolean> KNOWN = new ConcurrentHashMap<>();

    private AgvnGlGame() {}

    /** True when {@code s}'s game draws with OpenGL alone. Remembered per exe while the app runs. Never throws. */
    static boolean openGlOnly(Shortcut s) {
        String engine = s.getExtra(AgvnGameImporter.EXTRA_ENGINE);
        if (!engine.isEmpty() && !GameExeResolver.Engine.UNKNOWN.name().equals(engine)) return false;
        String exe = AgvnExeRedirect.toUnixPath(s.path, s.container);
        return exe != null && openGlOnly(new File(exe), engine.isEmpty());
    }

    /** {@code detect}: the engine is not known yet (a game added before engines were), so it is looked for first. */
    static boolean openGlOnly(File exe, boolean detect) {
        String key = exe.getPath() + "@" + exe.lastModified();
        Boolean known = KNOWN.get(key);
        if (known != null) return known;
        boolean gl = false;
        try {
            File folder = exe.getParentFile();
            boolean engine = detect && folder != null && GameExeResolver.detectEngine(folder) != GameExeResolver.Engine.UNKNOWN;
            List<String> own = engine ? null : AgvnPeFacts.imports(exe);
            if (own != null) {
                List<List<String>> modules = new ArrayList<>();
                modules.add(own);
                File[] dlls = folder != null ? folder.listFiles(f -> f.getName().toLowerCase(Locale.ROOT).endsWith(".dll")) : null;
                if (dlls != null) for (int i = 0; i < dlls.length && i < MAX_DLLS; i++) {
                    List<String> imports = dlls[i].isFile() ? AgvnPeFacts.imports(dlls[i]) : null;
                    if (imports != null) modules.add(imports);
                }
                gl = drawsWithOpenGlAlone(modules);
            }
        } catch (RuntimeException e) {
            gl = false; // unreadable: the fixes stay as they were
        }
        KNOWN.put(key, gl);
        return gl;
    }

    /** Each module's imports: opengl32 in one of them, Direct3D in none. Pure Java. */
    static boolean drawsWithOpenGlAlone(List<List<String>> modules) {
        boolean gl = false;
        for (List<String> module : modules) {
            for (String dll : module) {
                if (directX(dll)) return false;
                if (dll.equals("opengl32")) gl = true;
            }
        }
        return gl;
    }

    /** d3d9, d3d11, d3dx9_43, d3dcompiler_47 (ANGLE's), dxgi, ddraw... */
    static boolean directX(String dll) {
        return dll.startsWith("d3d") || dll.equals("dxgi") || dll.equals("ddraw");
    }
}
