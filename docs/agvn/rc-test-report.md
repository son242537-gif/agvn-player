# RC test report — AGVN Player v0.1.0 (versionCode 2)

Fill in on the maintainer PC + POCO F8 Pro, then commit this file with the results before tagging `v0.1.0`.
Tester: ____  Date: ____  Commit: ____  APK SHA-256: ____

## 1. Build & security
| Check | Command | Result |
|---|---|---|
| Unit tests (app + toolkit) | `make-release.ps1` / `make-release.sh` (runs both) | ☐ pass |
| Signed with AGVN key | cert SHA-256 = `release_cert_sha256` in `scripts/agvn/pins.txt` | ☐ |
| Label / package / version | `aapt2 dump badging` → AGVN Player, com.agvn.player, 2 / 0.1.0 | ☐ |
| No secrets in git | secret gate in the release script | ☐ |
| Antivirus | Defender scan in `make-release.ps1` (or other AV) | ☐ clean |

## 2. Install & upgrade (POCO F8 Pro, Android 16)
| Check | Result |
|---|---|
| Install beside Ludashi; both listed in `pm list packages` | ☐ |
| Upgrade over a previous AGVN build keeps library games and saves (debug→release signature change needs uninstall once; release→release must upgrade in place) | ☐ |
| Splash 1.2 s, tap skips; onboarding Vietnamese; `/sdcard/AGVN-Player` created | ☐ |

## 3. Functional
| Check | Result / notes |
|---|---|
| Import UE5 game (AV Director LIFE) → Shipping exe, reaches gameplay, tap = click, clean exit | ☐ |
| Import Unity game → reaches gameplay | ☐ |
| Import third engine (GameMaker/Ren'Py/other) → reaches gameplay | ☐ |
| Invalid profile → Vietnamese error, nothing imported | ☐ |
| Driver fallback v863/System → Turnip, toast shown | ☐ |
| `am crash com.agvn.player` → crash dialog → ZIP in Download | ☐ |
| Thermal warning (battery ≥ 45 °C) and low-RAM warning (< 300 MB) | ☐ |
| Pre-launch RAM dialog (< 1.5 GB free) with Kiểm tra lại / Vẫn chơi / Hủy | ☐ |
| Tier override Yếu → new import uses 24 FPS, 854×480, pool 512 | ☐ |
| Engine.ini has `r.Streaming.PoolSize` after launch | ☐ |
| 10-screen Vietnamese walkthrough (phase 8 list), no text cut at 360 dp | ☐ |

## 4. Performance vs baseline (AV Director, 1280×720, Turnip; `tools/agvn/do-tai.sh 12 10`)
| Metric | Baseline (device-findings) | RC | Pass rule | Pass |
|---|---|---|---|---|
| FPS (HUD) | 29.7 | | ≥ 29 avg | ☐ |
| App PSS / game RAM | ~1.5 GB | | ±10% | ☐ |
| CPU °C / GPU °C | 50.7 / 45.9 | | ±5 °C | ☐ |
| Free RAM at start | 3.43 GB | | — | |

## 5. Field test (2–3 weak phones: Adreno 6xx/Mali, 4–8 GB)
| Phone | SoC / GPU / RAM | Detected tier | Game | 5-min result | Diag ZIP |
|---|---|---|---|---|---|
| | | | | | ☐ |
| | | | | | ☐ |
| | | | | | ☐ |

Tier wrong on a phone → edit `app/src/main/assets/agvn/device-tiers.json`, bump `AGVN_VERSION_CODE`, rebuild v0.1.1.

## Decision
☐ Release v0.1.0  ☐ Fix and rebuild — notes: ____
