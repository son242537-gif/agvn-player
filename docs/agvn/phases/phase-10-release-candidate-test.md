# Phase 10: Release Candidate & Field Test

**Priority:** P1 · **Effort:** 4h (+ field time) · **Depends on:** Phases 2–9 complete

## Overview

Build the final release candidate, run a comprehensive test matrix on the maintainer's device, scan for antivirus false positives, write a Vietnamese installation guide, and conduct field testing on 2–3 weak phones to validate the tier detection and gather real-world performance data.

## Test Matrix

| Level | What | Method | Pass Criteria |
|-------|------|--------|---|
| Unit (JVM) | AgvnProfile, GameExeResolver, DeviceTier, SessionGuard, UeIniWriter, RamGuard, DriverSafety | `gradlew testReleaseUnitTest` | 100% tests green |
| Build | Release APK signed, reproducible | `apksigner verify --certs`, `aapt2 dump badging` | Cert = pins; label = "AGVN Player"; versionCode incremented |
| Integration | Install over previous RC, data preserved | Install + adb check shortcuts | Library shows previous games, saves intact |
| Functional | Import 3 games: UE5, Unity, other | "Thêm game AGVN" on each | All reach gameplay; tap = click; exit clean |
| Functional | Driver fallback, crash export, thermal/RAM dialogs, tier override | Manual triggers (phase 3–7 criteria) | All expected behaviors observed |
| Regression | Baseline performance vs P1 | 12 samples of FPS/RAM/°C; `do-tai.sh` | FPS ≥29 avg, RSS within ±10%, °C within ±5 |
| Weak-device functional | Tier override to Yếu | Settings → re-apply; check FPS/resolution | 854×480 @ 24 FPS applied; game playable |
| Field (weak phones) | 2–3 beta phones (4–8 GB, Adreno 6xx/Mali) | Beta users: tap game, 5 min play, send diagnostic zip + 1-line feedback | ≥2 zips received; no crashes; tier rules validated |
| Security | No secrets, no AV flags | `git ls-files` gate; Windows Defender scan | No .jks/.properties in repo; APK clean |
| UX | 10-screen Vietnamese walkthrough | Maintainer visual review (phase 8 list) | No English on customer-facing screens |

## Requirements

**Functional:**
- Release APK: `AGVN-Player-<versionName>.apk` (e.g., `AGVN-Player-v0.1.0.apk`)
- Checksum file: `.sha256` with APK hash for integrity verification
- Installation guide: `HUONG-DAN-CAI-AGVN-PLAYER.txt` (Vietnamese, plain language)
  - Allow unknown sources
  - First launch unpacks ~1–2 min
  - Where to copy games: `/sdcard/AGVN/`
  - How to import: "Thêm game AGVN" button
  - How to send diagnostics: "Xuất nhật ký lỗi" in Settings
- Release notes: Vietnamese, ~10 lines (version, new features, known limitations, beta feedback channel)
- Changelog entry: `docs/project-changelog.md` records RC as v0.1.0 with date and summary

**Non-Functional:**
- Every artifact reproducible from tagged commit (e.g., `git tag v0.1.0`)
- versionCode and versionName updated in `gradle.properties` before tagging

## Tooling (added during implementation)

- `scripts/agvn/make-release.ps1` (Windows, maintainer) / `make-release.sh`: secret gate, tests, signed build, badging + certificate pin check, `release/v<ver>/` with APK, `.sha256`, install guide and release notes; the PowerShell script also runs Windows Defender.
- Fill in `docs/agvn/rc-test-report.md`; install guide and notes live in `docs/agvn/release/`.

## Implementation Steps

1. **Prepare release build:**
   - Update `gradle.properties`: increment `AGVN_VERSION_CODE`, set `AGVN_VERSION_NAME=0.1.0`
   - Commit: `chore(release): bump to v0.1.0`
   - Tag: `git tag v0.1.0`
   - Build: `./gradlew testReleaseUnitTest assembleRelease --no-daemon`
   - Verify: `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`
   - Copy APK to release folder: `release/v0.1.0/AGVN-Player-v0.1.0.apk`
   - Checksum: `sha256sum AGVN-Player-v0.1.0.apk > AGVN-Player-v0.1.0.apk.sha256`

2. **Security scan:**
   - Windows Defender (or equivalent): scan APK with `MpCmdRun.exe -Scan -ScanType 3 -File <apk>`
   - If flagged: extract APK, scan internals (native libs, dex), identify triggering asset
   - Report findings (do not bypass)

3. **Run test matrix on maintainer device:**
   - Unit tests: `gradlew testReleaseUnitTest` → record results
   - Install RC over previous build: verify data preservation
   - Import 3 test games (UE5, Unity, other): verify launch, input, exit
   - Trigger each phase feature: crash dialog, thermal warning, pre-launch RAM dialog, driver fallback, tier override
   - Measure baseline: FPS, RAM, temps (compare to P1 numbers)
   - 10-screen Vietnamese review

4. **Write documentation:**
   - `HUONG-DAN-CAI-AGVN-PLAYER.txt`: installation steps, game import, diagnostics, contact info
   - Release notes: summary of 10 phases, new features (one-tap import, weak-device presets, Vietnamese UI, crash reporting)
   - `docs/project-changelog.md`: add v0.1.0 entry with date and summary

5. **Beta distribution:**
   - Maintainer shares APK link with 2–3 beta testers on weak phones
   - Beta testers: install, import a game, play ~5 min, send diagnostic zip + 1-line feedback
   - Collect zips and analyze:
     - Device tier correctly detected?
     - FPS/RAM within expectations?
     - Any crashes or thermal issues?

6. **Tune tier rules (if needed):**
   - Analyze beta diag zips; if unexpected tier assignments or performance:
     - Update `device-tiers.json` SoC patterns or thresholds
     - Bump `AGVN_VERSION_CODE` to v0.1.1; rebuild and deploy to beta testers

7. **Mark plan complete:**
   - Update `ROADMAP.md`: all phases marked ✓ complete
   - Update `docs/project-changelog.md` with final release status

## Success Criteria

1. All test matrix rows pass (field: ≥2 diagnostic zips from weak-phone beta users)
2. Antivirus scan reports no threat (clean)
3. Upgrade from RC n to RC n+1 on device preserves game shortcuts and saves
4. RC report (commit message or document) lists FPS/RAM/°C numbers for all device tests
5. Installation guide is clear to non-technical users (Vietnamese)
6. Beta feedback: "works", "nice", or specific device limitations → tier table updated if needed

## Device Test Checklist (Maintainer)

- [ ] `gradlew testReleaseUnitTest` green; APK builds and signs
- [ ] Install RC on device; previous builds coexist; data intact
- [ ] Import UE5 test game → reaches gameplay
- [ ] Import Unity test game → reaches gameplay
- [ ] Import third game type → reaches gameplay
- [ ] FPS: ≥29 avg (vs P1 baseline 29.7)
- [ ] RAM: within ±10% of P1 baseline (1.5 GB)
- [ ] Temps: within ±5°C of P1 (CPU 50°C, GPU 46°C)
- [ ] Driver fallback: v863 → Turnip works
- [ ] Thermal warning: >45°C triggers dialog
- [ ] Pre-launch RAM dialog: [Kiểm tra lại] re-measures after closing apps (no in-app RAM cleaner, see ROADMAP notes)
- [ ] Crash export: 10 screens Vietnamese (no English)
- [ ] Settings: tier override to Yếu → FPS 24, res 854×480 applied
- [ ] Antivirus scan: clean

## Beta Testing (Field)

- [ ] Distribute APK to 2–3 weak-phone users (goal: Adreno 6xx/Mali, 4–8 GB)
- [ ] Each user imports game, plays 5 min, exports diag zip
- [ ] Collect ≥2 zips; analyze tier detection and performance
- [ ] Tune device-tiers.json if tier assignments off; rebuild v0.1.1
- [ ] Release notes and guides ready for users

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|-----------|
| AV false positive on users' devices | Medium | High | Scan before release; never ship obfuscated/packed code; keep APK debuggable=false and plain |
| Weak phones behave unlike hypothesis | High | Medium | Beta first; tier JSON + override; diag export captures reality |
| Previous data lost on update | Low | High | Test upgrade path (install RC n, then RC n+1) |
| Field testers slow or unavailable | Medium | Low | Maintainer owns fallback: override tier to Yếu manually, field-validate alone |

## Next Steps

Post-release: bug fixes ship as v0.1.x with higher versionCode; future phases (performance optimization, more engines) on roadmap.
