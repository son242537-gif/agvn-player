# Phase 6: In-Game Thermal & RAM Guards

**Priority:** P1 · **Effort:** 4h · **Depends on:** Phase 5

## Overview

Monitor CPU/GPU temperature and available RAM during gameplay. If the device gets too hot (>45°C sustained) or RAM drops below 300 MB, show a gentle warning dialog giving the player 30 seconds to save before forcing app shutdown or severe throttling.

## Key Technical Insights

1. **Temperature Monitoring**
   - Read via `dumpsys thermalservice` (no root needed, no new permissions)
   - Qualcomm devices have thermal zones: `Skin`, `CPU`, `GPU` temperatures
   - Safe limit: sustained 45°C (keep headroom before OS throttles)
   - At >50°C, expect CPU frequency cap or app force-close

2. **RAM Monitoring**
   - Use `ActivityManager.MemoryInfo.availMem` for device-wide available RAM
   - Weak devices typically have 4–8 GB total; safe launch needs 1.5+ GB free
   - Below 300 MB free → system is likely to kill background apps; game at risk

3. **Non-Intrusive Approach**
   - Show warning, don't force-close immediately
   - Dialog: "Thiết bị nóng. Tắt để lưu game trước khi tắt nguồn."
   - User has 30 s to close the dialog or reach a save point
   - If ignored, app can reduce FPS or other graceful throttling (future)

## Requirements

**Functional:**
1. Background monitor thread (or WorkManager) polls temperature every 2 s during gameplay
2. Dialog if CPU >45°C or GPU >45°C sustained for 3+ consecutive polls
3. Dialog if available RAM <300 MB
4. Dialog shows warning + countdown (30 s) + [OK] button
5. Save gameplay before forcing exit or throttle

**Non-Functional:**
- Thermal monitoring requires no new Android permissions
- Works on phones without thermalservice (graceful skip)
- Does not interfere with game input or rendering

## Implementation Steps

1. **Create `ThermalMonitor.java`:**
   - Static methods to read temps via `dumpsys thermalservice`
   - Parse output for Skin/CPU/GPU zones
   - Return map {zone: temperature_C}
   - Catch and log if unavailable

2. **Create `RamGuard.java`:**
   - `isLowMemory(Context ctx)` → check `ActivityManager.MemoryInfo`
   - `getRequiredRam(agvnProfile)` → estimate min safe RAM for game (default 1.5 GB)
   - Used at pre-launch (P7) and during gameplay

3. **Create `SessionGuard.java`:**
   - `decide(thermalMap, availRam)` → decision enum (OK, WARN_THERMAL, WARN_LOW_RAM)
   - Accumulate samples; only warn after 3 consecutive high readings

4. **Modify `XServerDisplayActivity.java`:**
   - Start monitoring thread when game launches (in `onCreate` or `onResume`)
   - Poll every 2 s; accumulate readings
   - On decision change, post UI dialog via Handler

5. **Create UI dialog:**
   - AlertDialog with countdown timer
   - Vietnamese text
   - [OK] to dismiss (user saves manually)
   - Auto-dismiss after 30 s (optional: trigger shutdown)

6. **Unit tests:**
   - Thermal sample accumulation: 1 high reading → no alert; 3 high → alert
   - RAM calculation: profile texture pool + baseline emulator → required RAM
   - Decision logic: OK, WARN_THERMAL, WARN_LOW_RAM cases

## Success Criteria

1. Playing a game on maintainer device, monitor reaches >45°C → warning dialog appears
2. Dialog shows countdown; user can dismiss or wait 30 s
3. Available RAM drops to <300 MB → warning appears
4. Thermal and RAM warnings do not overlap (first one shown first)
5. Unit tests pass

## Device Test Checklist

- [ ] Trigger thermal warning: play game for 5+ min, measure `dumpsys thermalservice`
- [ ] Verify dialog appears at >45°C threshold
- [ ] Dialog countdown works, dismisses after 30 s
- [ ] Test low-RAM warning: override available memory, verify warning triggers

## Next Steps

Phase 7 adds pre-launch checks before allowing game start.
