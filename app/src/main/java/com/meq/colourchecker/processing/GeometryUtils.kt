package com.meq.colourchecker.processing

import org.bytedeco.opencv.opencv_core.Point2f

object GeometryUtils {
    /**
        * Orders four points in top-left, top-right, bottom-right, bottom-left sequence.
        */
    fun orderPoints(points: List<Point2f>): List<Point2f> {
        val tl = points.minByOrNull { it.x() + it.y() } ?: points.first()
        val br = points.maxByOrNull { it.x() + it.y() } ?: points.last()
        val remaining = points - tl - br
        val tr = remaining.maxByOrNull { it.x() - it.y() } ?: points.first()
        val bl = remaining.minByOrNull { it.x() - it.y() } ?: points.last()
        return listOf(tl, tr, br, bl)
    }
}
