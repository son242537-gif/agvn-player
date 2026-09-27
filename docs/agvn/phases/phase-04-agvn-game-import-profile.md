# Phase 4: AGVN Game Import Profile

**Priority:** P1 · **Effort:** 8h · **Depends on:** Phase 3

## Overview

Implement one-tap game import via `agvn-profile.json` schema. Users place games in `/sdcard/AGVN/<GAME>/` with a profile file; tapping "Thêm game AGVN" auto-creates Ludashi shortcuts with correct executable, environment, FPS cap, resolution, and texture pool. Profiles are validated at import to reject unsafe paths/args and guide users.

## Key Technical Insights

1. **Game Executable Selection**
   - UE games: prefer `<Game>/Binaries/Win64/<Game>-Win64-Shipping.exe` (statically linked VC++ runtime)
   - Avoid Debug and launcher exes (require .NET Framework & VC++ runtime redists → crashes under Wine)
   - Unity, Godot, GameMaker: platform-specific detection
   - Profile `exe` path is relative to game folder; absolute paths rejected

2. **Profile Schema v1 (Immutable)**
   - Version number gates validation rules; future breaking changes ship as v2
   - Portable: stores FPS cap, screen resolution, texture pool, UE Engine.ini overrides
   - Per-game override: shortcut extras inherit from profile, allowing user per-game tweaks later
   - Validated on import: path containment, arg/env allow-lists, texture pool vs device RAM

3. **User Safety**
   - Shell injection: args/env validated against allow-list regex (no `$(`, `` ` ``, `|`, `&&`, etc.)
   - Path traversal: `exe`, `workingDir` must be inside game folder (no `..`)
   - RAM sanity: texture pool ≤ device available RAM at import time

## Requirements

**Functional:**
1. `AgvnProfile` class: immutable, serializable to/from JSON; validator included
2. `GameExeResolver` class: detects engine type and suggests exe path (UE, Unity, Godot, GameMaker, etc.)
3. UI: "Thêm game AGVN" button in library → file picker or paste profile JSON
4. Import flow: validate profile → create Ludashi shortcut with profile data → store profile in app folder
5. Shortcut extras: inherit `exe`, `args`, `env`, `resolution`, `fpsLimit`, `texturePool` from profile
6. Error dialog: clear Vietnamese message if profile invalid (path outside folder, unknown exe, etc.)

**Non-Functional:**
- Profiles store only declarative config, no runtime state
- No profile modification in UI (advanced users edit JSON directly)
- Schema evolution: v1 is read-only; support multiple versions in code

## Implementation Steps

1. **Create `AgvnProfile.java`** (immutable data class):
   - Fields: `schemaVersion=1`, `name`, `exe`, `workingDir` (optional), `args[]`, `env{key:value}`, `resolution`, `fpsLimit`, `texturePool`, `weakDevice{...}`, `ueEngineIni{key:value}`
   - `validate(gameFolder)` → throws `AgvnProfileException(message)` with i18n message keys
   - Serialization: JSON via Gson with custom adapter

2. **Create `GameExeResolver.java`** (static methods):
   - `detectEngine(gameFolder)` → enum (UE5, UE4, Unity, Godot, GameMaker, etc.)
   - `resolveExe(gameFolder, engine)` → suggested exe path relative to folder
   - Heuristics: search for `Binaries/Win64/*-Shipping.exe`, `.exe` in root, etc.
   - Used by profile generator (P9) and import validator

3. **Modify app data model:**
   - Store profiles in app's data folder (`/sdcard/AGVN-Player/profiles/`), one JSON per game
   - Shortcut extras: add `agvnProfilePath` key; on launch, ProfileManager loads and applies settings

4. **Modify shortcut creation flow:**
   - `ShortcutManager` → after loading from Ludashi `.desktop` files, check for `agvnProfilePath` extra
   - If present, merge profile exe/args/env into shortcut config
   - FPS cap and resolution → apply to Ludashi preferences

5. **Create import UI:**
   - New fragment/dialog "Thêm game AGVN"
   - Folder picker: navigate to `/sdcard/AGVN/` subdirectories
   - Look for `agvn-profile.json` in folder; if found, show preview (name, exe, FPS, resolution)
   - [Nhập] button → validate profile → create shortcut → show success toast

6. **Validation rules (test fixtures required):**
   - `exe` path: must exist, be .exe, and be inside gameFolder (no `..`)
   - `args`, `env` values: whitelist alphanumeric, `/`, `-`, `_`, `=`, `.`, `:`; reject shell metacharacters
   - `workingDir` (optional): same containment rule
   - `resolution`: format `WIDTHxHEIGHT`, 640–3840 px
   - `fpsLimit`: 15–120 (or 0 = unlimited)
   - `texturePool`: 128–4096 MB
   - `ueEngineIni`: keys must match `[Engine.Rendering]` or similar sections

7. **Persist imported game:**
   - Profile JSON copied to `/sdcard/AGVN-Player/profiles/<GAMEID>/agvn-profile.json`
   - Shortcut persisted in Ludashi database (or custom storage)
   - Allow user to delete profile → removes shortcut

8. **Unit tests** (JUnit):
   - Profile validation: valid/invalid schemas, path containal, arg injection attempts
   - GameExeResolver: UE exe detection, Unity path suggestions
   - Fixtures: minimal game folder structures for each engine type

## Success Criteria

1. Import dialog finds `agvn-profile.json` in game folders; preview shows name/exe/FPS/resolution
2. Invalid profile (exe outside folder, shell metacharacters in args) → error dialog with Vietnamese message
3. Valid profile import → shortcut created; launch shortcut uses exe/FPS/resolution from profile
4. Profile stored in `/sdcard/AGVN-Player/profiles/<GAMEID>/` for persistence
5. Shortcut delete → profile can be re-imported
6. Unit tests: validation and exe detection all pass
7. Field test: import UE5 game (GameExeResolver auto-finds Shipping exe), Unity game, generic exe → all launch correctly

## Device Test Checklist

- [ ] Import a UE5 test game → profile auto-loads, exe correctly identified as Shipping variant
- [ ] Import with malformed profile (exe outside folder, shell metacharacters) → error shown
- [ ] Valid import → shortcut launches game with correct FPS cap and resolution applied
- [ ] Profile persisted; uninstall/reinstall keeps profile accessible
- [ ] Delete shortcut → can re-import profile with [Nhập]

## Next Steps

Phase 5 uses the profile schema to detect device tier and auto-apply presets. Phase 9 creates a PC-side tool that generates profiles.
