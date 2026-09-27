# AGVN Player — Roadmap & Phases

**Goal:** Transform Winlator-Ludashi into a weak-phone optimized emulation environment with Vietnamese UI and one-tap game import.

## Phase Overview

| Phase | Name | Dependencies | Effort | Status |
|-------|------|--------------|--------|--------|
| 1 | Baseline build (unmodified Ludashi) | — | 4h | ⏸ Local-only setup |
| 2 | App identity & signing | P1 | 4h | 📋 Pending |
| 3 | Crash safety & diagnostics | P2 | 4h | 📋 Pending |
| 4 | AGVN game import profile | P3 | 8h | 📋 Pending |
| 5 | Device tiering & auto presets | P4 | 6h | 📋 Pending |
| 6 | In-game thermal & RAM guards | P5 | 4h | 📋 Pending |
| 7 | Pre-launch RAM check & UE texture pool | P6 | 5h | 📋 Pending |
| 8 | Vietnamese UI & AGVN branding | P2, P7 | 10h | 📋 Pending |
| 9 | PC weak-build toolkit | P4 | 6h | 📋 Pending (parallel-safe) |
| 10 | Release candidate & field test | All | 4h | 📋 Pending |

**Total estimated effort:** 55 hours  
**Sequential path:** P1→P2→P3→P4→P5→P6→P7 (shared code files)  
**Parallel opportunity:** P8 after P2; P9 after P4 (touches no app code)

---

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
