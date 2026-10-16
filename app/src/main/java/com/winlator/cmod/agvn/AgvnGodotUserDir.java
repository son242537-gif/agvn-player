/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a Godot game keeps user://, its saves among them, as Godot for Windows sets it (OS_Windows::get_user_data_dir):
 * %APPDATA%/Godot/app_userdata/&lt;project name&gt;, or %APPDATA%/&lt;its own folder&gt; when the project asks for one.
 * "Chạy nhẹ" keeps it there too ({@link AgvnGodotLight}), so the Windows version and the phone share saves, but the
 * name is only in the project's settings: project.binary in the pack (ProjectSettings::_save_settings_binary), "ECFG",
 * a count, then each key as a length and UTF-8 bytes and its value as a length and an encoded Variant
 * (core/io/marshalls.cpp: a type, then a bool as 4 bytes or a string as a length and UTF-8). Pure Java (JVM-testable).
 */
final class AgvnGodotUserDir {
    static final String NAME = "application/config/name", OWN = "application/config/use_custom_user_dir",
            OWN_NAME = "application/config/custom_user_dir_name";
    /** The renderer on Android ("gl_compatibility", "mobile"); a project without it uses "mobile" (Vulkan). */
    static final String METHOD_MOBILE = "rendering/renderer/rendering_method.mobile";
    static final String ROAMING = "AppData/Roaming/", APP_USERDATA = ROAMING + "Godot/app_userdata/";
    private static final int ECFG = 0x47464345, BOOL = 1, STRING = 4, MAX_SETTINGS = 4 << 20, MAX_TEXT = 4096;

    private AgvnGodotUserDir() {}

    /** user:// of the game {@code exe} opens, relative to the Windows user folder ("AppData/Roaming/..."), or null. */
    static String of(File exe) {
        Map<String, Object> settings = settingsOf(exe);
        return settings != null ? folder(settings) : null;
    }

    /** The settings {@link #settings} reads from project.binary of the pack {@code exe} opens; null if unreadable. */
    static Map<String, Object> settingsOf(File exe) {
        AgvnGodotPack pack = AgvnGodotLight.mainPack(exe);
        AgvnGodotPack.Entry e = pack != null ? pack.find("project.binary") : null;
        if (e == null || e.size < 8 || e.size > MAX_SETTINGS || (e.flags & AgvnGodotPack.FILE_ENCRYPTED) != 0) return null;
        try (RandomAccessFile in = new RandomAccessFile(pack.file, "r")) {
            byte[] bytes = new byte[(int) e.size];
            in.seek(e.offset);
            in.readFully(bytes);
            return settings(bytes);
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    /**
     * The settings of project.binary's {@code bytes} that name user:// ({@link #NAME}, {@link #OWN}, {@link #OWN_NAME})
     * and the renderer on Android ({@link #METHOD_MOBILE}).
     */
    static Map<String, Object> settings(byte[] bytes) {
        Map<String, Object> out = new HashMap<>();
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        try {
            if (b.getInt() != ECFG) return out;
            for (int i = b.getInt(); i > 0 && b.remaining() >= 8; i--) {
                String key = text(b, b.getInt());
                int length = b.getInt();
                if (length < 0 || length > b.remaining()) break;
                int next = b.position() + length;
                boolean wanted = key.equals(NAME) || key.equals(OWN) || key.equals(OWN_NAME)
                        || key.equals(METHOD_MOBILE);
                if (length >= 8 && wanted) {
                    int type = b.getInt() & 0xff;
                    if (type == STRING) out.put(key, text(b, b.getInt()));
                    else if (type == BOOL) out.put(key, b.getInt() != 0);
                }
                b.position(next);
            }
        } catch (BufferUnderflowException | IllegalArgumentException e) {
            // cut short: what was read so far
        }
        return out;
    }

    /** user:// for {@code settings}, relative to the Windows user folder. */
    static String folder(Map<String, Object> settings) {
        Object name = settings.get(NAME), own = settings.get(OWN_NAME);
        String app = safe(name != null ? name.toString() : "", false);
        if (app.isEmpty()) return APP_USERDATA + "[unnamed project]";
        if (!Boolean.TRUE.equals(settings.get(OWN))) return APP_USERDATA + app;
        String folder = safe(own != null ? own.toString() : "", true);
        return ROAMING + (folder.isEmpty() ? app : folder);
    }

    /** OS::get_safe_dir_name: what a Windows folder name cannot hold becomes "-", and no ".." when paths are allowed. */
    static String safe(String name, boolean paths) {
        List<String> bad = new ArrayList<>(Arrays.asList(":", "*", "?", "\"", "<", ">", "|"));
        String s;
        if (paths) {
            bad.add("..");
            s = strip(name.replace("\\", "/").replace("//", "/"));
        } else {
            bad.add("/");
            bad.add("\\");
            s = strip(name);
            if (s.equals(".")) s = "dot";
            else if (s.equals("..")) s = "twodots";
        }
        for (String c : bad) s = s.replace(c, "-");
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '.') end--;
        return s.substring(0, end);
    }

    /** String::strip_edges: no spaces or control characters at either end. */
    private static String strip(String s) {
        int from = 0, to = s.length();
        while (from < to && s.charAt(from) <= ' ') from++;
        while (to > from && s.charAt(to - 1) <= ' ') to--;
        return s.substring(from, to);
    }

    private static String text(ByteBuffer b, int length) {
        if (length < 0 || length > MAX_TEXT || length > b.remaining()) throw new IllegalArgumentException("text of " + length);
        byte[] bytes = new byte[length];
        b.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
