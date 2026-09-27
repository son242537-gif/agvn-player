# Phase 5: Device Tiering & Auto Presets

**Priority:** P1 · **Effort:** 6h · **Depends on:** Phase 4

## Overview

Detect device capabilities (SoC model, GPU type, RAM) and assign a tier: Flagship (high-end), Trung bình (mid-range), or Yếu (weak). Weak tier auto-applies a conservative preset (24 FPS cap, 854×480 resolution, small texture pool) at import. Users can manually override tier per game or device-wide.

## Requirements

**Functional:**
1. Tier detection: parse `ro.soc.model`, `ro.hardware`, GPU renderer string; assign Flagship/Trung bình/Yếu
2. Weak tier profile: FPS cap 24, resolution 854×480, texture pool 512 MB, DXVK conservative settings
3. UI: Settings row "Chế độ thiết bị" showing current tier; dropdown to override; preview shows resulting FPS/resolution
4. Per-game override: shortcut settings allow tier override (weak → standard for that game only)
5. Profile inheritance: weak-device profile fields override global tier settings
6. Diagnostic info: include detected tier in crash export zip (phase 3)

**Non-Functional:**
- Tier detection rules are data-driven (JSON file, not hardcoded)
- Support future SoC additions without code changes
- Settings stored in SharedPreferences per device and per shortcut

## Tier Thresholds

| Tier | RAM | GPU Examples | FPS Cap | Resolution | Texture Pool |
|------|-----|--------------|---------|------------|--------------|
| Flagship | ≥8 GB | Adreno 8xx, Mali G9x | 30 | 1280×720 | 1024+ MB |
| Trung bình | 6–8 GB | Adreno 7xx, Mali G7x | 27 | 960×544 | 768 MB |
| Yếu | <6 GB | Adreno 6xx, Mali G5x | 24 | 854×480 | 512 MB |

## Implementation Steps

1. **Create `DeviceTier.java` (enum + detector):**
   - Enum: FLAGSHIP, TRUNG_BINH, YEU
   - Static `detect(Context ctx)` → parses Build properties and GPU renderer
   - Stores result in SharedPreferences with TTL (cache for session)

2. **Create `device-tiers.json` in assets:**
   ```json
   {
     "socs": [
       {"pattern": "Snapdragon 8 (Gen 3|3 Leading)", "tier": "FLAGSHIP"},
       {"pattern": "Snapdragon 8.*Elite", "tier": "FLAGSHIP"},
       {"pattern": "Snapdragon [67][0-9][0-9]", "tier": "TRUNG_BINH"},
       {"pattern": "Snapdragon [456][0-9][0-9]", "tier": "YEU"},
       {"pattern": "Exynos [0-9]{4}", "tier": "TRUNG_BINH"}
     ],
     "gpus": [
       {"pattern": "Adreno .*8[0-9][0-9]", "tier": "FLAGSHIP"},
       {"pattern": "Adreno .*[67][0-9][0-9]", "tier": "TRUNG_BINH"},
       {"pattern": "Adreno .*6[0-9][0-9]", "tier": "YEU"},
       {"pattern": "Mali.*G9", "tier": "FLAGSHIP"}
     ],
     "presets": {
       "FLAGSHIP": {"fps": 30, "resolution": "1280x720", "texturePool": 1024},
       "TRUNG_BINH": {"fps": 27, "resolution": "960x544", "texturePool": 768},
       "YEU": {"fps": 24, "resolution": "854x480", "texturePool": 512}
     }
   }
   ```

3. **Modify profile import (phase 4 continuation):**
   - At import time, if game tier not specified → detect device tier
   - If device is YEU, override profile FPS/resolution unless profile explicitly specifies them

4. **Create settings UI row:**
   - SettingsFragment (or Compose host): "Chế độ thiết bị" dropdown
   - Shows detected tier + option to override manually
   - Clicking override → tier-selection dialog
   - Preview text: "Giới hạn FPS: 24, Độ phân giải: 854×480"

5. **Modify shortcut launch flow:**
   - ProfileManager.applyTier() → read tier pref; override profile FPS/resolution if needed
   - Merge: device tier < game profile < per-game override

6. **Add tier to crash export (phase 3 integration):**
   - `DiagnosticsExporter.java` reads tier from SharedPreferences; include in `thiet-bi.txt`

7. **Unit tests:**
   - Tier detection: parse SoC/GPU strings correctly
   - Preset selection: weak device + game profile → resulting FPS/resolution correct
   - Override: manual tier selection overrides auto-detection

## Success Criteria

1. Device with Snapdragon 6xx or Adreno 650 → auto-detected as Yếu
2. Settings UI shows current tier; dropdown allows override
3. Override to Yếu → 24 FPS and 854×480 auto-applied to next imported game
4. Per-game override: weak game set to Standard tier → uses 30 FPS (not 24) for that shortcut only
5. Crash export includes detected tier in `thiet-bi.txt`
6. Unit tests pass

## Device Test Checklist

- [ ] Check device SoC model: `adb shell getprop ro.soc.model`
- [ ] Launch app → Settings show detected tier (should match device specs)
- [ ] Override tier to Yếu → import game → verify 24 FPS preset in game settings
- [ ] Per-game override: set weak-tier game to Standard → FPS changes to 30 in shortcut settings

## Next Steps

Phase 6 adds thermal and RAM safeguards that monitor game performance at runtime.
