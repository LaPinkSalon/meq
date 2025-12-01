package com.meq.colourchecker.camera

import androidx.camera.core.ImageProxy

/**
 * Converts YUV_420_888 image data to an RGBA byte array (row-major).
 * This is a simple CPU conversion suitable for prototype purposes.
 */
fun ImageProxy.toRgbaByteArray(): ByteArray {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val yRowStride = planes[0].rowStride
    val uvRowStride = planes[1].rowStride
    val uvPixelStride = planes[1].pixelStride

    val output = ByteArray(width * height * 4)
    var outputIndex = 0

    for (y in 0 until height) {
        val yRow = yRowStride * y
        val uvRow = (y / 2) * uvRowStride
        for (x in 0 until width) {
            val yValue = (yBuffer.get(yRow + x).toInt() and 0xFF)
            val uvIndex = uvRow + (x / 2) * uvPixelStride
            val u = (uBuffer.get(uvIndex).toInt() and 0xFF) - 128
            val v = (vBuffer.get(uvIndex).toInt() and 0xFF) - 128

            var r = (yValue + 1.370705f * v).toInt()
            var g = (yValue - 0.337633f * u - 0.698001f * v).toInt()
            var b = (yValue + 1.732446f * u).toInt()

            r = r.coerceIn(0, 255)
            g = g.coerceIn(0, 255)
            b = b.coerceIn(0, 255)

            output[outputIndex++] = r.toByte()
            output[outputIndex++] = g.toByte()
            output[outputIndex++] = b.toByte()
            output[outputIndex++] = 0xFF.toByte() // alpha
        }
    }

    return output
}
