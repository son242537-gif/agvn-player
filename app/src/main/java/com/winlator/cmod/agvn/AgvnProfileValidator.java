/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

/** Validation rules for {@link AgvnProfile} v1. Pure Java so it runs in JVM unit tests. */
public final class AgvnProfileValidator {
    static final Pattern SAFE_VALUE = Pattern.compile("^[A-Za-z0-9_\\-=./:,+]*$");
    static final Pattern ENV_KEY = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    static final Pattern RESOLUTION = Pattern.compile("^(\\d{3,4})x(\\d{3,4})$");
    static final Pattern INI_SECTION = Pattern.compile("^(/Script/[A-Za-z0-9_.]+|SystemSettings|ConsoleVariables|TextureStreaming)$");
    static final Pattern INI_KEY = Pattern.compile("^[A-Za-z0-9_.]+$");

    private AgvnProfileValidator() {}

    /**
     * Checks every rule and returns the exe path (relative to {@code gameDir}, '/' separated) that should be
     * launched. UE games are switched to their *-Win64-Shipping.exe when the profile names another exe.
     */
    public static String validate(AgvnProfile p, File gameDir) throws AgvnProfileException {
        if (p.schemaVersion != AgvnProfile.SCHEMA_VERSION)
            throw new AgvnProfileException("Phiên bản profile " + p.schemaVersion + " không được hỗ trợ (cần schemaVersion = 1).");
        if (p.name == null || p.name.trim().isEmpty() || p.name.length() > 80 || p.name.matches(".*[\\\\/:*?\"<>|].*"))
            throw new AgvnProfileException("Tên game (name) trống, quá dài hoặc có ký tự không hợp lệ.");

        for (String arg : p.args) {
            if (arg == null || !SAFE_VALUE.matcher(arg).matches())
                throw new AgvnProfileException("Tham số chạy (args) có ký tự không an toàn: " + arg);
        }
        for (Map.Entry<String, String> e : p.env.entrySet()) {
            if (!ENV_KEY.matcher(e.getKey()).matches() || e.getValue() == null || !SAFE_VALUE.matcher(e.getValue()).matches())
                throw new AgvnProfileException("Biến môi trường (env) không an toàn: " + e.getKey());
        }
        for (Map.Entry<String, String> e : p.dllOverrides.entrySet()) {
            if (!GameDllOverrides.isValid(e.getKey(), e.getValue()))
                throw new AgvnProfileException("dllOverrides không hợp lệ: " + e.getKey() + " (chỉ dùng n, b, n,b hoặc b,n)");
        }
        checkResolution(p.resolution);
        checkFps(p.fpsLimit);
        checkPool(p.texturePool);
        if (p.weakDevice != null) {
            checkResolution(p.weakDevice.resolution);
            checkFps(p.weakDevice.fpsLimit);
            checkPool(p.weakDevice.texturePool);
        }
        for (Map.Entry<String, Map<String, String>> section : p.ueEngineIni.entrySet()) {
            if (!INI_SECTION.matcher(section.getKey()).matches() || section.getValue() == null)
                throw new AgvnProfileException("Mục Engine.ini không được phép: " + section.getKey());
            for (Map.Entry<String, String> kv : section.getValue().entrySet()) {
                if (!INI_KEY.matcher(kv.getKey()).matches() || kv.getValue() == null || !SAFE_VALUE.matcher(kv.getValue()).matches())
                    throw new AgvnProfileException("Khóa Engine.ini không hợp lệ: " + kv.getKey());
            }
        }
        String exe = resolveExe(p, gameDir);
        if (gameDir.getName().contains("wine ") || exe.contains("wine "))
            throw new AgvnProfileException("Tên thư mục hoặc đường dẫn exe không được chứa chữ \"wine \" (viết thường, có dấu cách). Hãy đổi tên thư mục.");
        return exe;
    }

    private static String resolveExe(AgvnProfile p, File gameDir) throws AgvnProfileException {
        GameExeResolver.Engine engine = GameExeResolver.detectEngine(gameDir);
        String suggested = GameExeResolver.resolveExe(gameDir, engine);
        String exe = p.exe != null ? p.exe.trim().replace('\\', '/') : "";
        if (exe.isEmpty()) {
            if (suggested == null) throw new AgvnProfileException("Không tìm thấy file .exe để chạy. Hãy ghi rõ \"exe\" trong profile.");
            return suggested;
        }
        if (exe.startsWith("/") || exe.matches("^[A-Za-z]:.*") || exe.contains(".."))
            throw new AgvnProfileException("Đường dẫn exe phải nằm trong thư mục game: " + exe);
        if (!exe.toLowerCase().endsWith(".exe"))
            throw new AgvnProfileException("File chạy phải là .exe: " + exe);
        File file = new File(gameDir, exe);
        if (!isInside(file, gameDir)) throw new AgvnProfileException("Đường dẫn exe phải nằm trong thư mục game: " + exe);
        if (!file.isFile()) throw new AgvnProfileException("Không tìm thấy file chạy: " + exe);
        if (engine == GameExeResolver.Engine.UNREAL && !exe.toLowerCase().endsWith("-shipping.exe") && suggested != null)
            return suggested;
        return exe;
    }

    static boolean isInside(File file, File dir) {
        try {
            String d = dir.getCanonicalPath() + File.separator;
            return file.getCanonicalPath().startsWith(d);
        } catch (IOException e) {
            return false;
        }
    }

    private static void checkResolution(String res) throws AgvnProfileException {
        if (res == null) return;
        java.util.regex.Matcher m = RESOLUTION.matcher(res);
        if (!m.matches()) throw new AgvnProfileException("Độ phân giải phải có dạng RỘNGxCAO, ví dụ 1280x720: " + res);
        int w = Integer.parseInt(m.group(1)), h = Integer.parseInt(m.group(2));
        if (w < 640 || w > 3840 || h < 360 || h > 2160)
            throw new AgvnProfileException("Độ phân giải ngoài khoảng cho phép (640x360 – 3840x2160): " + res);
    }

    private static void checkFps(Integer fps) throws AgvnProfileException {
        if (fps == null || fps == 0) return;
        if (fps < 15 || fps > 120) throw new AgvnProfileException("Giới hạn FPS phải từ 15 đến 120 (hoặc 0 = không giới hạn): " + fps);
    }

    private static void checkPool(Integer pool) throws AgvnProfileException {
        if (pool == null || pool == 0) return;
        if (pool < 128 || pool > 4096) throw new AgvnProfileException("Texture pool phải từ 128 đến 4096 MB: " + pool);
    }
}
