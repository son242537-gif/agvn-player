# Phase 2: App Identity, Release Signing & Pinned Build

**Priority:** P1 · **Effort:** 4h · **Depends on:** Phase 1

## Overview

Establish the app's permanent identity (`com.agvn.player`), configure release signing with a maintainer-controlled keystore, and make builds reproducible by pinning all downloaded dependencies. After this phase, the APK can be installed alongside Ludashi and GameHub without data conflicts, and updates will be delivered safely to end users.

## Key Technical Insights

1. **Application ID vs. Namespace Separation**
   - Ludashi keeps `namespace 'com.winlator.cmod'` in `app/build.gradle` (all Java packages, R class unchanged)
   - Application ID becomes `com.agvn.player` (what users see in Play Store, system package list, and preferences)
   - This approach is proven: upstream's vanilla build uses `com.winlator.vanilla` with the same namespace

2. **Provider Authority Risk**
   - `AndroidManifest.xml:120` hardcodes `com.winlator.cmod.tileprovider`
   - `AndroidManifest.xml:128` hardcodes `com.winlator.cmod.core.WinlatorFilesProvider`
   - These prevent side-by-side installs of different CMOD variants
   - **Fix:** Use `${applicationId}` placeholders; grep confirms no Java code references them directly

3. **Shared External Folder Conflict**
   - `J/SettingsFragment.java:84` defines `DEFAULT_WINLATOR_PATH = /sdcard/Winlator`
   - 25 code references total; 10 are hardcoded literals (the rest use the constant)
   - Another Winlator variant could write to `/sdcard/Winlator` and change our GPU driver settings (qgl_config.txt)
   - **Fix:** Create `/sdcard/AGVN-Player` and update all references

4. **Release Signing Configuration**
   - Current `app/build.gradle:142` has `signingConfig signingConfigs.debug` for release builds
   - This silently falls back to debug signing if the keystore is missing—dangerous
   - Requirement: release build MUST fail loudly if the keystore props are not found

## Requirements

**Functional:**
- Application package ID: `com.agvn.player`
- Version from gradle properties: `AGVN_VERSION_CODE` and `AGVN_VERSION_NAME`
- Release APK signed with maintainer's private keystore
- Data folder: `/sdcard/AGVN-Player`
- Coexists safely with Ludashi (`com.winlator.vanilla`) and GameHub

**Technical:**
- `tools/build-windows.ps1` (from local phase 1 setup) provided as reference; cloud builds run `gradlew assembleRelease`
- `tools/prepare-native-deps.ps1` fetches and verifies imagefs and proton at pinned SHAs
- `tools/pins.txt` records sha256 hashes; build fails if cached files don't match
- `docs/agvn-build.md` (≤60 lines) documents build steps for developers
- No secrets in git: `.gitignore` blocks keystores, `keystore.properties`, tar.zst archives

**Non-Functional:**
- Offline rebuild possible via `-PagvnAssetCache=<dir>` gradle property
- Release build with missing keystore props produces a Vietnamese error message

## Implementation Steps

1. **Create application identity block in `app/build.gradle`:**
   - Set `applicationId "com.agvn.player"` (inside a `// AGVN identity block` comment)
   - Link `versionCode` and `versionName` to `gradle.properties`: `AGVN_VERSION_CODE=1`, `AGVN_VERSION_NAME=0.1.0`
   - Configure `signingConfigs.release` to load from `keystore.properties`
   - Add gradle logic: if release build is in task graph and props are missing, throw a GradleException with message in Vietnamese

2. **Update provider authorities in `AndroidManifest.xml`:**
   - Line 120: change `android:authorities="com.winlator.cmod.tileprovider"` → `"${applicationId}.tileprovider"`
   - Line 128: change `android:authorities="com.winlator.cmod.core.WinlatorFilesProvider"` → `"${applicationId}.core.WinlatorFilesProvider"`

3. **Unify external storage path:**
   - `J/SettingsFragment.java:84`: update `DEFAULT_WINLATOR_PATH` constant to use `Environment.getExternalStorageDirectory() + "/AGVN-Player"`
   - Replace 10 hardcoded `"Winlator/..."` literals with the constant + subfolder name (e.g., `DEFAULT_WINLATOR_PATH + "/qgl_config"`):
     - `J/container/Shortcut.java:89`
     - `J/core/GameSaveManager.java:109`
     - `J/core/PreloaderDialog.java:241,248,256`
     - `J/FileManagerFragment.java:850,861`
     - `J/GameDetailFragment.java:65,67,69`
   - Verify no remaining `"Winlator/..."` literals: `grep -r '"Winlator/' app/src/main/java/com/winlator/cmod/`

4. **Implement download pinning (imagefs and proton):**
   - Record sha256 of imagefs and proton files in `tools/pins.txt`
   - Modify `app/build.gradle` download tasks:
     - Check if `agvnAssetCache` (new gradle property) holds the file with matching sha256 → copy it
     - Otherwise download from official source
     - Verify sha256 of downloaded or cached file; fail build if mismatch
   - Example: `gradle -PagvnAssetCache=/opt/build-cache assembleRelease`

5. **Update `.gitignore`:**
   - Add `*.jks`, `keystore.properties`, `app/src/main/assets/imagefs.tar.zst`, `app/src/main/assets/proton-*.tar.zst`

6. **Create build documentation:**
   - `docs/agvn-build.md` (≤60 lines): how to build locally, environment setup, how to specify the keystore props path

7. **Verification checklist:**
   - `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` → SHA-256 cert fingerprint matches entry in `tools/pins.txt`
   - `aapt2 dump badging app/build/outputs/apk/release/app-release.apk` → `package: name='com.agvn.player' versionCode='1' versionName='0.1.0'`
   - Maintainer device test: install alongside Ludashi; verify separate data folder; adb pull `/sdcard/AGVN-Player` is distinct from `/sdcard/Winlator`

## Success Criteria

1. `adb shell pm list packages` shows both `com.agvn.player` and the Ludashi package installed together
2. `apksigner verify --print-certs` SHA-256 equals the certificate fingerprint in `tools/pins.txt`
3. Release build without `keystore.properties` fails with a Vietnamese error message; debug build succeeds
4. Changing one byte of cached imagefs → build fails with sha256 mismatch
5. After first launch, `adb shell ls /sdcard/AGVN-Player` exists and is separate from `/sdcard/Winlator`
6. `grep -r '"Winlator/' app/src/main/java/com/winlator/cmod/` returns 0 hits (excluding strings.xml)
7. `git ls-files | grep -E '\.jks|keystore\.properties|tar\.zst'` returns empty

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|-----------|
| Keystore lost or leaked | Low | Critical | Stored outside repo with offline backups; fingerprint pinned; password managed by maintainer only |
| Missed folder reference → app data conflict | Medium | Low | Automated grep gate in success criteria; manual review of phase PR |
| applicationId lookup bug in Compose UI | Low | Medium | Smoke test on device: verify HUD, sidebar, input control display |
| Upstream merge conflicts in build.gradle | Medium | Low | AGVN edits confined to marked blocks; easy to reapply |

## Device Test Checklist

- [ ] Release APK builds and signs without keystore password prompt
- [ ] Install on maintainer device beside Ludashi (packageNames both present)
- [ ] First launch creates `/sdcard/AGVN-Player` directory
- [ ] Ludashi still accesses `/sdcard/Winlator` (no cross-contamination)
- [ ] onboarding + library + launch one game all function (HUD visible, sidebar responsive)

## Next Steps

Phase 3 reuses the signed APK and adds crash safety + driver fallback logic.
