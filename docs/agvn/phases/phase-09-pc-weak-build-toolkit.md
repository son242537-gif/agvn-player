# Phase 9: PC Weak-Build Toolkit

**Priority:** P2 · **Effort:** 6h · **Depends on:** Phase 4 (parallel-safe; no app code changes)

## Overview

Create PC-side Python scripts to analyze game texture memory footprint and generate `agvn-profile.json` files. These tools guide AGVN staff in preparing weak-optimized builds of games before distributing them to players. No changes to the app itself; outputs are profiles and weak-build game folders that phase 4 imports.

## Key Insights

1. **Real RAM Lever: PC-Side Optimization**
   - App settings like "Max Device Memory" cap VRAM but don't save phone RAM (GPU uses shared memory)
   - Real savings come from: reducing texture resolution, downscaling atlases, removing LOD levels (all PC-side operations)
   - Each game engine handles optimization differently (UE pak repacking, Unity bundle compression, etc.)

2. **Measurement Approach**
   - Textures consume GPU memory = width × height × bytes-per-pixel × (1 + mipmaps overhead)
   - Largest assets are typically textures, not code or audio
   - Python scripts measure total GPU memory per texture and suggest top candidates for reduction

3. **Tools Already Proven**
   - Existing scripts in shared folders handle UE pak unpacking, texture scaling, Spine atlas optimization, Unity bundle analysis
   - Do not duplicate; import and reuse logic via `sys.path`

## Requirements

**Functional:**
1. `do-bo-nho-anh.py <game_dir>` → detects engine, sums GPU memory per texture, outputs CSV and summary
2. `tao-agvn-profile.py <game_dir> [--ram-mb N] [--fps 30] [--weak-fps 24] [--screen WIDTHxHEIGHT] [--pool MB --weak-pool MB]` → generates `agvn-profile.json` with exe auto-detected
3. `README-lam-nhe.md` (≤150 lines) → per-engine recipe index, QC gates (PSNR ≥35 dB for downscaled art), measurement routine

**Non-Functional:**
- Scripts ≤200 lines each; stream large files (no whole-pak loads into memory)
- No network calls; no unknown binaries (never auto-download repak.exe)
- Validation: profile schema matches P4 validator (shared fixture)

## Implementation Steps

1. **Create `do-bo-nho-anh.py`:**
   - Detect game engine: look for UE pak files, Unity data folders, Godot PCK, etc.
   - Engine-specific analyzer:
     - **UE:** open pak file; iterate textures; sum width × height × bpp × mip_overhead
     - **Unity:** scan `.assets` files via UnityPy; sum texture GPU bytes
   - Output CSV: texture name, dimensions, GPU MB, compression ratio
   - Summary: total GPU memory, top 30 largest, estimated savings at ½ resolution

2. **Create `tao-agvn-profile.py`:**
   - Argument parsing: game folder, optional RAM/FPS/resolution overrides, pool defaults
   - Call `GameExeResolver` logic (replicate validation rules from P4):
     - Detect UE Shipping vs Debug (prefer Shipping)
     - Detect Unity executable path
     - Detect Godot PCK or GameMaker exe
   - Generate `agvn-profile.json`:
     - Set exe, workingDir, args, env from defaults
     - Set FPS cap, resolution, texture pool from args or device tier
     - Save to `<game_dir>/agvn-profile.json`
   - Validate output via P4 schema (throw on invalid)

3. **Create `README-lam-nhe.md`:**
   - Per-engine sections:
     - **UE:** pak unpacking tools, Spine atlas crops, effect resolution halving, pak zlib recompression, texture pool via profile
     - **Unity:** quality level downgrades, crunch compression to ½ res, boot.config gfx tweaks, 720p lock via args
     - **GameMaker:** scale assets downward, check YYC shader optimization
   - QC gates:
     - Downscaled textures: PSNR ≥35 dB vs originals (use `compare` from ImageMagick)
     - Crunch header validation: `va-header-crn.py` must output "va 0"
     - No broken file references (parse pak/bundle for missing assets)
   - Measurement routine (on POCO after weak build import):
     - `do-tai.sh`: measure MemAvailable, FPS, temps before/after game update
     - Adjust texture pool in profile if baseline RAM is lower than expected

4. **Create test fixtures:**
   - Minimal UE pak structure (fake pak with 2–3 texture entries)
   - Minimal Unity project with a few .assets files
   - Validate scripts parse them correctly

5. **Validate on known games:**
   - AV Director (UE5): measure ~2.5 GB total; run `do-bo-nho-anh.py` → output within ±10%
   - Legend Cleaner (UE): expected ~5.7 GB; run both scripts; verify profile accepted by P4 importer
   - NTR Soccer (Unity): expected ~35 GB GPU total; memory stream test to ensure no blowup

## Success Criteria

1. `do-bo-nho-anh.py` totals within ±10% of known figures; processes large games in <2 GB RAM
2. `tao-agvn-profile.py` generates profile for AV Director with Shipping exe and weak-device pool defaults
3. Generated profile imported on device (P4) via "Thêm game AGVN"; logcat shows "AGVN import: added"
4. README lists all tool paths and QC gates; README itself is ≤150 lines

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|-----------|
| Python exe detection rules drift from Java (P4) | Medium | Low | Document rules once in README; shared fixture folder names |
| Python parser RAM blowup on huge games | Medium | Medium | Streaming reads; test on NTR Soccer (35 GB game) |
| User expectation: "one-button compression" | High | Medium | README + maintainer briefing: per-engine, semi-automatic, QC required |

## No Device Test

This phase is PC-side only. Validation: generated profiles accepted by P4 importer on device.

## Next Steps

Phase 10 depends on phases 1–9 completing. Profiles from this toolkit are used in P10 field testing.
