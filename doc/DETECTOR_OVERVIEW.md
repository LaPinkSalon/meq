# OpenCvColorCheckerDetector: Step-by-Step Flow

This document explains how the detector processes a frame, why each step exists, and what
assumptions/limits apply.

## Goals

- Detect a two-panel ColourChecker passport (two MCC24 grids side-by-side).
- Validate patch colors against MCC24 Lab references (CIEDE2000).
- Produce confidence + failure reason for the UI.
- Keep processing on-device; no network.

## Inputs

- `AnalysisFrame`: width, height, rotationDegrees, raw RGBA bytes.
- Dependencies:
    - `ColorCheckerLocator`: MCC quad detection (full + left/right splits).
    - `ImageQualityAnalyzer`: blur (Laplacian variance), contrast.
    - `PatchAnalyzer`: warp to 6×4 grid, sample patches, ΔE.
    - `DetectionScorer`: aggregate metrics into confidence/failure.
    - `Logger`: Timber-backed in debug, no-op in release.

## Processing Pipeline (detect)

1. **Frame validation**
    - Reject if dimensions ≤ 0 or pixel buffer missing/too small.

2. **Convert to OpenCV Mats**
    - Wrap RGBA bytes → `Mat` (CV_8UC4).
    - Convert to BGR (`cvtColor`) for MCC; also produce gray + Gaussian blur for quality metrics.

3. **Quality metrics**
    - `laplacianVariance(gray)`: sharpness proxy (higher = sharper).
    - `contrast(gray)`: basic contrast heuristic.

4. **Locate charts**
    - `locator.locateAll(bgr)` runs MCC on full image, then left/right halves, dedupes quads.
    - If zero quads: return `NotFound`.
    - Proceeds with 1 or more quads (single-panel supported).

5. **Primary selection**
    - Sort quads by area; pick the largest as primary.
    - Order corners TL, TR, BR, BL for warping.

6. **Primary scoring**
    - `patchAnalyzer.scorePatches(bgr, quad)`: warp to 600×400, sample 6×4 centers, compute
      CIEDE2000 ΔE vs MCC24 refs.
    - Compute primary bounds for geometry/area/aspect.

7. **Secondary handling**
    - If a second quad is detected:
        - Order corners, warp to 6×4 grid.
        - Validate grayscale patches: avgChroma < 55, maxChroma < 90, and monotonic luminance
          descent.
    - If no second quad: `secondaryValid = false`.
    - Single-panel detection is supported (continues with primary only).

8. **Scoring & output**
    - `DetectionScorer.score` blends:
        - Area (boosted), aspect, contrast, blur, color into confidence.
        - Fails when: blur too low → Blur; area too small → Partial; contrast too low → Lighting.
        - Pass threshold: confidence ≥ 0.70 (70%).
    - Returns `DetectionOutput` with `DetectorResult` (confidence, failureReason, needsInput) +
      `DetectionMetrics` for UI overlay/debug.

## Key Thresholds (current)

- **Pass threshold**: confidence ≥ 0.70 (70%).
- **Color (primary)**: avg ΔE < 120, max < 180 for scoring (relaxed for real-world lighting).
- **Secondary validation**: avgChroma < 55, maxChroma < 90, monotonic luminance descent.
- **Failure checks**:
    - Area floor: areaScore < 0.005 → Partial.
    - Blur floor: blurScore < 0.15 → Blur.
    - Contrast floor: contrastScore < 0.08 → Lighting.
- **Confidence weights**: area 0.7, aspect 0.1, contrast 0.05, blur 0.05, color 0.1 (area boosted
  ×8).

## Assumptions

- **Single or dual panels supported**: Detection works with 1 or 2 MCC24 panels. Secondary
  validation only applies when 2 panels detected.
- Input frames are upright (rotationDegrees provided by CameraX); overlay handles rotation.
- ABIs: arm64-v8a, x86_64 (bytedeco OpenCV/OpenBLAS bundled; larger APK).
- Processing is on CPU; no GPU/NPU acceleration.

## Limitations & Risks

- MCC detection can vary by device/lighting; thresholds are relaxed but may need tuning per device.
- Overlay uses center-crop mapping; extreme aspect ratios may misalign slightly.
- Inference path for second panel is disabled when two quads exist; if MCC misses one panel, result
  is Partial.
- No glare compensation; severe reflections can trip Lighting/NotFound.

## Potential Enhancements

### UX & Robustness
- Stabilize detections over a sliding window to reduce flicker.
- Improve overlay mapping with exact view transform from `PreviewView`.
- Add on-device calibration UI to tune thresholds.
- Make glare more tolerant: sample multiple sub-ROIs per patch (median/trimmed mean), downweight
  saturated highlights, and add a specific glare hint instead of generic lighting failures.

### Performance (CPU-only → 10-30 FPS)
Current: 3-10 FPS (CPU-only, sequential processing)

**Quick wins** (Low effort, high impact):
1. **Multi-threaded patch analysis**: Parallelize CIEDE2000 calculations across 24 patches using
   coroutines (2-4× speedup on multi-core devices).
2. **Adaptive frame processing**: Skip frames when confidence is stable (3-5× effective speedup,
   50% battery savings).
3. **Two-stage color validation**: Fast CIE94 screening → CIEDE2000 confirmation only for close
   matches (1.5-2× speedup).
4. **Resolution downsampling**: Process at 720p instead of 1080p+ (1.5-2× speedup).

**Advanced options** (Higher effort):
5. **Android NNAPI**: Offload LAB color conversion to NPU/GPU (2-3× speedup, requires TFLite
   integration).
6. **Vulkan compute shaders**: GPU-accelerated CIEDE2000 (5-10× speedup, complex implementation).
7. **Native C++ with ARM NEON**: SIMD-optimized math functions (2-4× speedup, ARM-only).


### Sliding-Window Stabilization (concept)

- Keep a ring buffer of the last N detections (e.g., 10–20 frames).
- Derive displayed state from the buffer: require a majority (e.g., 70%) of recent frames to be
  “pass” before showing Pass; otherwise show Fail/Scanning.
- Debounce transitions: only flip Pass→Fail (or vice versa) if the new state persists for M
  consecutive frames.
- Smooth overlay: draw the most recent valid quad or median-filter quad points across the buffer to
  reduce jitter.

### On-device calibration UI (concept)

- Debug-only screen with sliders/toggles for key thresholds: avg/max ΔE, area/aspect tolerances,
  contrast/blur floors, secondary strictness, confidence weights.
- Live preview: apply changes immediately to incoming frames; show overlay + metrics (ΔE, area,
  contrast, blur) while adjusting.
- Presets: save/load named profiles (e.g., “Lab lighting”, “Sunlight”) with reset-to-defaults; store
  in prefs.
- Optional export/import of JSON configs for sharing across devices/teams.
- Gate behind a debug flag/gesture; detector reads thresholds from the store with safe defaults.

## Glossary

- **MCC24**: Macbeth ColorChecker Classic with 24 color patches (6×4 grid); used as a color
  reference.
- **ColourChecker Passport**: Two MCC24 panels side-by-side (passport format).
- **CIEDE2000**: Perceptual color difference formula; lower ΔE = closer color match to reference.
- **ΔE**: Color difference metric in Lab space; ΔE 0 means identical colors.
- **CIE76 / CIE94**: Simpler/earlier ΔE formulas; less perceptually accurate than CIEDE2000.
- **CV_8UC4**: OpenCV matrix type: 8-bit unsigned, 4 channels (e.g., RGBA).
- **Laplacian variance**: Sharpness metric; higher values indicate sharper images.
- **Contrast score**: Heuristic for luminance spread; low contrast can signal glare/washout.
- **Pass thresholds**: Avg/max ΔE limits; used to decide if measured patches match references well
  enough.
