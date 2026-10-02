/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import androidx.core.content.ContextCompat;

import com.winlator.cmod.box64.Box64Preset;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.OpenGLDriverDefaults;
import com.winlator.cmod.core.WineInfo;
import com.winlator.cmod.fexcore.FEXCorePreset;

import org.json.JSONObject;

/** Settings of the first game environment, and the storage check used by the first start. */
public final class AgvnFirstContainer {
    private AgvnFirstContainer() {}

    /**
     * Same defaults as the upstream first start, with the debug overlays off, the OpenGL driver pre-set and the DXVK
     * version the phone's Vulkan can run ({@link AgvnDxvkPick}).
     */
    static JSONObject data(Context context, ContentsManager contents, int id) throws Exception {
        String runtime = WineInfo.MAIN_WINE_VERSION.identifier();
        WineInfo wineInfo = WineInfo.fromIdentifier(context, contents, runtime);
        if (wineInfo.path == null || wineInfo.path.isEmpty()) throw new IllegalStateException("bundled Proton missing");
        EnvVars env = new EnvVars(Container.DEFAULT_ENV_VARS);
        env.remove("DXVK_HUD");
        env.remove("TU_DEBUG");

        JSONObject data = new JSONObject();
        data.put("name", "Container-" + id);
        data.put("screenSize", Container.DEFAULT_SCREEN_SIZE);
        data.put("envVars", env.toString());
        data.put("graphicsDriver", Container.DEFAULT_GRAPHICS_DRIVER);
        data.put("graphicsDriverConfig", Container.DEFAULT_GRAPHICSDRIVERCONFIG);
        data.put("rendererNative", false);
        data.put("rendererPresentMode", "fifo");
        data.put("dxwrapper", Container.DEFAULT_DXWRAPPER);
        data.put("dxwrapperConfig", Container.DEFAULT_DXWRAPPERCONFIG);
        data.put("audioDriver", Container.DEFAULT_AUDIO_DRIVER);
        data.put("emulator", wineInfo.isArm64EC() ? "FEXCore" : "Box64");
        data.put("wincomponents", Container.DEFAULT_WINCOMPONENTS);
        data.put("drives", Container.DEFAULT_DRIVES);
        data.put("box64Version", wineInfo.isArm64EC() ? DefaultVersion.WOWBOX64 : DefaultVersion.BOX64);
        data.put("box64Preset", Box64Preset.COMPATIBILITY);
        data.put("fexcoreVersion", DefaultVersion.FEXCORE);
        data.put("fexcorePreset", FEXCorePreset.INTERMEDIATE);
        data.put("wineVersion", runtime);
        OpenGLDriverDefaults.initialize(context, data);
        try {
            AgvnDxvkPick.apply(context, data); // DXVK by what the phone's Vulkan can run
        } catch (Exception e) {
            data.put("dxwrapperConfig", Container.DEFAULT_DXWRAPPERCONFIG);
        }
        return data;
    }

    /** True when the app may read game folders in shared storage. */
    public static boolean hasStorageAccess(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return Environment.isExternalStorageManager();
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }
}
