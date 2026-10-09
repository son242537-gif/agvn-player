/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.core.KeyValueSet;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The DXVK version of the first game environment, by what the phone's Vulkan can do. DXVK 2.x needs Vulkan 1.3. With
 * Turnip (Adreno 6xx and newer) every phone has it. Without Turnip the games use the phone's own Vulkan driver, and one
 * older than 1.3 (Adreno 5xx, PowerVR GE8xxx, the first Xclipse) gets DXVK 1.10.3, which needs only Vulkan 1.1. Mali
 * keeps 1.10.3, as upstream chose; a version the phone does not tell keeps 2.3.1, as before. Pure Java except
 * {@link #apply} and {@link #systemVulkan}.
 */
public final class AgvnDxvkPick {
    static final String NEW = "2.3.1", OLD = "1.10.3";

    private AgvnDxvkPick() {}

    /** {@code systemVulkan}: the phone driver's Vulkan as VK_MAKE_API_VERSION packs it, 0 when unknown. */
    static String version(String gpu, boolean turnip, int systemVulkan) {
        if (turnip) return NEW;
        if (gpu != null && gpu.contains("Mali")) return OLD;
        return systemVulkan <= 0 || atLeast(systemVulkan, 1, 3) ? NEW : OLD;
    }

    static boolean atLeast(int packed, int major, int minor) {
        int ma = (packed >>> 22) & 0x7F, mi = (packed >>> 12) & 0x3FF;
        return ma > major || ma == major && mi >= minor;
    }

    /** "1.3" for a packed Vulkan version, "?" for 0. */
    static String name(int packed) {
        return packed <= 0 ? "?" : ((packed >>> 22) & 0x7F) + "." + ((packed >>> 12) & 0x3FF);
    }

    /**
     * True for any DXVK-Sarek build, also one a player installed ("Sarek-1.11.0-async"), not only upstream's
     * "1.11.1-sarek". The wrapper rewrites each shader as the game creates it; upstream turns its OpConstantComposite pass
     * off for Sarek's shaders (WRAPPER_NO_PATCH_OPCONSTCOMP). A Mali phone with an installed Sarek, the pass on, crashed
     * at that very step: "Assertion failed! ... vkCreateShaderModule".
     */
    public static boolean isSarek(String version) {
        return version != null && version.toLowerCase(java.util.Locale.ROOT).contains("sarek");
    }

    /**
     * At a game's start: DXVK 2.x on the phone's own driver ("System") older than Vulkan 1.3 cannot create a DirectX
     * device, and the game then says DirectX 11 is missing. That start uses 1.10.3 instead. Turnip and other drivers,
     * and every other version, stay as chosen. Games set up before {@link #apply} existed keep 2.3.1 otherwise.
     */
    static String forLaunch(String version, String driver, int systemVulkan) {
        if (version == null || !version.startsWith("2.") || !"System".equalsIgnoreCase(driver)) return version;
        if (systemVulkan <= 0 || atLeast(systemVulkan, 1, 3)) return version;
        return version.contains("arm64ec") ? OLD + "-arm64ec-async" : OLD;
    }

    /**
     * True when {@code a} and {@code b} start as the same DXVK ({@link #forLaunch}): choosing one for the other changes
     * nothing. A Galaxy M34's driver (Mali-G68, Vulkan 1.1) was offered DXVK 2.3.1, and each start ran 1.10.3 (09/10).
     */
    static boolean sameAtStart(String a, String b, String driver, int systemVulkan) {
        return java.util.Objects.equals(forLaunch(a, driver, systemVulkan), forLaunch(b, driver, systemVulkan));
    }

    /** {@link #forLaunch} on a start's DXVK settings; {@code driver} is the Vulkan driver the start uses. */
    public static void fitLaunch(Context context, KeyValueSet dxwrapperConfig, String driver) {
        String version = dxwrapperConfig.get("version");
        int vulkan = systemVulkan(context);
        String fitted = forLaunch(version, driver, vulkan);
        if (fitted == null || fitted.equals(version)) return;
        dxwrapperConfig.put("version", fitted);
        Log.i("AGVN", "DXVK " + version + " needs Vulkan 1.3, the phone's driver has " + name(vulkan) + ": using " + fitted);
    }

    /** {@code config} ("version=2.3.1,framerate=0,...") with its DXVK version set to {@code version}. */
    static String withVersion(String config, String version) {
        String[] items = (config != null ? config : "").split(",", -1);
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        for (String item : items) {
            if (sb.length() > 0) sb.append(',');
            if (item.startsWith("version=")) {
                sb.append("version=").append(version);
                found = true;
            } else {
                sb.append(item);
            }
        }
        if (!found) sb.insert(0, "version=" + version + (sb.length() > 0 ? "," : ""));
        return sb.toString();
    }

    /** After the graphics driver is chosen ("freedreno" means Turnip): the DXVK this phone can run. */
    static void apply(Context context, JSONObject data) throws JSONException {
        boolean turnip = "freedreno".equals(data.optString("graphicsDriver"));
        String gpu = DriverSafety.getGpuRenderer(context);
        String chosen = version(gpu, turnip, turnip ? 0 : systemVulkan(context));
        data.put("dxwrapperConfig", withVersion(data.optString("dxwrapperConfig", Container.DEFAULT_DXWRAPPERCONFIG), chosen));
    }

    /**
     * The Vulkan version the phone declares (android.hardware.vulkan.version), which Android requires to match its
     * driver; 0 when it declares none. No driver is loaded to read it, so every process may ask.
     */
    static int systemVulkan(Context context) {
        try {
            for (FeatureInfo f : context.getPackageManager().getSystemAvailableFeatures()) {
                if (PackageManager.FEATURE_VULKAN_HARDWARE_VERSION.equals(f.name)) return f.version;
            }
        } catch (RuntimeException ignored) {
            // no answer: unknown
        }
        return 0;
    }
}
