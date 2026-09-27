# Phase 3: Crash Safety (Driver Fallback) & One-Tap Diagnostics Export

**Priority:** P1 · **Effort:** 4h · **Depends on:** Phase 2

## Overview

Prevent crashes when bundled GPU drivers are incompatible or unusable. Implement a driver safety layer that detects unsuitable drivers before launch, falls back to a safe alternative, and logs the event. Add a one-tap diagnostic export feature so non-technical users can generate a support zip without ADB.

## Critical Findings from Device Testing

1. **Root Cause: Driver String Parsing Bug**
   - `XServerDisplayActivity.java:2480` calls `GPUInformation.getVulkanVersion(id, this).split("\\.")[2]`
   - Native code (`app/src/main/cpp/winlator/vulkan.c:215-227`) returns `"Unknown"` when Vulkan instance/device creation fails for a given driver
   - String split produces length 1; accessing index 2 → `ArrayIndexOutOfBoundsException`
   - Qualcomm v863.1 bundled driver crashes on Adreno 830; Turnip driver works reliably

2. **Inconsistent Driver Filtering**
   - `GPUInformation.isDriverSupported()` exists but is applied inconsistently:
     - Applied in `RuntimeSettingsSupport.kt:230-233` and `GraphicsDriverConfigDialog.java:351`
     - NOT applied in `ContainerSectionFragment.java:114-128` and `AdrenotoolsManager.java:136-143`
   - Bundled drivers added to picker without checking support
   - "System" driver (native OS driver) renders no 3D on Adreno 8xx despite passing probe

3. **Diagnostic Access**
   - App can read its own logcat without special permissions
   - No `Application` subclass currently; logs are opt-in via LogView
   - Users without ADB need a way to export logs for troubleshooting

## Requirements

**Functional:**
1. Unusable/denylisted driver at launch → fallback to `DefaultVersion.WRAPPER_ADRENO` (Turnip 26.2.0) if usable; else `System`; show Vietnamese toast
2. Pickers hide unusable bundled drivers and denylisted ones (user-installed drivers remain visible)
3. Uncaught Java exceptions → write to `filesDir/agvn/last-crash.txt` (timestamp, versionName, thread, stack trace), then chain to previous handler
4. Next app start after crash → dialog: "Ứng dụng vừa gặp lỗi. Xuất nhật ký để gửi AGVN?" with [Xuất] [Bỏ qua] buttons
5. Settings row "Xuất nhật ký lỗi" → ZIP to `/sdcard/Download/AGVN-nhat-ky-<yyyyMMdd-HHmm>.zip`:
   - `thiet-bi.txt`: model, SoC, Android version, GPU renderer string, total/available RAM, app version, device tier (once P5 lands)
   - `logcat.txt`: last 20000 lines of logcat
   - `last-crash.txt`: the crash file written by CrashRecorder
   - 2 newest log files from app's log directory

**Non-Functional:**
- Export ≤10 s, ≤10 MB; off the UI thread
- Never include SharedPreferences dumps, API keys, or SteamGridDB credentials
- Denylist is a data file (JSON), not hardcoded; editable without rebuilding

## Architecture

```
App launch
  ├─ extractGraphicsDriverFiles() calls DriverSafety.resolveUsable(ctx, id)
  │  ├─ Check denylist (assets/agvn/driver-denylist.json): {"gpu":"regex", "hide":["id1","id2"]}
  │  ├─ Check probe cache (SharedPreferences "agvn_driver_probe" key: "{id}@{versionCode}")
  │  │  ├─ State: "ok" | "bad" | "probing" (poison pill for native crash recovery)
  │  │  └─ If "probing" on startup → mark "bad" (SIGSEGV in vendor blob)
  │  └─ Fallback chain: try id → fallback → System
  │
  ├─ Pickers (ContainerSectionFragment, AdrenotoolsManager) filter via DriverSafety.isUsable()
  │
  └─ AgvnApp.onCreate() registers CrashRecorder; MainActivity offers export after new crash

Crash recording flow:
  1. User action → uncaught exception → CrashRecorder handler
  2. Write filesDir/agvn/last-crash.txt
  3. Chain to previous handler (or rethrow)
  4. App typically exits
  5. Next launch: MainActivity detects last-crash.txt exists → show dialog

Export flow:
  1. Settings "Xuất nhật ký lỗi" → DiagnosticsExporter.export(ctx)
  2. Executor thread:
     - Read thiet-bi.txt data
     - Run ProcessBuilder logcat with 5s timeout
     - Collect files
     - ZipOutputStream → /sdcard/Download/
  3. Toast: "Xuất xong: AGVN-nhat-ky-<time>.zip"
```

## Implementation Steps

1. **Create `DriverSafety.java`** (package `com.winlator.cmod.agvn`):
   - `isDenylisted(String gpuName, String id)` → check denylist JSON against GPU regex
   - `isUsable(Context ctx, String id)` → call `GPUInformation.isDriverSupported` with poison-pill caching
   - `resolveUsable(Context ctx, String id)` → return fallback chain
   - `parseVulkanPatch(String fullVersion)` → extract patch version or return 0 on parse error

2. **Modify `XServerDisplayActivity.java`** (method `extractGraphicsDriverFiles`, ~2434–2485):
   - Replace `.split("\\.")[2]` with `DriverSafety.parseVulkanPatch(...)`
   - Call `resolveUsable()` before driver extraction
   - If resolved != id: log `"AGVN: fallback {id} -> {resolved}"`, toast message, update config

3. **Create `CrashRecorder.java`:**
   - Implement `Thread.UncaughtExceptionHandler`
   - Write crash info to `filesDir/agvn/last-crash.txt`
   - Chain to previous handler or rethrow

4. **Create `AgvnApp.java` (Application subclass):**
   - `onCreate()`: install `CrashRecorder` via `Thread.setDefaultUncaughtExceptionHandler()`

5. **Modify `AndroidManifest.xml`:**
   - Add `android:name=".agvn.AgvnApp"` to `<application>` tag

6. **Modify `MainActivity.java`** (~124–141):
   - After onboarding check, check if `filesDir/agvn/last-crash.txt` exists
   - Show dialog: "Ứng dụng vừa gặp lỗi. Xuất nhật ký để gửi AGVN?" 
   - [Xuất] → call DiagnosticsExporter; [Bỏ qua] → delete crash file and continue

7. **Create `DiagnosticsExporter.java`:**
   - Public static `export(Context ctx)` method (returns nothing, shows toast)
   - Runs on Executor (e.g., `ForkJoinPool.commonPool()`)
   - Collects device info via `Build`, `Runtime`, `ActivityManager.MemoryInfo`, `GLSurfaceView` vendor/renderer
   - Runs `logcat -d -v time -t 20000` with 5s timeout; catch if blocked
   - Allow-listed SharedPreferences keys: screen-size-related, graphics driver, FEX/Box64 settings, `agvn*`
   - ZipOutputStream → `/sdcard/Download/AGVN-nhat-ky-<yyyyMMdd-HHmm>.zip`

8. **Create `assets/agvn/driver-denylist.json`:**
   ```json
   [
     {
       "gpu": "Adreno \\(TM\\) 8\\d\\d",
       "hide": ["System"]
     }
   ]
   ```

9. **Filter pickers (2 sites):**
   - `ContainerSectionFragment.java:114-116` → wrap adapter filter with `DriverSafety.isUsable()`
   - `AdrenotoolsManager.java:136-143` (bundled drivers) → same filter

10. **Add strings to `app/src/main/res/values/agvn_strings.xml`:**
    - Crash dialog text, export button text, toast messages (Vietnamese)

11. **Unit tests** (`app/src/test/java/.../agvn/DriverSafetyTest.java`):
    - `parseVulkanPatch("1.3.289")` → 289
    - `parseVulkanPatch("Unknown")` → 0
    - Denylist regex matching: Adreno 830 + System → hidden; Adreno 740 + System → shown

## Success Criteria

1. Shortcut created on old build with v863 driver → launch: no crash, fallback logged, game renders
2. Driver picker on device shows Turnip but hides v863 and System
3. `adb shell am crash com.agvn.player` → next launch shows crash export dialog
4. Export → `/sdcard/Download/AGVN-nhat-ky-*.zip` ≤10 MB; unzip contains expected files; no SteamGridDB keys or prefs dumps
5. Unit tests all pass: `gradlew testReleaseUnitTest`

## Device Test Checklist

- [ ] Shortcut with v863 configured → launch succeeds, Turnip fallback in logcat
- [ ] Driver pickers (game settings, AdrenotoolsManager dialog) hide v863 and System
- [ ] Trigger uncaught exception → `last-crash.txt` written
- [ ] Relaunch → crash dialog appears with [Xuất] button
- [ ] Click [Xuất] → ZIP created in `/sdcard/Download/`; contains device info, logcat, crash trace
- [ ] Unit tests pass

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|-----------|
| Native crash in probe (vendor blob SIGSEGV) | Low | High | Poison-pill marker; cache per versionCode |
| Denylist hides driver an older game needs | Low | Low | Turnip covers Adreno 8xx; denylist is data, editable without rebuild |
| Logcat exec blocked by OEM | Low | Low | Catch exception, write "logcat unavailable" |
| Stale shortcut configs with hidden drivers | Low | Medium | Launch-time fallback handles them |

## Next Steps

Phase 4 adds game import profile validation, which reuses the AGVN strings file and the test framework.
