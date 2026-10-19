/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.util.Collections;
import java.util.Locale;

/**
 * "Tự sửa lỗi" for a game Wine stopped with "Unimplemented function version.dll.GetFileVersionInfoSizeW called": a DLL
 * beside the exe, named after one of Windows' own, forwards only the functions the game calls (a cheat table's
 * version.dll, a mod loader's winmm.dll). Wine's own code that uses the same DLL then finds the rest missing, as the
 * game's DLLs are loaded first ({@link GameDllOverrides}): mmdevapi, Wine's sound, called version.dll's
 * GetFileVersionInfoSizeW, and コトネイロ stopped as it opened (20/10/2026). The game starts with Wine's copy of that DLL
 * instead ("dll-wine"): it runs, without the cheat or mod in it. Pure Java but {@link #fix}.
 */
final class AgvnDllProxy {
    static final String FIX = "dll-wine";

    private AgvnDllProxy() {}

    /**
     * "version" of {@code named} ("version.dll", the DLL Wine named) when a file of that name is beside the game's exe
     * in {@code exeDir} and {@code envVars} does not already load Wine's copy; else null: Wine's own DLL lacks it.
     */
    static String dll(File exeDir, String named, String envVars) {
        String name = named != null ? named.trim().toLowerCase(Locale.ROOT) : "";
        if (!name.endsWith(".dll") || name.length() <= 4 || name.contains("/") || name.contains("\\")) return null;
        name = name.substring(0, name.length() - 4);
        File[] files = exeDir != null ? exeDir.listFiles() : null;
        boolean beside = false;
        if (files != null) for (File f : files) if (f.isFile() && f.getName().equalsIgnoreCase(name + ".dll")) beside = true;
        return beside && !AgvnMods.off(envVars, Collections.singletonList(name)) ? name : null;
    }

    /** The button for the DLL the problem named ({1}), or null when it is not the game's own copy. */
    static AgvnFixes.Fix fix(Activity a, Shortcut s, AgvnProblemCatalog.Finding finding) {
        File exe = finding != null ? AgvnEngineLogs.exe(s) : null;
        String dll = exe != null ? dll(exe.getParentFile(), finding.params.get("1"), s.getExtra("envVars")) : null;
        return dll != null ? new AgvnFixes.Fix(FIX, a.getString(R.string.agvn_fix_dll_wine, dll + ".dll"), dll) : null;
    }

    /** The game's {@code envVars} loading Wine's copy of {@code dll}. */
    static String apply(String envVars, String dll) {
        return AgvnMods.withBuiltin(envVars, Collections.singletonList(dll));
    }
}
