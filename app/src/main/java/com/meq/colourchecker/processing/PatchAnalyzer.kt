package com.meq.colourchecker.processing

import org.bytedeco.javacpp.indexer.DoubleIndexer
import org.bytedeco.javacpp.indexer.FloatIndexer
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Point2f
import org.bytedeco.opencv.opencv_core.Rect
import org.bytedeco.opencv.opencv_core.Size
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Normalizes detected MCC quads, samples patch centers, and computes ΔE00
 * against the MCC24 reference set. Secondary panel validation reuses the
 * same grid with looser chroma/luminance heuristics.
 */
class PatchAnalyzer @Inject constructor() {
    fun scorePatches(bgr: Mat, quad: Mat): PatchScores {
        val samples = samplePatches(bgr, quad)
        val deltas = samples.mapIndexed { idx, sample ->
            val reference = REFERENCE_LABS[idx]
            deltaE2000(Triple(sample.L, sample.a, sample.b), reference)
        }
        val avg = deltas.average()
        val max = deltas.maxOrNull() ?: avg
        return PatchScores(avg, max)
    }

    fun validateSecondaryPanel(bgr: Mat, quad: Mat): Boolean {
        // Validate secondary panel with 6x4 sampling (same grid), but looser chroma to allow skin/utility tones.
        val samples = samplePatches(bgr, quad)
        val chromas = samples.map { sqrt(it.a * it.a + it.b * it.b) }
        val luminanceRows = samples.chunked(6).map { row -> row.map { it.L } }
        val avgChroma = chromas.average()
        val maxChroma = chromas.maxOrNull() ?: avgChroma
        val rowMeans = luminanceRows.map { it.average() }
        val monotonic = rowMeans.zipWithNext().all { (a, b) -> a >= b - 2.0 }

        // Allow color, but reject highly saturated/occluded cases; require rough luminance ordering.
        return avgChroma < 55.0 && maxChroma < 90.0 && monotonic
    }


    private fun warpToChart(bgr: Mat, quad: Mat): Mat {
        val points = quadToPoints(quad)
        val ordered = GeometryUtils.orderPoints(points) // Ensure TL, TR, BR, BL for perspective transform.

        val src = Mat(4, 1, opencv_core.CV_32FC2)
        val srcIdx = src.createIndexer() as FloatIndexer
        ordered.forEachIndexed { i, p -> srcIdx.put(i.toLong(), 0L, p.x(), p.y()) }
        srcIdx.release()

        val dst = Mat(4, 1, opencv_core.CV_32FC2)
        val dstIdx = dst.createIndexer() as FloatIndexer
        dstIdx.put(0L, 0L, 0f, 0f)
        dstIdx.put(1L, 0L, WARP_WIDTH.toFloat(), 0f)
        dstIdx.put(2L, 0L, WARP_WIDTH.toFloat(), WARP_HEIGHT.toFloat())
        dstIdx.put(3L, 0L, 0f, WARP_HEIGHT.toFloat())
        dstIdx.release()

        val transform = opencv_imgproc.getPerspectiveTransform(src, dst)
        val warped = Mat()
        opencv_imgproc.warpPerspective(bgr, warped, transform, Size(WARP_WIDTH, WARP_HEIGHT))
        transform.release()
        src.release()
        dst.release()
        return warped
    }

    private fun quadToPoints(quad: Mat): List<Point2f> {
        // Quad is always CV_32FC2 from pointVectorToMat, so just use it directly
        val idx = quad.createIndexer() as FloatIndexer
        val points = (0 until quad.rows()).map { i ->
            Point2f(idx.get(i.toLong(), 0L, 0L), idx.get(i.toLong(), 0L, 1L))
        }
        idx.release()
        return points
    }

    private data class PatchSample(val L: Double, val a: Double, val b: Double)

    private fun samplePatches(bgr: Mat, quad: Mat): List<PatchSample> {
        val warped = warpToChart(bgr, quad)
        val lab = Mat()
        opencv_imgproc.cvtColor(warped, lab, opencv_imgproc.COLOR_BGR2Lab)

        // Chart is warped to 600x400; MCC layout is 6 columns by 4 rows.
        val cellWidth = WARP_WIDTH / 6
        val cellHeight = WARP_HEIGHT / 4
        val samples = mutableListOf<PatchSample>()

        for (row in 0 until 4) {
            for (col in 0 until 6) {
                // Sample a small center ROI to avoid patch borders and bleed.
                val x = col * cellWidth + cellWidth / 4
                val y = row * cellHeight + cellHeight / 4
                val roi = Rect(
                    x, y, (cellWidth / 2).coerceAtLeast(4), (cellHeight / 2).coerceAtLeast(4)
                )
                val patch = Mat(lab, roi)
                val mean = Mat()
                val std = Mat()
                opencv_core.meanStdDev(patch, mean, std)
                val idx = mean.createIndexer() as DoubleIndexer
                samples.add(PatchSample(idx.get(0, 0), idx.get(1, 0), idx.get(2, 0)))
                idx.release()
                mean.release()
                std.release()
                patch.release()
            }
        }

        lab.release()
        warped.release()
        return samples
    }

    private fun deltaE2000(
        measured: Triple<Double, Double, Double>, reference: Triple<Double, Double, Double>
    ): Double {
        val (L1, a1, b1) = measured
        val (L2, a2, b2) = reference

        val avgLp = (L1 + L2) / 2.0
        val c1 = sqrt(a1 * a1 + b1 * b1)
        val c2 = sqrt(a2 * a2 + b2 * b2)
        val avgC = (c1 + c2) / 2.0

        // Standard CIEDE2000 constants/terms; keep inlined for clarity.
        val g = 0.5 * (1 - sqrt(avgC.pow(7) / (avgC.pow(7) + 25.0.pow(7))))
        val a1p = (1 + g) * a1
        val a2p = (1 + g) * a2
        val c1p = sqrt(a1p * a1p + b1 * b1)
        val c2p = sqrt(a2p * a2p + b2 * b2)

        val h1p = atan2Safe(b1, a1p)
        val h2p = atan2Safe(b2, a2p)

        val deltaLp = L2 - L1
        val deltaCp = c2p - c1p

        val deltahp = when {
            c1p * c2p == 0.0 -> 0.0
            kotlin.math.abs(h2p - h1p) <= Math.PI -> h2p - h1p
            h2p - h1p > Math.PI -> h2p - h1p - 2 * Math.PI
            else -> h2p - h1p + 2 * Math.PI
        }
        val deltaHp = 2 * sqrt(c1p * c2p) * sin(deltahp / 2.0)

        val avgLpTerm = avgLp - 50
        val sl = 1 + (0.015 * avgLpTerm * avgLpTerm) / sqrt(20 + avgLpTerm * avgLpTerm)
        val sc = 1 + 0.045 * ((c1p + c2p) / 2.0)

        val hpSum = h1p + h2p
        val avgHp = when {
            c1p * c2p == 0.0 -> hpSum
            kotlin.math.abs(h1p - h2p) <= Math.PI -> hpSum / 2.0
            hpSum < 2 * Math.PI -> (hpSum + 2 * Math.PI) / 2.0
            else -> (hpSum - 2 * Math.PI) / 2.0
        }

        val t = 1 - 0.17 * cos(avgHp - Math.toRadians(30.0)) + 0.24 * cos(2 * avgHp) + 0.32 * cos(
            3 * avgHp + Math.toRadians(6.0)
        ) - 0.20 * cos(4 * avgHp - Math.toRadians(63.0))

        val sh = 1 + 0.015 * ((c1p + c2p) / 2.0) * t

        val deltaTheta =
            Math.toRadians(30.0) * exp(-((avgHp - Math.toRadians(275.0)) / Math.toRadians(25.0)).let { it * it })
        val avgCp = (c1p + c2p) / 2.0
        val rc = 2 * sqrt(avgCp.pow(7) / (avgCp.pow(7) + 25.0.pow(7)))
        val rt = -rc * sin(2 * deltaTheta)

        return sqrt(
            (deltaLp / sl) * (deltaLp / sl) + (deltaCp / sc) * (deltaCp / sc) + (deltaHp / sh) * (deltaHp / sh) + rt * (deltaCp / sc) * (deltaHp / sh)
        )
    }

    private fun atan2Safe(y: Double, x: Double): Double {
        val angle = atan2(y, x)
        return if (angle < 0) angle + 2 * Math.PI else angle
    }

    companion object {
        private const val WARP_WIDTH = 600
        private const val WARP_HEIGHT = 400
        private val REFERENCE_LABS: List<Triple<Double, Double, Double>> = listOf(
            Triple(37.986, 13.555, 14.059), // Dark Skin
            Triple(65.711, 18.13, 17.81),  // Light Skin
            Triple(49.927, -4.88, -21.925),// Blue Sky
            Triple(43.139, -13.095, 21.905),// Foliage
            Triple(55.112, 8.844, -25.399), // Blue Flower
            Triple(70.719, -33.395, -0.199),// Bluish Green
            Triple(62.661, 36.067, 57.096), // Orange
            Triple(40.02, 10.41, -45.964),  // Purplish Blue
            Triple(51.124, 48.239, 16.248), // Moderate Red
            Triple(30.325, 22.976, -21.587),// Purple
            Triple(72.532, -23.709, 57.255),// Yellow Green
            Triple(71.941, 19.363, 67.857), // Orange Yellow
            Triple(28.778, 14.179, -50.297),// Blue
            Triple(55.261, -38.342, 31.37), // Green
            Triple(42.101, 53.378, 28.19),  // Red
            Triple(81.733, 4.039, 79.819),  // Yellow
            Triple(51.935, 49.986, -14.574),// Magenta
            Triple(51.038, -28.631, -28.638),// Cyan
            Triple(96.539, -0.425, 1.186),  // White 9.5 (.05 D)
            Triple(81.257, -0.638, -0.335), // Neutral 8 (.23 D)
            Triple(66.766, -0.734, -0.504), // Neutral 6.5 (.44 D)
            Triple(50.867, -0.153, -0.27),  // Neutral 5 (.70 D)
            Triple(35.656, -0.421, -1.231), // Neutral 3.5 (1.05 D)
            Triple(20.461, -0.079, -0.973)  // Black 2 (1.50 D)
        )
    }
}
