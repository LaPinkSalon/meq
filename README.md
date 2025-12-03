# Colour Checker (Android)

Android app that uses CameraX + OpenCV MCC to verify a two‑panel ColourChecker passport in real
time. Frames are analyzed on-device; UI is Jetpack Compose with clear pass/fail feedback and
overlayed quads.

## Project description

- CameraX preview with live `ImageAnalysis` feeding an OpenCV detector.
- Detector uses OpenCV MCC to find panels, warps to a 6×4 grid, scores patches against MCC24 Lab
  references (CIEDE2000), and combines area/aspect/contrast/blur/color into confidence.
- Supports passport (two panels); requires both panels visible for a pass. Color-coded overlays show
  detected quads with status feedback (green=pass, red=fail, blue=scanning).
- Hilt DI, Kotlin coroutines, Material3 Compose UI.

## Documentation

**[📖 Detector Overview](doc/DETECTOR_OVERVIEW.md)** - Comprehensive technical documentation:
- Step-by-step processing pipeline
- Algorithm details (MCC detection, CIEDE2000, quality metrics)
- Current thresholds and configuration
- Performance analysis (3-10 FPS CPU-only)
- Potential enhancements (multi-threading, adaptive frames, GPU acceleration)
- Assumptions, limitations, and recommended improvements

## Setup & build

Prereqs: Android Studio Hedgehog/Koala+, JDK 17, Android SDK 35, device/emulator with arm64 or
x86_64.

Build:

```
./gradlew :app:assembleDebug
```

Release (minify/shrink on):

```
./gradlew :app:assembleRelease
```

Tests:

```
./gradlew :app:test              # unit
./gradlew :app:connectedAndroidTest  # instrumented (uses assets)
```

## Usage

1) Install debug/release build on a device.
2) Grant camera permission on launch.
3) Place a ColourChecker (single or dual panel) in view, flat and well lit.
4) Watch the status card and overlay. The overlay color indicates detection status:
   - **🟢 Green**: Valid color checker detected (PASS - confidence ≥70%)
   - **🔴 Red**: Detection failed validation (wrong object, poor lighting, blur, etc.)
   - **🔵 Blue**: Analyzing/scanning (detection in progress)
   - **🟠 Orange**: System error
   - **No overlay**: No detection found
5) Torch toggle available in the top bar (if device supports it).

## Assumptions & limitations

- Supports single or dual panel ColorCheckers; two-panel ColorChecker Passports show both panels when
  detected.
- Detection tuned for MCC24 layout (6×4 grid); unconventional charts may be rejected.
- Requires confidence ≥70% to pass; partially blocked/covered ColorCheckers will fail.
- CPU-only processing: 3-10 FPS frame rate (adequate for real-time verification).
- Overlay uses center-crop mapping; extreme aspect ratios may show slight misalignment.
- OpenCV MCC detector may find false-positives (grids, books, tiles); validation and color-coded
  overlays help distinguish real vs. fake detections.
- Color thresholds are relaxed (ΔE 120/180) for real-world lighting; heavy glare/blur will fail.
- Only arm64-v8a and x86_64 ABIs are packaged (larger APK due to native libs).
- No network or backend; all processing is on-device.
