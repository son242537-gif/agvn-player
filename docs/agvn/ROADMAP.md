# AGVN Player — Roadmap & Phases

**Goal:** Transform Winlator-Ludashi into a weak-phone optimized emulation environment with Vietnamese UI and one-tap game import.

## Phase Overview

| Phase | Name | Dependencies | Effort | Status |
|-------|------|--------------|--------|--------|
| 1 | Baseline build (unmodified Ludashi) | — | 4h | ⏸ Local-only setup |
| 2 | App identity & signing | P1 | 4h | ✅ Merged (#1), device test pending |
| 3 | Crash safety & diagnostics | P2 | 4h | ✅ Merged (#2), device test pending |
| 4 | AGVN game import profile | P3 | 8h | ✅ Merged (#3), device test pending |
| 5 | Device tiering & auto presets | P4 | 6h | ✅ Merged (#4), device test pending |
| 6 | In-game thermal & RAM guards | P5 | 4h | ✅ Merged (#5), device test pending |
| 7 | Pre-launch RAM check & UE texture pool | P6 | 5h | ✅ Merged (#6), device test pending |
| 8 | Vietnamese UI & AGVN branding | P2, P7 | 10h | ✅ Merged (#7), device test pending |
| 9 | PC weak-build toolkit | P4 | 6h | ✅ Merged (#9), needs real game files to calibrate |
| 10 | Release candidate & field test | All | 4h | 🔶 Tooling/docs merged; signing, device, AV and field tests pending (maintainer) |

**Total estimated effort:** 55 hours  
**Sequential path:** P1→P2→P3→P4→P5→P6→P7 (shared code files)  
**Parallel opportunity:** P8 after P2; P9 after P4 (touches no app code)

## Progress after the 10 phases (as of 2026-10-02)

**Releases** (published on GitHub Releases, signed by the maintainer):

| Version | Code | Built from | Notes |
|---------|------|------------|-------|
| 0.1.0–0.1.2 | 2–4 | `agvn/main` | Release notes in `docs/agvn/release/` |
| 0.1.4 | 6 | `agvn/p43-release-0.1.3` (tag `v0.1.4`) | In-app update, MF video, saves, settings fixes, frame pacing, RAM/heat warnings, multi-game folders |
| 0.1.5 | 7 | `agvn/p43-release-0.1.3` (tag `v0.1.5`) | Broken game-file check before launch, RPG Maker MV sound in the HTML runner |

**`agvn/main` is behind the published app:** it stops at #36 (0.1.2, Zink Mesa 25.1.9 from #34 merged).
Everything in 0.1.4/0.1.5 lives only on the stacked branches `agvn/p28` … `agvn/p48`, merged together into
`agvn/p43-release-0.1.3`. Merging that release branch (or the stack #37–#51 in order) into `agvn/main` is the
next housekeeping step, so later work does not branch from a base two releases old.

| Work | PR / branch | Status |
|------|-------------|--------|
| Graphics steps for every engine, saves, setting help, two-finger right click, settings fixes, in-app update, Japanese fonts, BCn under Turnip, game logs, RAM watch, MF video, splash, frame pacing, heat warning | #37, #38, #40–#51 (`agvn/p28`–`p41`) | 🔶 Shipped in 0.1.4; PRs still draft, not in `agvn/main` |
| Tap hold, multi-game folders, broken game files, HTML runner sound | `agvn/p45`–`p48` (no PR) | 🔶 Shipped in 0.1.4/0.1.5 via `agvn/p43-release-0.1.3` |
| DDraw wrapper: restore Wine's ddraw | #39 `agvn/fix-ddraw-restore` | ⏳ Open, not released |
| CLAUDE.md rule: fix for every game, never one game | #52 `agvn/p42-every-game-rule` | ⏳ Open |
| Game main thread on fast cores + perf hints | `agvn/p44-game-cpu` (no PR) | 🚧 In progress, on top of 0.1.5 |
| App under GPL-3.0-or-later; plan for native Ren'Py / RPG Maker XP–VX Ace | `agvn/p49-gpl-license` (no PR) | 🚧 Needs maintainer decision (licence change) |
| Ren'Py 8 games run natively (Ren'Py 8.5.3 Android) | `agvn/p50-renpy8` (on p49, no PR) | 🚧 In progress |
| Keep games alive; tell a crash from a normal exit | `agvn/p53-game-session` (on p50, no PR) | 🚧 In progress |

Still open from the hand-off (`handoff-pc.md`): OpenGL games flicker black once a second at low settings (Zink
abort fixed by #34; flicker cause unknown), Dimensity/Mali checks on a real phone, and the v0.1.0 RC report
(`rc-test-report.md`) and weak-phone field test have not been filled in.

---

## Implementation notes (deviations decided during the build)

- P2: a missing `keystore.properties` does not fail the default release build (cloud/test builds are debug-signed with a warning); `-PagvnRequireReleaseSigning=true` makes it fatal for real releases.
- P6: temperature comes from battery temperature + `PowerManager` thermal status/headroom (no `dumpsys`, which needs the DUMP permission); raw CPU/GPU 45 °C is the normal steady state on the Adreno 830 and is not used as a trigger.
- P7: no "Sạch RAM" button (Android 14+ lets an app kill only its own processes; AGVN never kills other apps); Unreal overrides go to the per-user `Saved/Config/Windows*/Engine.ini` in the Wine prefix with `r.Streaming.PoolSize`.
- Simulated Touchscreen is on by default for imported games and for games added from the file manager.

## Quick Phase Summaries

| Phase | Summary |
|-------|---------|
| 1 | Baseline build (unmodified Ludashi @ b8048ac, local-only setup) |
| 2 | App identity (`com.agvn.player`), signing, pinned build artifacts |
| 3 | Crash safety (driver fallback, unusual driver detection), one-tap diagnostic export |
| 4 | AGVN profile schema (exe/args/env/FPS/resolution/texture pool), game import via "Thêm game AGVN" |
| 5 | Device tier detection (SoC/GPU), auto-apply weak-phone presets (24 FPS, 854×480, 512 MB pool) |
| 6 | In-game thermal monitor (warn >45°C), RAM guard (warn <300 MB free) |
| 7 | Pre-launch RAM check, UE Engine.ini texture pool config |
| 8 | Vietnamese UI on 8 customer-facing screens, AGVN branding (icon, splash, About, licenses) |
| 9 | PC toolkit: measure textures, generate profiles, QC gates (PSNR, header validation) |
| 10 | RC build, test matrix, antivirus scan, Vietnamese install guide, beta field test on 2–3 weak phones |

---

## Dependency Graph

```
P1 (local)
  └─→ P2 (app identity)
      ├─→ P3 (crash safety)
      │   └─→ P4 (game import)
      │       ├─→ P5 (device tiers)
      │       │   └─→ P6 (thermal/RAM)
      │       │       └─→ P7 (pre-launch)
      │       └─→ P9 (PC toolkit)
      └─→ P8 (branding/i18n) ← waits for P7 to merge (Kotlin sweep)
            └─→ P10 (RC & test) ← depends on P2-P9 completion
```

## How to Contribute

1. **Claim a phase:** Pick the next unblocked phase from the roadmap.
2. **Read the phase file:** Open `docs/agvn/phases/phase-NN-<slug>.md` for detailed requirements, code files, and success criteria.
3. **Implement on a branch:** Create `agvn/pNN-<slug>` based on `agvn/main`; one phase per branch.
4. **Include device test checklist:** PR description lists all manual tests + expected results.
5. **Merge to `agvn/main`:** Maintainer reviews, runs device tests, merges.

## Key Measurements & Targets

From device testing on a Snapdragon 8 Elite / Adreno 830 with a UE5 DX11 game:
- **Baseline (1280×720):** 29.7 FPS, 1.5 GB RAM, 46°C GPU
- **Weak tier (960×544):** −16% GPU busy (not a 2× speedup; fixed costs dominate)
- **Thermal limit:** Sustained 45°C OK; >50°C requires user intervention
- **RAM available:** Start with >1.5 GB free for safe launch (device-dependent)

**Real lever for weak phones:** Correct .exe + working GPU driver (P3/P4) and texture/resolution reduction done on PC (P9), then applied via tier selection (P5) or user override (P5).

See [`device-findings.md`](./device-findings.md) for detailed numbers and test procedures.
