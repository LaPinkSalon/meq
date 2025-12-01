package com.meq.colourchecker.processing

import com.meq.colourchecker.util.Logger
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_mcc
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Point2f
import org.bytedeco.opencv.opencv_core.Point2fVector
import org.bytedeco.opencv.opencv_core.Rect
import org.bytedeco.opencv.opencv_core.Size
import javax.inject.Inject
import org.bytedeco.opencv.opencv_mcc.CCheckerDetector

/**
 * Finds ColorChecker chart quads using OpenCV's MCC (Macbeth ColorChecker) module.
 *
 * Uses a two-stage detection strategy:
 * 1. Full image detection (catches both panels if close together)
 * 2. Split detection (left/right halves) to catch widely separated panels
 *
 * This approach handles both single-panel and dual-panel ColorChecker Passports.
 */
open class ColorCheckerLocator @Inject constructor(
    private val logger: Logger
) {
    /**
     * Detects all ColorChecker quads in the BGR image.
     *
     * Detection Strategy:
     * - Stage 1: Run MCC on full image (handles most cases: single panel or close dual panels)
     * - Stage 2: If < 2 quads found, split image in half and detect separately
     * - This catches widely separated dual panels that MCC might miss in full image
     * - Aggregate results and deduplicate overlapping detections (same quad detected twice)
     *
     * @param bgr OpenCV Mat in BGR format (required by MCC module)
     * @return List of quads, each represented as 4 corner points (Point2fVector)
     */
    open fun locateAll(bgr: Mat): List<Point2fVector> {
        val quads = mutableListOf<Point2fVector>()

        // Stage 1: Detect on full image
        // This is fastest and handles most cases (single panel or close dual panels)
        quads += detectMcc(bgr, "full")
        if (quads.size >= 2) return quads  // Early exit: found both panels, no need for split

        // Stage 2: Split detection for widely separated dual panels
        // Sometimes MCC misses one panel when they're far apart horizontally
        // Splitting gives MCC a better chance to detect each panel independently
        val midX = bgr.cols() / 2
        val left = Rect(0, 0, midX, bgr.rows())  // Left half ROI
        val right = Rect(midX, 0, bgr.cols() - midX, bgr.rows())  // Right half ROI

        // Create sub-matrices (views) for each half - no data copy, just references
        val leftMat = Mat(bgr, left)
        val rightMat = Mat(bgr, right)

        // Detect in each half, then offset coordinates back to full image space
        // offsetQuad shifts local coordinates (0,0 = ROI top-left) to global coordinates
        val leftQuads = detectMcc(leftMat, "left").map { offsetQuad(it, left.x(), left.y()) }
        val rightQuads = detectMcc(rightMat, "right").map { offsetQuad(it, right.x(), right.y()) }

        // Release sub-matrix views (lightweight - doesn't free original data)
        leftMat.release()
        rightMat.release()

        // Aggregate all detections (full + left + right)
        quads += leftQuads
        quads += rightQuads

        // Remove duplicates: same quad may be detected in both full and split passes
        // Uses 40px distance threshold to identify duplicates
        val deduped = deduplicateQuads(quads)
        logger.d("MCC detector: aggregated ${deduped.size} quads (full+splits)")
        return deduped
    }

    /**
     * Calculates axis-aligned bounding box from quad corner points.
     *
     * Used for area and aspect ratio calculations in scoring.
     *
     * @param points List of corner points (typically 4 for a quad)
     * @return BoundingBox with width and height in pixels
     */
    fun boundingBox(points: List<Point2f>): BoundingBox {
        // Extract all X and Y coordinates
        val xs = points.map { it.x().toDouble() }
        val ys = points.map { it.y().toDouble() }

        // Bounding box = (max - min) for both dimensions
        val width = (xs.maxOrNull() ?: 0.0) - (xs.minOrNull() ?: 0.0)
        val height = (ys.maxOrNull() ?: 0.0) - (ys.minOrNull() ?: 0.0)

        return BoundingBox(width = width.coerceAtLeast(0.0), height = height.coerceAtLeast(0.0))
    }

    /**
     * Converts OpenCV Point2fVector (native) to Mat format required by warpPerspective.
     *
     * Creates a 4x1 matrix with CV_32FC2 type (32-bit float, 2 channels for x,y).
     * This format is required by OpenCV's perspective transform functions.
     *
     * @param vec Point2fVector with 4 corner points
     * @return Mat suitable for cv::getPerspectiveTransform()
     */
    fun pointVectorToMat(vec: Point2fVector): Mat {
        // Create 4x1 matrix, 2 channels (x, y), 32-bit float
        val mat = Mat(4, 1, opencv_core.CV_32FC2)
        val idx = mat.createIndexer() as org.bytedeco.javacpp.indexer.FloatIndexer

        // Copy each point's x,y coordinates into the matrix
        for (i in 0 until 4) {
            val p = vec.get(i.toLong())
            idx.put(i.toLong(), 0L, p.x(), p.y())  // Row i, col 0, channels (x, y)
        }

        // Release indexer (doesn't free Mat, just the accessor)
        idx.release()
        return mat
    }

    /**
     * Converts OpenCV Point2fVector to Kotlin List for easier manipulation.
     *
     * @param vec Native OpenCV Point2fVector
     * @return Kotlin List<Point2f>
     */
    fun pointVectorToList(vec: Point2fVector): List<Point2f> {
        val list = ArrayList<Point2f>(vec.size().toInt())
        for (i in 0 until vec.size().toInt()) {
            list.add(vec.get(i.toLong()))
        }
        return list
    }

    /**
     * Runs OpenCV MCC (Macbeth ColorChecker) detection on a BGR image.
     *
     * How it works:
     * 1. Creates CCheckerDetector instance
     * 2. Calls process() with MCC24 type (6x4 grid, 24 color patches)
     * 3. Retrieves detected checkers and extracts quad corner points
     * 4. Returns list of quads (4 points each) found in the image
     *
     * MCC detection is robust to rotation, scaling, and mild perspective distortion.
     *
     * @param bgr BGR image to search (OpenCV format)
     * @param label Debug label for logging (e.g., "full", "left", "right")
     * @return List of detected quads (Point2fVector with 4 corners each)
     */
    private fun detectMcc(bgr: Mat, label: String): List<Point2fVector> {
        val detector = CCheckerDetector.create()
        return try {
            // Run MCC detection for MCC24 type (24-patch ColorChecker)
            val found = detector.process(bgr, opencv_mcc.MCC24)
            val checkers = detector.getListColorChecker()

            // Check if detection succeeded
            if (!found || checkers == null || checkers.size() == 0L) {
                logger.d("MCC detector[$label]: no chart found (found=$found, list=${checkers?.size() ?: 0})")
                emptyList()
            } else {
                // Extract quad corners from each detected checker
                buildList {
                    for (i in 0 until checkers.size().toInt()) {
                        // getBox() returns 4 corner points of the detected quad
                        val quadVec = checkers.get(i.toLong()).getBox()
                        if (quadVec != null && quadVec.size() >= 4) add(quadVec)
                    }
                }.also {
                    logger.d("MCC detector[$label]: found ${it.size} quads from ${checkers.size()} checkers")
                }
            }
        } finally {
            // CRITICAL: Deallocate native detector object to prevent memory leak
            // CCheckerDetector uses native C++ memory that won't be GC'd
            detector.deallocate()
        }
    }

    /**
     * Translates quad coordinates by (dx, dy) offset.
     *
     * Used when detecting in ROIs (sub-regions) to map local coordinates
     * back to full image space.
     *
     * Example: If detecting in right half starting at x=960,
     * a point at local (50, 100) becomes global (1010, 100).
     *
     * @param vec Quad in local (ROI) coordinate space
     * @param dx X offset to add (ROI.x)
     * @param dy Y offset to add (ROI.y)
     * @return New quad in global coordinate space
     */
    private fun offsetQuad(vec: Point2fVector, dx: Int, dy: Int): Point2fVector {
        val out = Point2fVector(vec.size())
        for (i in 0 until vec.size().toInt()) {
            val p = vec.get(i.toLong())
            // Shift point by ROI offset
            out.put(i.toLong(), Point2f(p.x() + dx, p.y() + dy))
        }
        return out
    }

    /**
     * Removes duplicate quads from detection list.
     *
     * Why needed: Full-image and split-image detections may find the same quad twice.
     * For example, a quad near the center might be detected in both full and left/right halves.
     *
     * Algorithm: For each candidate, check if any existing quad is within threshold distance.
     * If similar quad already exists (avg corner distance < 40px), skip it.
     *
     * @param quads List of potentially overlapping quads
     * @param threshold Distance threshold in pixels (default 40px)
     * @return Deduplicated list of unique quads
     */
    private fun deduplicateQuads(
        quads: List<Point2fVector>,
        threshold: Double = 40.0
    ): List<Point2fVector> {
        val result = mutableListOf<Point2fVector>()
        quads.forEach { candidate ->
            // Check if this quad is similar to any already-accepted quad
            val isNew = result.none { existing ->
                averageCornerDistance(candidate, existing) < threshold
            }
            if (isNew) result.add(candidate)  // No similar quad exists, add it
        }
        return result
    }

    /**
     * Calculates average Euclidean distance between corresponding corners of two quads.
     *
     * Used to determine if two quads are the same (duplicate detection).
     *
     * Distance calculation:
     * - For each corner pair (i): distance = sqrt((x1-x2)² + (y1-y2)²)
     * - Return average of all 4 corner distances
     *
     * Small average distance (<40px) = same quad detected twice
     * Large distance = different quads
     *
     * @param a First quad
     * @param b Second quad
     * @return Average distance between corresponding corners (pixels)
     */
    private fun averageCornerDistance(a: Point2fVector, b: Point2fVector): Double {
        // Safety check: need at least 4 corners for a valid quad
        if (a.size() < 4 || b.size() < 4) return Double.MAX_VALUE

        var sum = 0.0
        val count = minOf(a.size().toInt(), b.size().toInt())

        // Sum Euclidean distances for each corner pair
        for (i in 0 until count) {
            val pa = a.get(i.toLong())
            val pb = b.get(i.toLong())
            val dx = pa.x() - pb.x()
            val dy = pa.y() - pb.y()
            sum += kotlin.math.sqrt(dx * dx + dy * dy)  // Euclidean distance
        }

        // Return average distance across all corners
        return sum / count
    }

}
