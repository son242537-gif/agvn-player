/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Whether a Unity game has the Input System package, whose mouse on Windows comes only from raw input (WM_INPUT):
 * Unity.InputSystem.dll among a Mono game's assemblies, or that name in an IL2CPP game's metadata. Open At Nine
 * (Unity 2020.3, its menus on InputSystemUIInputModule) took no tap from the X server's pointer ({@link AgvnRawMouse}).
 * Pure Java.
 */
final class AgvnUnityInput {
    static final String ASSEMBLY = "Unity.InputSystem";
    private static final int BLOCK = 1 << 20;

    private AgvnUnityInput() {}

    /** True when the Unity game of {@code exe} has the Input System. */
    static boolean usesInputSystem(File exe) {
        File data = dataDir(exe);
        if (data == null) return false;
        if (AgvnRgssGame.child(AgvnRgssGame.child(data, "Managed"), ASSEMBLY + ".dll") != null) return true;
        File il2cpp = il2cppMetadata(exe);
        return il2cpp != null && contains(il2cpp, ASSEMBLY.getBytes(StandardCharsets.US_ASCII));
    }

    /** An IL2CPP game's metadata (the names of its assemblies, types...), or null: a Mono game, or not Unity. */
    static File il2cppMetadata(File exe) {
        File metadata = AgvnRgssGame.child(AgvnRgssGame.child(dataDir(exe), "il2cpp_data"), "Metadata");
        File il2cpp = AgvnRgssGame.child(metadata, "global-metadata.dat");
        return il2cpp != null && il2cpp.isFile() ? il2cpp : null;
    }

    /** &lt;exe name&gt;_Data beside {@code exe}, where Unity keeps the game; null when there is none. */
    static File dataDir(File exe) {
        if (exe == null || exe.getParentFile() == null) return null;
        String base = exe.getName().replaceFirst("(?i)\\.exe$", "");
        File data = AgvnRgssGame.child(exe.getParentFile(), base + "_Data");
        return data != null && data.isDirectory() ? data : null;
    }

    /** True when {@code file} holds {@code needle}; read in blocks that overlap by the needle's length. */
    static boolean contains(File file, byte[] needle) {
        byte[] buf = new byte[BLOCK + needle.length];
        try (InputStream in = new BufferedInputStream(new FileInputStream(file), BLOCK)) {
            int kept = 0;
            for (int n; (n = in.read(buf, kept, buf.length - kept)) > 0; ) {
                int end = kept + n;
                if (indexOf(buf, end, needle) >= 0) return true;
                kept = Math.min(needle.length - 1, end);
                System.arraycopy(buf, end - kept, buf, 0, kept);
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    private static int indexOf(byte[] buf, int end, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= end; i++) {
            for (int j = 0; j < needle.length; j++) if (buf[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }
}
