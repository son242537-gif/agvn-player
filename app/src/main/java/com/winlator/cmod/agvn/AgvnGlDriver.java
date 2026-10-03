/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.core.EnvVars;

import java.io.File;

/**
 * Keeps the OpenGL driver installed in the imagefs in step with the one bundled in the APK
 * (assets/graphics_driver/opengl_*.tzst). A container re-installs it when the marker it stored differs, so a new
 * bundled build reaches games that already ran, and the libgallium of the previous Mesa version is removed.
 */
public final class AgvnGlDriver {
    /**
     * Bump when a bundled opengl_zink.tzst changes. 1 = Ludashi's Mesa 24.3.0 debug build (aborted games that bind a
     * too-big uniform buffer); 2 = AGVN's Mesa 25.1.9 release build (scripts/agvn/zink/build-zink.sh); 3 = the same
     * with the freed-buffer cache capped at 256 MB (patch 0007).
     */
    static final int ZINK_REVISION = 3;

    private AgvnGlDriver() {}

    /**
     * Godot asks for an OpenGL 3.3 core context and stops with "Unable to initialize video driver" when the driver
     * offers less. On Zink (phones without Turnip) a Godot game ({@code engine} "GODOT") gets
     * MESA_GL_VERSION_OVERRIDE=3.3 and MESA_GLSL_VERSION_OVERRIDE=330, so Zink offers 3.3; Godot's renderer needs
     * nothing above it. Values the player set stay.
     */
    public static void forGodot(String engine, EnvVars env) {
        if (!"GODOT".equals(engine)) return;
        if (!env.has("MESA_GL_VERSION_OVERRIDE")) env.put("MESA_GL_VERSION_OVERRIDE", "3.3");
        if (!env.has("MESA_GLSL_VERSION_OVERRIDE")) env.put("MESA_GLSL_VERSION_OVERRIDE", "330");
    }

    /** Value stored in the container extra "installedOpenGLDriver" once {@code driver} is installed. */
    public static String marker(String driver) {
        return "zink".equals(driver) ? "zink@" + ZINK_REVISION : driver;
    }

    /** Deletes usr/lib/libgallium-*.so (up to 85 MB each) so only the one the next extraction brings stays. */
    public static void removeOldGallium(File rootDir) {
        File[] old = new File(rootDir, "usr/lib").listFiles((dir, name) -> isGallium(name));
        if (old == null) return;
        for (File f : old) f.delete();
    }

    static boolean isGallium(String name) {
        return name.startsWith("libgallium-") && name.endsWith(".so");
    }
}
