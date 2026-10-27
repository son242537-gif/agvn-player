/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a Python game (PyInstaller, {@link AgvnPeFacts}) is made with, for its session events: its Python and the
 * libraries that draw or play it, read from the names in its _internal/ folder (PyInstaller 6) or beside its exe (5).
 * Isekai NTR Inn (27/10/2026) drew nothing at two starts on an Adreno 840, and the log said only "PyInstaller, game
 * Python": pygame on SDL2, pyglet or Qt each draw another way (GDI, Direct3D, OpenGL). Pure Java (JVM-testable).
 */
final class AgvnPythonFacts {
    private static final Pattern PYTHON_DLL = Pattern.compile("python(\\d)(\\d{1,2})\\.dll");
    /** A package (folder or .pyd) → its name in the line; first match wins. */
    private static final String[][] PACKAGES = {{"pygame", "pygame"}, {"pyglet", "pyglet"}, {"arcade", "arcade"},
            {"moderngl", "moderngl"}, {"opengl", "PyOpenGL"}, {"pyqt5", "PyQt5"}, {"pyqt6", "PyQt6"},
            {"pyside2", "PySide2"}, {"pyside6", "PySide6"}, {"kivy", "Kivy"}, {"panda3d", "Panda3D"}, {"ursina", "Ursina"},
            {"raylib", "raylib"}, {"pyray", "raylib"}, {"glfw", "glfw"}, {"wx", "wxPython"}, {"tkinter", "tkinter"},
            {"_tkinter", "tkinter"}, {"pygame_gui", "pygame_gui"}, {"cv2", "OpenCV"}, {"numpy", "numpy"},
            {"pyaudio", "PyAudio"}, {"sounddevice", "sounddevice"}, {"vlc", "python-vlc"}};
    /** DLLs that say how it draws or plays. */
    private static final String[] DLLS = {"sdl2.dll", "sdl3.dll", "sdl2_mixer.dll", "glfw3.dll", "opengl32sw.dll",
            "libegl.dll", "d3dcompiler_47.dll", "qt5core.dll", "qt6core.dll", "tcl86t.dll", "libvlc.dll", "openal32.dll"};

    private AgvnPythonFacts() {}

    /** "Game Python: Python 3.12 · thư viện: pygame, numpy · DLL: SDL2.dll"; null when nothing is known. */
    static String describe(File exeDir) {
        if (exeDir == null) return null;
        List<String> names = new ArrayList<>();
        for (File dir : new File[]{new File(exeDir, "_internal"), exeDir}) {
            String[] in = dir.list();
            if (in != null) for (String n : in) names.add(n);
        }
        return describe(names);
    }

    static String describe(List<String> names) {
        String python = null;
        Set<String> packages = new LinkedHashSet<>(), dlls = new LinkedHashSet<>();
        for (String name : names) {
            String n = name.toLowerCase(Locale.ROOT);
            Matcher m = PYTHON_DLL.matcher(n);
            if (m.matches() && python == null) python = "Python " + m.group(1) + "." + m.group(2);
            String bare = n.replaceFirst("\\.(pyd|py|pyc)$", "").replaceFirst("\\.cp\\d+-win_amd64$", "");
            for (String[] p : PACKAGES) {
                if (bare.equals(p[0]) || bare.startsWith(p[0] + "-") && n.endsWith(".dist-info")) {
                    packages.add(p[1]);
                    break;
                }
            }
            for (String d : DLLS) if (n.equals(d)) dlls.add(name);
        }
        if (python == null && packages.isEmpty() && dlls.isEmpty()) return null;
        StringBuilder s = new StringBuilder("Game Python: ").append(python != null ? python : "Python ?");
        if (!packages.isEmpty()) s.append(" · thư viện: ").append(String.join(", ", packages));
        if (!dlls.isEmpty()) s.append(" · DLL: ").append(String.join(", ", dlls));
        return s.toString();
    }
}
