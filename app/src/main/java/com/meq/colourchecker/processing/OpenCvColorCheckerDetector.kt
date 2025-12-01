package com.meq.colourchecker.processing

import com.meq.colourchecker.util.Logger
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Loader
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Point2f
import org.bytedeco.opencv.opencv_core.Point2fVector
import org.bytedeco.opencv.opencv_core.Size
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * OpenCV ColorChecker detection orchestrator - main entry point for frame processing.
 *
 * This class coordinates the entire detection pipeline from raw RGBA camera frames
 * to final confidence scores and overlay coordinates. It orchestrates five specialized
 * components that handle different aspects of detection and validation.
 *
 * Pipeline Overview:
 * 1. Frame validation (dimensions, pixel buffer)
 * 2. Color space conversion (RGBA → BGR → Gray)
 * 3. Quality metrics (blur via Laplacian, contrast via std dev)
 * 4. MCC quad detection (full + split strategy via ColorCheckerLocator)
 * 5. Primary panel scoring (CIEDE2000 color validation via PatchAnalyzer)
 * 6. Secondary panel validation (grayscale panel detection)
 * 7. Confidence aggregation (area/aspect/blur/contrast/color via DetectionScorer)
 *
 * Dependencies:
 * - ColorCheckerLocator: Finds MCC24 quads using two-stage detection
 * - ImageQualityAnalyzer: Computes blur and contrast metrics
 * - PatchAnalyzer: Warps quads to 6×4 grid, samples patches, calculates CIEDE2000 ΔE
 * - DetectionScorer: Aggregates all metrics into confidence score and failure reason
 * - Logger: Debug output (Timber-backed in debug builds)
 *
 * Memory Management:
 * All OpenCV Mat objects use native C++ memory that must be explicitly released.
 * The finally block ensures proper cleanup even when exceptions occur.
 *
 * Thread Safety:
 * This detector is NOT thread-safe. CameraX ImageAnalysis ensures sequential calls.
 * OpenCV library loading uses double-checked locking for one-time initialization.
 */
class OpenCvColorCheckerDetector @Inject constructor(
    private val logger: Logger,
    private val locator: ColorCheckerLocator,
    private val qualityAnalyzer: ImageQualityAnalyzer,
    private val patchAnalyzer: PatchAnalyzer,
    private val scorer: DetectionScorer
) : ColorCheckerDetector {

    /**
     * Detects and validates ColorChecker panels in a camera frame.
     *
     * Processing Steps:
     * 1. Validate frame dimensions and pixel buffer
     * 2. Load OpenCV native libraries (one-time init)
     * 3. Convert RGBA to BGR (required by MCC detector)
     * 4. Convert BGR to grayscale and apply Gaussian blur for quality metrics
     * 5. Calculate blur (Laplacian variance) and contrast (std dev)
     * 6. Detect all ColorChecker quads using MCC module (full + split strategy)
     * 7. Sort quads by area, select largest as primary panel
     * 8. Score primary panel: warp to 6×4 grid, calculate CIEDE2000 ΔE for 24 patches
     * 9. Validate secondary panel if detected (grayscale checks)
     * 10. Aggregate metrics into confidence score and failure reason
     *
     * @param frame Camera frame with RGBA pixels, dimensions, and rotation
     * @return DetectionOutput with confidence, failure reason, and overlay coordinates
     */
    override suspend fun detect(frame: AnalysisFrame): DetectionOutput {
        // Step 1: Reject invalid frames (zero dimensions or missing pixels)
        if (!isValidFrame(frame)) {
            return DetectionOutput(
                result = DetectorResult(
                    confidence = 0f,
                    failureReason = FailureReason.NotFound,
                    needsInput = true  // User needs to provide valid frame
                ),
                metrics = null
            )
        }

        // Step 2: Ensure OpenCV native libraries are loaded (thread-safe, one-time)
        ensureLoaded()

        // Declare Mat variables for cleanup in finally block
        // All OpenCV Mat objects use native C++ memory that won't be garbage collected
        var rgba: Mat? = null
        var bgr: Mat? = null
        var gray: Mat? = null
        var quad: Mat? = null

        return try {
            // Step 3: Extract pixel buffer and validate presence
            // CameraX provides RGBA bytes in row-major order (4 bytes per pixel)
            val pixels = frame.rgbaPixels ?: return DetectionOutput(
                result = DetectorResult(
                    confidence = 0f,
                    failureReason = FailureReason.NotFound,
                    needsInput = true
                ),
                metrics = null
            )

            // Step 4: Wrap raw RGBA bytes into OpenCV Mat (zero-copy view)
            // CV_8UC4 = 8-bit unsigned, 4 channels (RGBA)
            // BytePointer wraps the byte array for native access
            rgba = Mat(
                frame.height,
                frame.width,
                opencv_core.CV_8UC4,
                BytePointer(ByteBuffer.wrap(pixels)) as org.bytedeco.javacpp.Pointer?
            )

            // Step 5: Convert RGBA to BGR (required by OpenCV MCC module)
            // BGR is OpenCV's native format for historical reasons (Windows BMP compatibility)
            bgr = Mat()
            opencv_imgproc.cvtColor(rgba, bgr, opencv_imgproc.COLOR_RGBA2BGR)

            // Step 6: Convert BGR to grayscale for quality metrics
            // Grayscale simplifies blur/contrast calculations (single channel vs 3)
            gray = Mat()
            opencv_imgproc.cvtColor(bgr, gray, opencv_imgproc.COLOR_BGR2GRAY)

            // Step 7: Apply Gaussian blur to grayscale for noise reduction
            // 5×5 kernel smooths noise before Laplacian edge detection
            // In-place operation (gray = output) saves memory allocation
            opencv_imgproc.GaussianBlur(gray, gray, Size(5, 5), 0.0)

            // Step 8: Calculate blur metric (Laplacian variance)
            // Low variance = blurry image (weak edges)
            // High variance = sharp image (strong edges)
            // Used to fail detections on motion blur or out-of-focus frames
            val lapVar = qualityAnalyzer.laplacianVariance(gray)

            // Step 9: Detect all ColorChecker quads using MCC module
            // Two-stage strategy: full image detection + left/right split detection
            // Returns 0-2 quads (0=not found, 1=single panel, 2=dual panel passport)
            val quads = locator.locateAll(bgr)
            logger.d("Detector: quads.size=${quads.size}, frame=${frame.width}x${frame.height}")

            // Step 10: Early exit if no quads detected
            if (quads.isEmpty()) {
                // Clean up Mat objects before returning (prevent memory leak)
                rgba?.release()
                bgr?.release()
                gray?.release()
                return DetectionOutput(
                    result = DetectorResult(
                        confidence = 0f,
                        failureReason = FailureReason.NotFound,
                        needsInput = false  // Detection ran successfully, just didn't find chart
                    ),
                    metrics = null
                )
            }

            // Step 11: Sort quads by area (width × height) in descending order
            // Largest quad = primary panel (color reference)
            // Second largest = secondary panel (grayscale reference, if present)
            // Sorting by area ensures we prioritize the most prominent chart
            val sorted = quads.sortedByDescending {
                locator.boundingBox(locator.pointVectorToList(it))
                    .let { box -> box.width * box.height }
            }
            val primaryVec: Point2fVector = sorted[0]
            val secondaryVec: Point2fVector? = sorted.getOrNull(1)

            // Step 12: Convert primary quad to Mat format for perspective transform
            // Point2fVector (native) → Mat (CV_32FC2) required by getPerspectiveTransform()
            quad = locator.pointVectorToMat(primaryVec)
            val primaryPoints = locator.pointVectorToList(primaryVec)

            // Step 13: Order primary quad corners as TL, TR, BR, BL
            // Consistent ordering is critical for correct perspective warp
            // Without ordering, warp could flip/rotate the chart incorrectly
            val orderedPrimary = GeometryUtils.orderPoints(primaryPoints)

            // Step 14: Score primary panel using CIEDE2000 color validation
            // Warps quad to 600×400 px, samples 6×4 grid centers, compares to MCC24 references
            // Returns average and max ΔE across 24 patches
            val primaryPatchScores = patchAnalyzer.scorePatches(bgr, quad)

            // Step 15: Calculate bounding box dimensions for area/aspect scoring
            val primaryBounds = locator.boundingBox(orderedPrimary)
            logger.d("Detector: primaryBounds=${primaryBounds.width}x${primaryBounds.height}")

            // Step 16: Handle secondary panel validation (if dual-panel passport detected)
            val secondaryOrderedPoints: List<Point>
            val secondaryValid: Boolean
            if (secondaryVec != null) {
                // Convert secondary quad to Mat and order corners
                val secondaryMat = locator.pointVectorToMat(secondaryVec)
                val secondaryPoints = locator.pointVectorToList(secondaryVec)
                val orderedSecondary = GeometryUtils.orderPoints(secondaryPoints)

                // Validate secondary panel using grayscale heuristics
                // Checks: avgChroma < 55, maxChroma < 90, monotonic luminance descent
                // This catches non-grayscale panels (colored objects, text, etc.)
                secondaryValid = patchAnalyzer.validateSecondaryPanel(bgr, secondaryMat)

                // Convert Point2f to Point for DetectionMetrics (overlay rendering)
                secondaryOrderedPoints = orderedSecondary.map { Point(it.x(), it.y()) }

                // Release temporary secondary Mat
                secondaryMat.release()

                val pointsStr = secondaryOrderedPoints.joinToString { "(${it.x},${it.y})" }
                logger.d("Detector: secondaryValid=$secondaryValid, points=$pointsStr")
            } else {
                // No secondary quad detected (single panel or detection missed second panel)
                secondaryValid = false
                secondaryOrderedPoints = emptyList()
                logger.d("Detector: no secondary quad available")
            }

            // Step 17: Aggregate all metrics into final confidence score and failure reason
            // DetectionScorer blends:
            // - Area score (boosted weight ×8) - larger chart = more confident
            // - Aspect ratio score - 1.5:1 expected (6 cols : 4 rows)
            // - Blur score - Laplacian variance threshold
            // - Contrast score - std dev / 64
            // - Color score - CIEDE2000 ΔE against MCC24 references
            // Returns confidence [0.0-1.0] and failure reason (Partial/Blur/Lighting/NotFound)
            return scorer.score(
                frameWidth = frame.width,
                frameHeight = frame.height,
                bounds = primaryBounds,
                laplacianVariance = lapVar,
                contrastScore = qualityAnalyzer.contrast(gray),
                colorScores = primaryPatchScores,
                expectedAspect = EXPECTED_ASPECT,
                blurRef = BLUR_REF,
                passAvgDeltaE = PASS_AVG_DELTA_E,
                passMaxDeltaE = PASS_MAX_DELTA_E,
                quad = orderedPrimary.map { Point(it.x(), it.y()) },
                rotationDegrees = frame.rotationDegrees,
                secondaryQuad = secondaryOrderedPoints,
                secondaryValid = secondaryValid
            )
        } catch (e: Exception) {
            // Catch any OpenCV or processing exceptions (rare but possible)
            // Examples: corrupt frame data, OpenCV internal errors, out of memory
            logger.e("OpenCV MCC detection failed: %s", e, e.message)
            DetectionOutput(
                result = DetectorResult(
                    confidence = 0f,
                    failureReason = FailureReason.NotFound,
                    needsInput = true  // Exception suggests frame or system issue
                ),
                metrics = null
            )
        } finally {
            // CRITICAL: Release all Mat objects to prevent native memory leaks
            // OpenCV Mat objects use C++ heap memory that won't be garbage collected
            // Failing to release Mats will cause memory accumulation (serious on Android)
            // Called even if exception occurs or early return happens
            quad?.release()
            rgba?.release()
            bgr?.release()
            gray?.release()
        }
    }

    /**
     * Validates frame has non-zero dimensions and sufficient pixel data.
     *
     * Why needed: CameraX can occasionally deliver invalid frames during:
     * - Camera startup/shutdown
     * - Configuration changes (rotation, resolution switch)
     * - Resource constraints (low memory)
     *
     * Validation prevents crashes from attempting to process malformed frames.
     *
     * @param frame Camera frame to validate
     * @return true if frame is valid and processable, false otherwise
     */
    private fun isValidFrame(frame: AnalysisFrame): Boolean {
        // Check for zero or negative dimensions (invalid frame)
        if (frame.width <= 0 || frame.height <= 0) return false

        // Check pixel buffer exists and has correct size
        // RGBA = 4 bytes per pixel, so total size = width × height × 4
        return frame.rgbaPixels != null && frame.rgbaPixels.size >= frame.width * frame.height * 4
    }

    /**
     * Ensures OpenCV native libraries are loaded exactly once.
     *
     * Why needed: OpenCV uses JNI to call native C++ code, requiring shared libraries
     * (.so on Android) to be loaded before any OpenCV calls. Loading is expensive
     * (~50-200ms) so we do it once and cache the result.
     *
     * Thread safety: Double-checked locking pattern ensures only one thread loads
     * the libraries even with concurrent detect() calls from multiple threads.
     *
     * Implementation:
     * 1. Volatile flag for memory visibility across threads
     * 2. First check without lock (fast path for already-loaded case)
     * 3. Synchronized block for actual loading (slow path, once only)
     * 4. Second check inside lock to prevent duplicate loading
     */
    private fun ensureLoaded() {
        // Fast path: library already loaded (no synchronization needed)
        if (!loaded) {
            // Slow path: acquire lock for one-time loading
            synchronized(this) {
                // Double-check pattern: another thread may have loaded while we waited
                if (!loaded) {
                    // Load OpenCV core library (includes imgproc, mcc modules)
                    // This extracts native .so files from JAR to temp directory and loads them
                    Loader.load(opencv_core::class.java)
                    loaded = true
                }
            }
        }
    }

    companion object {
        /**
         * Volatile flag ensures memory visibility of library loading across threads.
         * Without volatile, a thread might see stale false value after another thread sets true.
         */
        @Volatile
        private var loaded = false

        /**
         * Expected aspect ratio for MCC24 ColorChecker (6 columns : 4 rows = 1.5:1).
         * Used to score detection confidence - deviation from 1.5 reduces aspect score.
         */
        private const val EXPECTED_ASPECT = 1.5

        /**
         * Blur reference threshold for Laplacian variance.
         * Values below 120.0 indicate blurry/out-of-focus images and trigger Blur failure.
         * Tuned empirically across multiple devices and lighting conditions.
         */
        private const val BLUR_REF = 120.0

        /**
         * Pass threshold for average CIEDE2000 ΔE across 24 patches.
         * ΔE < 120 = good color match to MCC24 references.
         * Relaxed threshold (vs lab-grade ~10) accommodates real-world lighting variations.
         */
        private const val PASS_AVG_DELTA_E = 120.0

        /**
         * Pass threshold for maximum CIEDE2000 ΔE among 24 patches.
         * ΔE < 180 = worst patch still acceptable.
         * Prevents outliers (glare on one patch) from failing entire detection.
         */
        private const val PASS_MAX_DELTA_E = 180.0
    }
}
