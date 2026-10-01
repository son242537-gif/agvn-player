# AGVN Player — Engineering Guide for Cloud CI/CD Sessions

**Repository:** StevenMXZ/Winlator-Ludashi fork at `agvn/base` (upstream commit b8048ac).  
**Application ID:** `com.agvn.player` · **Branch:** `agvn/main` (new repo, forked after phase 1).  
**License:** MIT (upstream) + bundled GPL/LGPL libraries (Wine, proot, patchelf, Mesa); see [`docs/agvn/LICENSES.md`](./docs/agvn/LICENSES.md).

## What is AGVN Player?

A Vietnamese-first emulation environment for PC games on weak Android phones (Adreno 6xx/7xx, Mali, 6–8 GB RAM). It is Winlator-Ludashi rebrand that adds:
- One-tap AGVN game import (profile-driven configuration)
- Device-tiered presets (weak-phone optimization: 24 FPS cap, scaled resolution, RAM guards)
- Thermal & memory safeguards (prevents thermal throttle & "Out of Memory" force-close)
- Vietnamese UI for non-technical players
- AGVN branding + credit (agvn.io.vn only; no ads, telemetry, or third-party accounts)

**Goal:** Every AGVN game runs smoothly at 24–30 FPS on Snapdragon 6xx (2.8 GHz, Adreno 650) or equiv. with 6 GB RAM, measured on real devices by the maintainer before release.

## Coding Standards

### File & Naming
- **Repository structure:** Minimize diffs against upstream (`agvn/base` at b8048ac). New AGVN code lives in `app/src/main/java/com/winlator/cmod/agvn/` (package `com.winlator.cmod.agvn`); keep `namespace 'com.winlator.cmod'` in `app/build.gradle` (unchanged from Ludashi).
- **File size:** Keep newly created files ≤ 200 lines; split if larger.
- **Naming:** Kebab-case files/directories (already enforced by upstream); class names in PascalCase; constants in SCREAMING_SNAKE_CASE.

### Commits & Branches
- **Conventional format** (no AI/assistant references):
  ```
  feat(driver): add fallback safety for unusable graphics drivers
  fix(diag): fix export zip size calculation
  docs(readme): update device tier thresholds
  refactor(agvn): consolidate profile validation
  ```
- **Branch naming:** `agvn/pNN-<slug>` based on `agvn/main` (e.g., `agvn/p03-crash-safety`). One phase per branch.
- **PR into `agvn/main`:** Maintainer reviews & merges; each PR includes a **device test checklist** (see ROADMAP).

### Hard Rules (Never Commit)

1. **No keystores, passwords, or secrets:** Release signing is done locally by the maintainer.
   - Keystores, `keystore.properties`, signing passwords → `.gitignore`; they live only on the maintainer's machine, outside the repo.
   - Cloud builds are **unsigned** or **debug-signed only** (no release key in CI).
   - Environment variable `AGVN_KEYSTORE_PROPS` overrides the keystore path for future CI.

2. **No features requiring root or in-app ADB self-pairing** (security risk for non-technical users).

3. **No in-app telemetry, ads, or external links** (except agvn.io.vn in About screen and diagnostic exports).
   - Exception, approved by the maintainer: the in-app updater (`agvn/AgvnUpdater`).
     - It reads `agvn-update.txt` and the APK named there, only from this repository's GitHub Releases.
     - It sends nothing about the device.

4. **Do not break upstream structure needlessly:** Keep diffs small and focused; new code in new files where sensible.

## Build Instructions (Linux / Cloud Environment)

### Prerequisites
- **OS:** Ubuntu 24.04 LTS (x86_64, run as root or with sudo)
- **Environment:** `ANDROID_HOME=/opt/android-sdk`, JDK 17+ (cloud typically has 21)
- **Setup:** Run `scripts/agvn/cloud-setup.sh` (idempotent, ~5 min)

### One-Time Setup
```bash
bash scripts/agvn/cloud-setup.sh
source ~/.bashrc  # or re-login for ANDROID_HOME to take effect
```

### Build Release APK
```bash
export ANDROID_HOME=/opt/android-sdk
export AGVN_VERSION_CODE=2
./gradlew assembleRelease --no-daemon
# Output: app/build/outputs/apk/release/app-release.apk
```

### Publish an Update (maintainer's PC only)
Phones update themselves from GitHub Releases (Cài đặt → Cập nhật ứng dụng). There is one channel: every phone gets the
newest release, and there are no test builds. The script builds with the release key, so it runs only on the
maintainer's PC, after `gh auth login`:
```powershell
.\tools\agvn\dang-ban-cap-nhat.ps1    # release v<AGVN_VERSION_NAME> from gradle.properties
```
- Before each release, a cloud session raises `AGVN_VERSION_CODE` and `AGVN_VERSION_NAME` in `gradle.properties`.
  Android only installs a higher code over the installed one.
- It also writes the player-facing notes to `docs/agvn/release/ghi-chu-phat-hanh-v<version>.txt`.

See [`docs/agvn/cap-nhat-ung-dung.md`](./docs/agvn/cap-nhat-ung-dung.md).

### Build & Run Unit Tests
```bash
./gradlew testReleaseUnitTest --no-daemon
# Tests profile schema, exe resolver, device tiers, FPS step-down, INI merge, driver-version parsing
```

### Compile Check (No Build Artifacts)
```bash
./gradlew compileReleaseSources --no-daemon
```

### Skip Vulkan Validation Layer Bundling
The local build includes vulkan-validation-layers (for debug). Cloud builds omit this step — the app runs without validation. Edit `app/build.gradle` `postBuild` task to skip if needed (debug builds do not require it).

## Testing Reality

- **Cloud environment:** No Android device is available. Verify by compiling and unit tests.
- **Device testing:** The maintainer tests on a POCO F8 Pro (Snapdragon 8 Elite / Adreno 830, Android 16 / HyperOS 3) using adb logcat, dumpsys, and field measurements before approving each PR for merge to `agvn/main`.
- **Every PR description must include a "Device Test Checklist"** section listing:
  - Build test (compilation successful, APK signature verified)
  - Install test (safe to install beside existing Ludashi)
  - Functional tests (phase-specific, e.g., driver fallback, crash export, game import)
  - Regression test (previous phase features still work)

## Roadmap & Phase Dependencies

See [`docs/agvn/ROADMAP.md`](./docs/agvn/ROADMAP.md) for the full 10-phase plan, status, and sequential/parallel dependencies.

**Summary:** Phases 1–7 are strictly sequential (shared `XServerDisplayActivity.java`, strings file). Phase 8 can start after phase 2 (branding is parallel). Phase 9 is a standalone PC-side toolkit. Phase 10 is the release candidate after all prior phases merge.

## Critical Files & References

- `app/src/main/java/com/winlator/cmod/agvn/` — all new AGVN code
- `app/src/main/res/values/agvn_strings.xml` — Vietnamese strings (shared by phases 3–7)
- `app/src/main/assets/agvn/` — config JSON, denylist, assets
- `tools/build-windows.ps1`, `tools/pins.txt`, `scripts/agvn/cloud-setup.sh` — reproducible build
- `docs/agvn/phases/` — detailed phase files for reference during implementation

## Attribution & Copyright

- **Original:** BrunoSX (Winlator), StevenMXZ (Ludashi fork)
- **AGVN additions:** Licensed MIT (same as upstream); source code must remain public
- **Third-party notices:** Wine, proot, patchelf, Mesa, DXVK, FEX, Box64, Turnip — see bundled licenses in app
- **Copyright year:** Keep BrunoSX/StevenMXZ notices in all derivative files; add "Copyright (c) 2026 agvn.io.vn" to new AGVN modules

## Further Reading

- [`docs/agvn/ROADMAP.md`](./docs/agvn/ROADMAP.md) — phase overview, dependencies, effort estimates
- [`docs/agvn/phases/phase-02-*.md`](./docs/agvn/phases/) through phase-10 — detailed requirements, code references, success criteria, test checklists
- [`docs/agvn/device-findings.md`](./docs/agvn/device-findings.md) — measured performance characteristics (Adreno 830, 960×544 resolution, thermal limits)
- [`scripts/agvn/`](./scripts/agvn/) — cloud build environment setup and native dependency pinning
