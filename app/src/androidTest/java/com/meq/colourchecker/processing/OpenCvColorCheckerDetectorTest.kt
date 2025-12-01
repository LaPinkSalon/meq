package com.meq.colourchecker.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meq.colourchecker.util.TimberLogger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

/**
 * Integration check that runs the detector on a real image asset
 * (two-panel ColourChecker photo) to verify a pass.
 */
@RunWith(AndroidJUnit4::class)
class OpenCvColorCheckerDetectorTest {

    @Test
    fun assetPassportImagePasses() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        timber.log.Timber.plant(timber.log.Timber.DebugTree())
        val bytes = ctx.assets.open("spyder_passport.png").use { it.readBytes() }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Failed to decode spyder_passport.png from assets")

        val rgba = bitmapToRgba(bitmap)
        val frame = AnalysisFrame(
            width = bitmap.width,
            height = bitmap.height,
            rotationDegrees = 0,
            rgbaPixels = rgba
        )

        val logger = TimberLogger()
        val detector = OpenCvColorCheckerDetector(
            logger = logger,
            locator = ColorCheckerLocator(logger),
            qualityAnalyzer = ImageQualityAnalyzer(),
            patchAnalyzer = PatchAnalyzer(),
            scorer = DetectionScorer()
        )

        val output = detector.detect(frame)
        val msg = "Detector output: confidence=${output.result.confidence} failure=${output.result.failureReason}"
        assertTrue(msg, output.result.passed)
        assertNull(msg, output.result.failureReason)
        assertTrue(msg, output.result.confidence >= 0.7f)
    }

    @Test
    fun assetBlockedPassportFails() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val bytes = ctx.assets.open("fail.png").use { it.readBytes() }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Failed to decode fail.png from assets")

        val rgba = bitmapToRgba(bitmap)
        val frame = AnalysisFrame(
            width = bitmap.width,
            height = bitmap.height,
            rotationDegrees = 0,
            rgbaPixels = rgba
        )

        val logger = TimberLogger()
        val detector = OpenCvColorCheckerDetector(
            logger = logger,
            locator = ColorCheckerLocator(logger),
            qualityAnalyzer = ImageQualityAnalyzer(),
            patchAnalyzer = PatchAnalyzer(),
            scorer = DetectionScorer()
        )

        val output = detector.detect(frame)
        val metrics = output.metrics
        val msg = "Detector output: confidence=${output.result.confidence} failure=${output.result.failureReason} " +
                "avgDeltaE=${metrics?.avgDeltaE} maxDeltaE=${metrics?.maxDeltaE} " +
                "areaScore=${metrics?.areaScore} contrastScore=${metrics?.contrastScore} blurScore=${metrics?.blurScore}"
        timber.log.Timber.d(msg)
        assertFalse(msg, output.result.passed)
    }

    @Test
    fun singlePanelCropFails() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val bytes = ctx.assets.open("single_panel.jpeg").use { it.readBytes() }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Failed to decode single_panel.jpeg from assets")

        // Crop to a single panel (right half) to simulate a one-sided chart.
        val singlePanel = Bitmap.createBitmap(
            bitmap,
            bitmap.width / 2,
            0,
            bitmap.width / 2,
            bitmap.height
        )

        val rgba = bitmapToRgba(singlePanel)
        val frame = AnalysisFrame(
            width = singlePanel.width,
            height = singlePanel.height,
            rotationDegrees = 0,
            rgbaPixels = rgba
        )

        val logger = TimberLogger()
        val detector = OpenCvColorCheckerDetector(
            logger = logger,
            locator = ColorCheckerLocator(logger),
            qualityAnalyzer = ImageQualityAnalyzer(),
            patchAnalyzer = PatchAnalyzer(),
            scorer = DetectionScorer()
        )

        val output = detector.detect(frame)
        val msg = "Detector output: confidence=${output.result.confidence} failure=${output.result.failureReason}"
        assertFalse(msg, output.result.passed)
    }

    private fun bitmapToRgba(bitmap: Bitmap): ByteArray {
        // Ensure we are working with ARGB_8888 and explicitly expand to RGBA bytes.
        val bmp = if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)

        val out = ByteArray(pixels.size * 4)
        pixels.forEachIndexed { i, argb ->
            out[i * 4] = ((argb shr 16) and 0xFF).toByte() // R
            out[i * 4 + 1] = ((argb shr 8) and 0xFF).toByte() // G
            out[i * 4 + 2] = (argb and 0xFF).toByte() // B
            out[i * 4 + 3] = ((argb shr 24) and 0xFF).toByte() // A
        }
        return out
    }
}
