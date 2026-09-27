# Device Findings — POCO F8 Pro Baseline Measurements

**Test device:** POCO F8 Pro (Snapdragon 8 Elite, Adreno 830, 11 GB RAM, Android 16 / HyperOS 3)  
**Game:** UE5 DX11 test case; 30 FPS capped, low quality  
**Setup:** Proton 9 arm64ec + FEXCore 2601 + Turnip driver + 1280×720 resolution + Simulated Touchscreen

## Performance Baseline

| Scenario | Resolution | GPU Busy | CPU | RAM (Game) | Free RAM | CPU °C | GPU °C | FPS |
|----------|------------|----------|-----|------------|----------|--------|--------|-----|
| B0 | 1280×720 | 48.6% | 200% | 1.48 GB | 3.43 GB | 50.7 | 45.9 | 29.7 |
| R1 | 960×544 | 40.9% | 192% | 1.72 GB | 3.26 GB | 51.3 | 45.3 | 29.9 |
| R3 | 1280×720, VRAM 1024 MB | 48.7% | 199% | 1.81 GB | 3.28 GB | 52.1 | 46.9 | 29.8 |

**Key insight:** Reducing resolution from 1280×720 to 960×544 (−43% pixels) yields only −16% GPU busy. Fixed costs (DXVK translation, post-processing passes) dominate; weak devices still benefit, but avoid expecting 2× speedup.

## Critical Findings

1. **Correct .exe is essential**
   - UE games must run Shipping exe (e.g., `<Game>-Win64-Shipping.exe`), not Debug or launcher
   - Debug exe requires .NET Framework & VC++ redists → Wine crashes with `0xc0000005`
   - Shipping exe uses Wine's built-in msvcp140/vcruntime → loads in ~45 s

2. **GPU driver selection (Adreno 830)**
   - **Turnip (recommended):** Renders correctly, ~49% GPU busy, stable 30 FPS
   - **Qualcomm v863.1 (bundled):** Crashes at launch with `ArrayIndexOutOfBoundsException` in `XServerDisplayActivity.extractGraphicsDriverFiles` (string split bug, fixed in P3)
   - **System driver (HyperOS native):** No 3D rendering (UI only), unsuitable

3. **VRAM cap ineffective on phones**
   - Setting "Max Device Memory" (VRAM limit) to 1024 MB does not save RAM
   - GPU uses shared system RAM; capping VRAM just relocates data to host memory
   - Real lever: game-side texture pool, texture resolution, model LOD (done on PC in P9, applied by P5)

4. **Thermal & RAM headroom**
   - Sustained CPU 50–53°C, GPU 45–47°C after ~15 min gameplay (safe range)
   - Game + emulator consume ~2.5 GB combined CPU + GPU RAM
   - Minimum safe launch: 1.5 GB free RAM on phone; below that, risk OOM kill

5. **Simulated Touchscreen mode**
   - "Simulated Touchscreen" (per-game setting) converts touch to mouse click at correct coordinates
   - Suitable for games designed for mouse/visual novel input; enable by default for AGVN titles
   - Toggle via Input tab settings; stored per-game

6. **FPS cap (30 fps) behavior**
   - Game limits frame rate internally via configuration
   - At 30 FPS cap, GPU scales down power automatically (not CPU-bound)
   - Percentage "GPU busy" is relative to 30 FPS; not a direct load indicator
   - Lower FPS cap → lower GPU utilization, but fixed costs remain (~16–18% baseline)

## Measurement Procedure (Reference for Future Testing)

Use `dumpsys gpu --gpuwork` to measure GPU utilization (requires ADB access, no root):
```
adb shell dumpsys gpu --gpuwork | grep uid
```
Calculate: `Δtotal_active_duration_ns / elapsed_wall_ns` for your app's uid.

For CPU temperature and load:
```
adb shell dumpsys thermalservice | grep Skin  # or CPU thermal zone
adb shell top -n 1 | head -20
```

Thermal throttle threshold: Qualcomm devices typically throttle above 50–55°C; HyperOS 3 may cap CPU clock.

## Weak Phone Extrapolation

This device (Snapdragon 8 Elite) is a flagship. For weak phones (Snapdragon 6xx, Mali G77/G78, 4–8 GB RAM):
- GPU busy % will be higher (older GPUs less efficient)
- CPU load and thermal headroom lower; targeting 45°C as safe upper limit (P6, P7)
- Available free RAM tighter; 300 MB threshold for safety dialogs (P6)
- 24 FPS cap + 854×480 resolution likely necessary (P5 weak tier)

Field testing with actual weak phones (P10 beta phase) required for tuning.
