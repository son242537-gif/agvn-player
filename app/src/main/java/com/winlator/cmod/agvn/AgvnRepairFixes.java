/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.EnvVars;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The fixes "Tự sửa lỗi" tries for what the player reports ({@link AgvnRepair}), besides those of {@link AgvnFixes}.
 * Each changes one setting of the game's own, and each is made only where it changes something:
 * <ul>
 *   <li>render-sysmem, turnip-nolrz, turnip-noubwc: Turnip draws whole frames in memory, without LRZ (its early depth
 *   test) or without UBWC (its compressed images), known causes of flicker, black spots and wrong colours on Adreno
 *   (TU_DEBUG);</li>
 *   <li>async-off: DXVK makes a pipeline before it draws with it, so nothing shows half made (async=0);</li>
 *   <li>present-sync: "Đồng bộ khung hình" and "Tắt Present Wait" ({@link AgvnPresentSync});</li>
 *   <li>bcn-full: the wrapper unpacks every BCn texture, for a driver that reads none (Turnip reads them);</li>
 *   <li>unity-quality-own: a Unity game keeps its own quality where Đồ họa Thấp or Siêu nhẹ set its lowest
 *   ({@link AgvnUnityQuality});</li>
 *   <li>audio-other: PulseAudio or ALSA;</li>
 *   <li>locale-ja, locale-zh: the language Windows runs the game in, which its text needs (LC_ALL);</li>
 *   <li>mods-off: the game without its mod loader, through Wine's own copy of the loader's DLL ({@link AgvnMods});</li>
 *   <li>raw-mouse: touches as a real mouse, raw input included, or back to the X server's pointer events for a game
 *   that has them so ({@link AgvnRawMouse}).</li>
 * </ul>
 * Pure Java except {@link #make}.
 */
final class AgvnRepairFixes {
    static final List<String> IDS = Arrays.asList("render-sysmem", "turnip-nolrz", "turnip-noubwc", "async-off",
            "present-sync", "bcn-full", "unity-quality-own", "audio-other", "locale-ja", "locale-zh", "mods-off",
            "raw-mouse");
    static final String CHINESE = "zh_CN.UTF-8", PULSE = "pulse-audio-gn", ALSA = "alsa";

    private AgvnRepairFixes() {}

    /** The fix {@code id} for {@code s}, or null when it is not one of {@link #IDS} or would change nothing. */
    static AgvnFixes.Fix make(Context a, Shortcut s, String id) {
        switch (id) {
            case "render-sysmem":
            case "turnip-nolrz":
            case "turnip-noubwc": {
                String flag = flag(id);
                boolean on = AgvnFixEdits.renderFlags(s.getExtra("envVars"), AgvnFixes.inheritedTuDebug(s))
                        .contains(flag);
                return AgvnFixes.turnip(a, s) && !on ? fix(a, id, label(id), flag) : null;
            }
            case "async-off":
                return AgvnFixes.dxvk(s) && "1".equals(AgvnFixEdits.configValue(AgvnFixes.dxvkConfig(s), "async", ','))
                        ? fix(a, id, R.string.agvn_fix_async_off, "0") : null;
            case "present-sync":
                return AgvnPresentSync.offerable(s) ? fix(a, id, R.string.agvn_black_sync, "1") : null;
            case "bcn-full":
                return AgvnFixes.dxvk(s) && !AgvnFixes.turnip(a, s)
                        && !"full".equals(AgvnFixEdits.configValue(AgvnFixes.driverConfig(s), "bcnEmulation", ';'))
                        ? fix(a, id, R.string.agvn_fix_bcn_full, "full") : null;
            case "unity-quality-own":
                return AgvnUnityQuality.forced(s) ? fix(a, id, R.string.agvn_fix_unity_quality_own, "1") : null;
            case "audio-other": {
                String to = otherAudio(s.getExtra("audioDriver", s.container.getAudioDriver()));
                String name = ALSA.equals(to) ? "ALSA" : "PulseAudio";
                return new AgvnFixes.Fix(id, a.getString(R.string.agvn_fix_audio, name), to);
            }
            case "locale-ja":
            case "locale-zh": {
                String to = id.equals("locale-ja") ? AgvnLocale.JAPANESE : CHINESE;
                return to.equals(s.getExtra("lc_all")) ? null : fix(a, id, label(id), to);
            }
            case "mods-off": {
                Map<String, List<String>> mods = AgvnMods.found(AgvnEngineLogs.exe(s).getParentFile());
                if (mods.isEmpty() || AgvnMods.off(s.getExtra("envVars"), AgvnMods.proxies(mods))) return null;
                String names = String.join(", ", mods.keySet());
                return new AgvnFixes.Fix(id, a.getString(R.string.agvn_fix_mods_off, names), names);
            }
            case "raw-mouse": {
                String extra = s.getExtra(AgvnRawMouse.EXTRA);
                boolean on = AgvnRawMouse.wanted(extra, extra.isEmpty() && AgvnRawMouse.inputSystem(s));
                return on ? fix(a, id, R.string.agvn_fix_raw_mouse_off, "0")
                        : fix(a, id, R.string.agvn_fix_raw_mouse, "1");
            }
            default:
                return null;
        }
    }

    /** Does {@code fix} on the game's settings; false when it is not one of {@link #IDS}. The caller saves. */
    static boolean apply(Shortcut s, AgvnFixes.Fix fix) {
        switch (fix.id) {
            case "render-sysmem":
                s.putExtra("envVars",
                        AgvnFixEdits.withRenderMode(s.getExtra("envVars"), AgvnFixes.inheritedTuDebug(s), fix.to));
                return true;
            case "turnip-nolrz":
            case "turnip-noubwc":
                s.putExtra("envVars", withTuFlag(s.getExtra("envVars"), AgvnFixes.inheritedTuDebug(s), fix.to));
                return true;
            case "async-off":
                s.putExtra("dxwrapperConfig",
                        AgvnFixEdits.withConfigValue(AgvnFixes.dxvkConfig(s), "async", fix.to, ','));
                return true;
            case "present-sync":
                s.putExtra("graphicsDriverConfig", AgvnPresentSync.withBoth(AgvnFixes.driverConfig(s)));
                return true;
            case "bcn-full":
                s.putExtra("graphicsDriverConfig",
                        AgvnFixEdits.withConfigValue(AgvnFixes.driverConfig(s), "bcnEmulation", fix.to, ';'));
                return true;
            case "unity-quality-own":
                s.putExtra(AgvnUnityQuality.EXTRA_OWN, fix.to);
                return true;
            case "audio-other":
                s.putExtra("audioDriver", fix.to);
                return true;
            case "locale-ja":
            case "locale-zh":
                s.putExtra("lc_all", fix.to);
                return true;
            case "mods-off":
                s.putExtra("envVars", AgvnMods.withBuiltin(s.getExtra("envVars"),
                        AgvnMods.proxies(AgvnMods.found(AgvnEngineLogs.exe(s).getParentFile()))));
                return true;
            case "raw-mouse":
                s.putExtra(AgvnRawMouse.EXTRA, fix.to);
                return true;
            default:
                return false;
        }
    }

    /** The game's environment with Turnip's {@code flag} on (TU_DEBUG), its other flags kept. */
    static String withTuFlag(String env, String inherited, String flag) {
        EnvVars vars = new EnvVars(env == null ? "" : env);
        List<String> flags = AgvnFixEdits.renderFlags(env, inherited);
        if (!flags.contains(flag)) flags.add(flag);
        vars.put("TU_DEBUG", String.join(",", flags));
        return vars.toString();
    }

    /** PulseAudio for a game on ALSA, else ALSA. */
    static String otherAudio(String now) {
        return ALSA.equals(now) ? PULSE : ALSA;
    }

    private static String flag(String id) {
        return id.equals("render-sysmem") ? "sysmem" : id.equals("turnip-nolrz") ? "nolrz" : "noubwc";
    }

    private static int label(String id) {
        switch (id) {
            case "render-sysmem": return R.string.agvn_fix_render_sysmem;
            case "turnip-nolrz": return R.string.agvn_fix_turnip_nolrz;
            case "turnip-noubwc": return R.string.agvn_fix_turnip_noubwc;
            case "locale-ja": return R.string.agvn_fix_locale_ja;
            default: return R.string.agvn_fix_locale_zh;
        }
    }

    private static AgvnFixes.Fix fix(Context a, String id, int label, String to) {
        return new AgvnFixes.Fix(id, a.getString(label), to);
    }
}
