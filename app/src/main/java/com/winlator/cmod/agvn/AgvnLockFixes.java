/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.fexcore.FEXCorePresetManager;

import java.util.Locale;

/**
 * Fixes for a game whose threads lock each other out ({@link AgvnLockWatch}), offered when that shows
 * (game-problems.json "deadlock", or the player's "Game đứng hình, treo"). Each sets one variable of the game's own
 * environment, which comes after its container's and its FEX preset's:
 * <ul>
 *   <li>emulator-order: FEX handles every load or store that is not aligned with full barriers, as it comes, instead of
 *   rewriting its code to "half-barrier" accesses (FEX_HALFBARRIERTSOENABLED=0). FEX warns that the rewrite "can be
 *   dangerous due to aligned loadstores through the same code now become non-atomic": a lock word read through such
 *   code is then read out of order. GameHub runs FEX so, and ran the game of the ticket of 21/10/2026 through. Its
 *   cost: code that touches unaligned data often runs slower. Only where FEX runs the game's code with x86 memory
 *   order (TSO) on, and still rewrites.</li>
 *   <li>esync-off: Wine waits and locks through its own server instead of esync (WINEESYNC=0). Its cost: a game with
 *   many threads spends more time waiting.</li>
 * </ul>
 */
final class AgvnLockFixes {
    static final String ORDER = "emulator-order", ESYNC = "esync-off";
    static final String HALF_BARRIER = "FEX_HALFBARRIERTSOENABLED", TSO = "FEX_TSOENABLED", WINEESYNC = "WINEESYNC";

    private AgvnLockFixes() {}

    /** The fix {@code id} for {@code s}, or null when it is not one of these or would change nothing. */
    static AgvnFixes.Fix make(Context a, Shortcut s, String id) {
        switch (id) {
            case ORDER: {
                String preset = s.getExtra("fexcorePreset", s.container.getFEXCorePreset());
                EnvVars fex = FEXCorePresetManager.getEnvVars(a, preset);
                fex.putAll(s.container.getEnvVars());
                fex.putAll(s.getExtra("envVars"));
                String emulator = s.getExtra("emulator", s.container.getEmulator());
                boolean fexRuns = runsOnFex(s.container.getWineVersion(), emulator);
                return fexRuns && halfBarriers(fex)
                        ? new AgvnFixes.Fix(id, a.getString(R.string.agvn_fix_emulator_order), "0") : null;
            }
            case ESYNC: {
                EnvVars env = new EnvVars(s.container.getEnvVars());
                env.putAll(s.getExtra("envVars"));
                return "0".equals(env.get(WINEESYNC)) ? null
                        : new AgvnFixes.Fix(id, a.getString(R.string.agvn_fix_esync_off), "0");
            }
            default:
                return null;
        }
    }

    /** Does {@code fix} on the game's environment; false when it is not one of these. The caller saves. */
    static boolean apply(Shortcut s, AgvnFixes.Fix fix) {
        String name = ORDER.equals(fix.id) ? HALF_BARRIER : ESYNC.equals(fix.id) ? WINEESYNC : null;
        if (name == null) return false;
        s.putExtra("envVars", withVar(s.getExtra("envVars"), name, fix.to));
        return true;
    }

    /** {@code env} with {@code name}={@code value}, its other variables kept in their order. */
    static String withVar(String env, String name, String value) {
        EnvVars vars = new EnvVars(env);
        vars.put(name, value);
        return vars.toString();
    }

    /** FEX runs x86-64 code under an ARM64EC Wine, and x86 (32-bit) code when it is the 32-bit emulator. */
    static boolean runsOnFex(String wineVersion, String emulator) {
        return (wineVersion != null && wineVersion.toLowerCase(Locale.ROOT).contains("arm64ec"))
                || "FEXCore".equalsIgnoreCase(emulator);
    }

    /** FEX keeps x86 memory order (TSO) and still rewrites unaligned accesses to half barriers (both on by default). */
    static boolean halfBarriers(EnvVars fex) {
        return !"0".equals(fex.get(TSO)) && !"0".equals(fex.get(HALF_BARRIER));
    }
}
