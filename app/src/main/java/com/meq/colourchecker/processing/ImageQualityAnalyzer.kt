package com.meq.colourchecker.processing

import org.bytedeco.javacpp.indexer.DoubleIndexer
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.opencv_core.Mat
import javax.inject.Inject

class ImageQualityAnalyzer @Inject constructor() {
    fun contrast(gray: Mat): Double {
        val mean = Mat()
        val std = Mat()
        opencv_core.meanStdDev(gray, mean, std)
        val idx = std.createIndexer() as DoubleIndexer
        val stdVal = idx.get(0, 0)
        idx.release()
        mean.release()
        std.release()
        return (stdVal / 64.0).coerceIn(0.0, 1.0)
    }

    fun laplacianVariance(gray: Mat): Double {
        val lap = Mat()
        opencv_imgproc.Laplacian(gray, lap, opencv_core.CV_64F)
        val mean = Mat()
        val std = Mat()
        opencv_core.meanStdDev(lap, mean, std)
        val idx = std.createIndexer() as DoubleIndexer
        val stdVal = idx.get(0, 0)
        idx.release()
        mean.release()
        lap.release()
        std.release()
        return stdVal * stdVal
    }
}
