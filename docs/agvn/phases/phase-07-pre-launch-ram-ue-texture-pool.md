# Phase 7: Pre-Launch RAM Check & UE Texture Pool Config

**Priority:** P1 · **Effort:** 5h · **Depends on:** Phase 6

## Overview

Before launching a game, check if the device has enough free RAM to safely load game textures. If not, offer a "Sạch RAM" button. For UE games, apply the profile's texture pool setting via `Engine.ini` overrides (only if game does not lock these settings).

## Key Technical Insights

1. **UE Texture Pool**
   - Configured via `Engine.ini` key `r.TextureStreamingPoolSize=<MB>`
   - Shipping builds typically ignore `-ExecCmds`; INI is the reliable method
   - Game folder typically has `Config/DefaultEngine.ini`; app merges AGVN overrides
   - Weak-tier games set pool to 512 MB; flagship can use 1024+ MB

2. **RAM Availability**
   - Safe launch: free RAM ≥ texture pool + baseline emulator (typically 1.5 GB)
   - Below threshold: show "Sạch RAM" button; user taps to kill background apps (killBackgroundProcesses)
   - After cleanup, user retaps [Chơi] to retry

3. **Pre-launch Dialog Flow**
   - Estimate needed RAM: profile texture pool (512–1024 MB) + overhead (~1 GB)
   - If available < needed: show "Sạch RAM" button + estimated time message
   - User taps → call `ActivityManager.killBackgroundProcesses()` for cached/background apps
   - Recheck RAM; if still low, inform user but allow launch anyway (user's choice)

## Requirements

**Functional:**
1. Pre-launch RAM check: compare available RAM vs estimated need
2. If low: dialog with [Sạch RAM] button + loading-time estimate + [Chơi] / [Bỏ qua]
3. [Sạch RAM] → `killBackgroundProcesses()` for apps in "cached" state only (safe, Android already manages them)
4. Load and merge `Engine.ini` from game folder into `Config/` before game launch
5. Apply AGVN profile texture pool via `r.TextureStreamingPoolSize` override
6. Weak-tier games auto-set pool (512 MB); user can override per-game

**Non-Functional:**
- RAM check ≤2 s; no blocking
- INI merge preserves existing settings not overridden
- Works with UE4, UE5; skip for non-UE games

## Implementation Steps

1. **Create `RamGuard.java` (expansion from P6):**
   - `getRequiredRam(agvnProfile)` → sum of texture pool + 1 GB (emulator/Wine overhead)
   - `getAvailableRam(Context ctx)` → from `MemoryInfo.availMem`
   - `needsCleanup(available, required)` → boolean
   - `killCachedApps(Context ctx)` → safe wrapper around `killBackgroundProcesses()` (only cached apps)

2. **Create `UeIniWriter.java`:**
   - `loadEngineIni(gameFolder)` → read `Config/DefaultEngine.ini`
   - `mergeOverrides(ini, profile.ueEngineIni)` → add/update keys without removing others
   - `writeEngineIni(gameFolder, ini)` → write back to temp location (or in-memory for dry-run)
   - Validation: reject keys outside `[Engine.*]` sections

3. **Modify shortcut launch flow (container/XServerDisplayActivity):**
   - Before extracting game exe, call `RamGuard.needsCleanup()` with profile texture pool
   - If true, show dialog: "RAM không đủ. Dự tính: Nạp game ~30 s. [Sạch RAM] [Chơi] [Bỏ qua]"
   - [Sạch RAM] → call `RamGuard.killCachedApps()`, recheck, update dialog
   - [Chơi] → proceed (even if low)
   - [Bỏ qua] → abort launch

4. **Apply UE INI config:**
   - After container is ready, before game exe launch:
     - Detect if UE game (from profile.exe or filename heuristic)
     - Load profile `ueEngineIni` overrides
     - Merge into game's `Config/DefaultEngine.ini`
     - Ensure `r.TextureStreamingPoolSize` set from profile

5. **Unit tests:**
   - RAM need calculation: profile pool 512 MB + 1 GB = 1.5 GB required
   - INI merge: existing keys preserved, profile keys added/updated
   - Device tier weak → pool 512 MB; flagship → pool 1024 MB

## Success Criteria

1. Import weak-tier UE game with 512 MB texture pool
2. Device free RAM <1.5 GB → pre-launch dialog shows [Sạch RAM]
3. Click [Sạch RAM] → background apps killed; recheck RAM
4. [Chơi] → game launches; `dumpsys meminfo` shows game using ~512 MB texture pool
5. Weak game launched on flagship device → pool still 512 MB (profile overrides tier default)
6. Unit tests pass

## Device Test Checklist

- [ ] Fill device memory; import weak-tier game → pre-launch dialog appears
- [ ] Click [Sạch RAM] → available RAM increases; dialog updates
- [ ] Click [Chơi] → game launches, textures load within estimated time
- [ ] Check game config: profile texture pool applied via Engine.ini

## Next Steps

Phase 8 adds Vietnamese branding and UI; phases 6–7 logic merges cleanly. Phase 9 creates PC-side profile generator that pre-sets texture pool for each game.
