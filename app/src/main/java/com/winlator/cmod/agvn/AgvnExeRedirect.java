/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import com.winlator.cmod.container.Container;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Launch-time exe redirect for packaged Unreal games. The root {@code <Game>.exe} (~150 KB) is only a bootstrap: it
 * checks for the Visual C++ / .NET redistributables and asks the player to install them under Wine, while
 * {@code <Game>/Binaries/Win64/<Game>-Win64-Shipping.exe} runs fine on Wine's builtin msvcp140/vcruntime140.
 * AGVN imports already point at the Shipping exe; file-manager shortcuts and older imports may not. Only the command of
 * the current launch changes: the shortcut file (Shortcut.path and its extras) is never rewritten.
 */
public final class AgvnExeRedirect {
    private static final String TAG = "AGVN";

    private AgvnExeRedirect() {}

    /**
     * Path to launch instead of {@code shortcutPath}, in the same form (unix or DOS, quoted, trailing args kept), or
     * {@code shortcutPath} itself when nothing is redirected. .lnk shortcuts are left untouched. Never throws.
     */
    public static String effectivePath(String shortcutPath, Container container) {
        if (shortcutPath == null || container == null) return shortcutPath;
        try {
            return effectivePath(shortcutPath, driveMap(container));
        } catch (RuntimeException e) {
            Log.w(TAG, "exe redirect failed for " + shortcutPath, e);
            return shortcutPath;
        }
    }

    static String effectivePath(String shortcutPath, Map<String, String> drives) {
        String[] exeAndArgs = splitArgs(shortcutPath.replace("\"", "").trim());
        String from = exeAndArgs[0];
        if (from.toLowerCase(Locale.ROOT).endsWith(".lnk")) return shortcutPath;
        String unix = toUnixPath(from, drives);
        if (unix == null) return shortcutPath;
        File exe = new File(unix);
        File target = redirectUnrealBootstrap(exe);
        String rel = target.equals(exe) ? null : relative(exe.getParentFile(), target);
        int cut = Math.max(from.lastIndexOf('/'), from.lastIndexOf('\\'));
        if (rel == null || cut < 0) return shortcutPath;
        char sep = from.startsWith("/") ? '/' : '\\';
        String to = from.substring(0, cut + 1) + rel.replace('/', sep);
        Log.i(TAG, "exe redirect " + from + " -> " + to);
        return "\"" + to + "\"" + exeAndArgs[1];
    }

    /** Unix path of the shortcut's exe (quotes and trailing args removed), or null when a DOS drive is not mapped. */
    public static String toUnixPath(String shortcutPath, Container container) {
        return toUnixPath(shortcutPath, container != null ? driveMap(container) : null);
    }

    /** Same, with drive letter ("D" or "D:", any case) mapped to its unix folder by {@code drives}. */
    public static String toUnixPath(String shortcutPath, Map<String, String> drives) {
        if (shortcutPath == null) return null;
        String path = splitArgs(shortcutPath.replace("\"", "").trim())[0];
        if (path.startsWith("/")) return path;
        if (drives == null || path.length() < 3 || path.charAt(1) != ':' || !Character.isLetter(path.charAt(0))) return null;
        if (path.charAt(2) != '\\' && path.charAt(2) != '/') return null;
        String root = driveRoot(drives, path.charAt(0));
        if (root == null) return null;
        String rest = path.substring(3).replace('\\', '/').replaceAll("/+", "/");
        if (rest.startsWith("/")) rest = rest.substring(1);
        return rest.isEmpty() ? root : root.endsWith("/") ? root + rest : root + "/" + rest;
    }

    /**
     * The -Shipping.exe to run instead of an Unreal bootstrap or dev exe, or {@code exe} unchanged. Handled layouts:
     * {@code <Root>/<Name>.exe} with {@code <Root>/<Name>/Binaries/Win64/<Name>-Win64-Shipping.exe};
     * any {@code <Root>/*.exe} next to {@code <Root>/Engine} and {@code <Root>/<X>/Binaries/Win64/*-Shipping.exe};
     * {@code .../Binaries/Win64/<Name>[-...].exe} next to {@code <Name>-Win64-Shipping.exe}.
     */
    public static File redirectUnrealBootstrap(File exe) {
        if (exe == null || !exe.isFile()) return exe;
        String name = exe.getName();
        String lower = name.toLowerCase(Locale.ROOT);
        File dir = exe.getParentFile();
        if (dir == null || !lower.endsWith(".exe") || lower.endsWith("-shipping.exe")) return exe;
        String base = name.substring(0, name.length() - 4);
        File exact = new File(dir, base + "/Binaries/Win64/" + base + "-Win64-Shipping.exe");
        if (exact.isFile()) return exact;
        if (dir.getName().equalsIgnoreCase("Win64")) {
            File sibling = shippingFor(dir, base.toLowerCase(Locale.ROOT));
            return sibling != null ? sibling : exe;
        }
        if (!new File(dir, "Engine").isDirectory()) return exe;
        File shipping = GameExeResolver.findShipping(dir);
        return shipping != null ? shipping : exe;
    }

    /** First *-Shipping.exe in {@code win64} whose project name (text before the first '-') matches {@code base}. */
    private static File shippingFor(File win64, String base) {
        File[] files = win64.listFiles();
        if (files == null) return null;
        Arrays.sort(files);
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (!f.isFile() || !n.endsWith("-shipping.exe")) continue;
            String project = n.substring(0, n.indexOf('-'));
            if (!project.isEmpty() && (base.equals(project) || base.startsWith(project + "-"))) return f;
        }
        return null;
    }

    /**
     * Splits "x.exe -arg" into {"x.exe", " -arg"} like the launch command does: a space after the last '.' of the
     * file name starts the arguments.
     */
    static String[] splitArgs(String path) {
        int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        int dot = path.lastIndexOf('.');
        int space = dot > sep ? path.indexOf(' ', dot) : -1;
        if (space == -1) return new String[]{path, ""};
        return new String[]{path.substring(0, space), path.substring(space)};
    }

    /** Drive letter (upper case) -> unix folder, the way WineUtils.createDosdevicesSymlinks links dosdevices. */
    static Map<String, String> driveMap(Container container) {
        Map<String, String> drives = new HashMap<>();
        File root = container.getRootDir();
        if (root != null) {
            drives.put("C", new File(root, ".wine/drive_c").getPath());
            File home = root.getParentFile();
            if (home != null && home.getParentFile() != null) drives.put("Z", home.getParentFile().getPath());
        }
        try {
            for (String[] drive : container.drivesIterator()) {
                if (drive[0] == null || drive[0].isEmpty() || drive[1] == null || drive[1].isEmpty()) continue;
                drives.put(drive[0].substring(0, 1).toUpperCase(Locale.ROOT), drive[1]);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "cannot read container drives", e); // malformed drives string: keep C: and Z:
        }
        return drives;
    }

    private static String driveRoot(Map<String, String> drives, char letter) {
        char wanted = Character.toUpperCase(letter);
        for (Map.Entry<String, String> e : drives.entrySet()) {
            String key = e.getKey();
            if (key == null || key.isEmpty() || Character.toUpperCase(key.charAt(0)) != wanted) continue;
            if (key.length() > 1 && !key.substring(1).equals(":")) continue;
            String root = e.getValue();
            if (root == null || root.isEmpty()) return null;
            while (root.length() > 1 && (root.endsWith("/") || root.endsWith("\\"))) root = root.substring(0, root.length() - 1);
            return root;
        }
        return null;
    }

    private static String relative(File dir, File file) {
        if (dir == null) return null;
        String d = dir.getAbsolutePath() + File.separator;
        String f = file.getAbsolutePath();
        return f.startsWith(d) ? f.substring(d.length()).replace(File.separatorChar, '/') : null;
    }
}
