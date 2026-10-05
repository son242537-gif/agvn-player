# Phase 8: Vietnamese UI & AGVN Branding

**Priority:** P1 · **Effort:** 10h · **Depends on:** Phase 2 (parallel-safe until P7 merges)

## Overview

Rename the app to "AGVN Player", replace the icon with AGVN branding, add a 1.2-second splash screen, and translate all customer-facing UI text to Vietnamese. Remove references to Winlator and upstream branding from customer paths (onboarding, library, game launch, in-game menu, settings). Keep original authors' legal notices in a separate Licenses screen.

## Scope: Customer-Facing Screens (Vietnamese)

1. Onboarding: welcome screen, permission grants
2. Library: game list view, game detail view
3. Launch: pre-game loading screen, game title
4. In-game: sidebar menu, FPS/RAM overlay, input controls
5. Exit: completion/save reminder
6. Settings: basic options (resolution, FPS cap, touchscreen mode)
7. Main menu: File Manager, Settings, About, Help
8. Import dialog: "Thêm game AGVN"

**Out of Scope (Stay English):** Container editor, driver/Box64/FEX/DXVK advanced configs, XR features, internal log viewers.

## Branding Touchpoints

| Location | Current | New |
|----------|---------|-----|
| App name | "Winlator" | "AGVN Player" |
| Manifest label | Winlator Ludashi | AGVN Player |
| Launcher icon | Generic | AGVN logo (adaptive) |
| Splash screen | (none) | AGVN logo 1.2 s, tappable |
| About | Winlator credits + winlator.org | AGVN Player, agvn.io |
| Licenses | (none) | MIT + third-party list |

## Requirements

**Functional:**
1. App labeled "AGVN Player"; `applicationId` from P2
2. Adaptive launcher icon: AGVN logo (1024×1024, provided) scaled to mdpi–xxxhdpi
3. SplashActivity: shows AGVN logo, counts 1.2 s (or user taps to skip), then launches MainActivity
4. About dialog: only agvn.io credit; button "Giấy phép mã nguồn mở" → separate screen
5. Licenses screen: MIT text ("Copyright (c) 2023 BrunoSX"), Wine/proot/patchelf source links
6. Vietnamese text on all 8 scoped screens; no English except app/product names (Wine, DXVK, FPS, etc.)
7. No layout overflow at 360 dp width

**Non-Functional:**
- Translation in `res/values/strings.xml` (in-place, not separate `values-vi/`)
- Branding changes isolated to marked sections; easy upstream merges
- Licenses render via `Html.fromHtml` (bundled asset, not WebView)

## Implementation Steps

1. **Prepare assets:**
   - AGVN logo 1024×1024 PNG as `res/drawable-nodpi/agvn_logo.png`
   - Use ImageMagick to generate icon files (mdpi 48 px, hdpi 72 px, xhdpi 96 px, xxhdpi 144 px, xxxhdpi 192 px)
   - Create adaptive icon XML: `res/mipmap-anydpi-v26/ic_launcher.xml` + background color

2. **Create SplashActivity:**
   - Simple Activity: layout with ImageView (AGVN logo), countdown
   - Post handler at 1.2 s → startActivity(MainActivity)
   - OnTouchEvent → skip countdown, launch immediately
   - Manifest: add LAUNCHER intent filter; move MAIN from MainActivity to here

3. **Update AndroidManifest.xml:**
   - Line 29: change label to `@string/app_name`
   - Launcher intent-filter: move from MainActivity to SplashActivity
   - MainActivity no longer LAUNCHER

4. **Create About & Licenses UI:**
   - Modify `MainActivity.showAboutDialog()`: new layout with AGVN branding
   - Button "Giấy phép mã nguồn mở" → `LicensesActivity`
   - `LicensesActivity`: WebView or TextView with `Html.fromHtml(assets/agvn/licenses.html)`

5. **Prepare licenses.html:**
   ```html
   <html><body>
   <h2>AGVN Player</h2>
   <p>Based on Winlator (MIT License)</p>
   <p><strong>Copyright (c) 2023 BrunoSX</strong></p>
   <p>Permission is hereby granted...</p>
   <h3>Third-Party Libraries</h3>
   <ul>
   <li>Wine (LGPL): https://www.winehq.org</li>
   <li>proot (GPL): https://proot-me.github.io</li>
   <li>patchelf (GPL-3): https://github.com/NixOS/patchelf</li>
   </ul>
   </body></html>
   ```

6. **Translate strings.xml:**
   - Line 4: `TERMUX_X11_APP_NAME` "Winlator:X11" → "AGVN Player:X11"
   - Line 7: `app_name` "Winlator" → "AGVN Player"
   - 80/20 scope: prioritize customer-path strings (onboarding, library, game detail, in-game sidebar)
   - Use glossary: Container → "Môi trường chạy", Shortcut → "Game", Resolution → "Độ phân giải", etc.

7. **Sweep hardcoded literals (after P7 merges):**
   - Scoped files: `J/ui/onboarding/`, `J/ui/library/`, `J/GameDetailFragment.java`, etc.
   - Replace `Text("English")` with `stringResource(R.string.key)` or Vietnamese text in place
   - Automate where possible: `grep -rn 'Text("' app/src/main/java/com/winlator/cmod/ui/` → generate TODO list

8. **Verify no branding leakage:**
   - `grep -rn "Winlator\|winlator.org\|Ludashi\|StevenMXZ" app/src/main/res/ app/src/main/java/com/winlator/cmod/ui/` → only allowed hits (licenses.html, internal identifiers, log tags)

## Success Criteria

1. `aapt2 dump badging` → `application-label:'AGVN Player'`; launchable activity = SplashActivity
2. Cold start shows splash 1.2 s then transitions to library
3. Splash taps to skip
4. About screen shows only agvn.io; licenses button shows MIT notice with BrunoSX copyright
5. 8 scoped screens contain no English words except product/technical names (Wine, FPS, etc.)
6. Grep gate passes (no leakage of Winlator/Ludashi/StevenMXZ in customer UI)
7. Maintainer device visual review: 10-screen walkthrough confirms Vietnamese on each screen

## Device Test Checklist (Maintainer)

- [ ] Cold start → splash shows 1.2 s, transitions smoothly
- [ ] Tap splash during countdown → skips to library immediately
- [ ] Library screen: all text Vietnamese (no English except app names)
- [ ] Game detail: Vietnamese; buttons, descriptions all translated
- [ ] Launch countdown + pre-load screen: Vietnamese
- [ ] In-game sidebar: Vietnamese menu, FPS/RAM display readable
- [ ] Settings basic options: Vietnamese labels
- [ ] About: only agvn.io; licenses button works
- [ ] Export diagnostic: checks crash export (P3) includes Vietnamese strings without corruption

## Next Steps

Phase 10 release candidate testing; phases 6–7 run in parallel after phase 7 merges to main.
