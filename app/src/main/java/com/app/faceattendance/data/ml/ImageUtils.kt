package com.app.faceattendance.data.ml

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import kotlin.math.max
import kotlin.math.min

object ImageUtils {

    const val TARGET_FACE_SIZE = 112

    @OptIn(ExperimentalGetImage::class)
    fun imageProxyToUprightBitmap(imageProxy: ImageProxy, isFrontCamera: Boolean = true): Bitmap {
        val rawBitmap = imageProxy.toBitmap()
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        if (rotationDegrees == 0 && !isFrontCamera) return rawBitmap

        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            if (isFrontCamera) postScale(-1f, 1f)
        }

        return Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
    }

    fun cropFaceBitmap(sourceBitmap: Bitmap, boundingBox: Rect, marginPercent: Float = 0.10f): Bitmap {
        val widthMargin = (boundingBox.width() * marginPercent).toInt()
        val heightMargin = (boundingBox.height() * marginPercent).toInt()

        val left = max(0, boundingBox.left - widthMargin)
        val top = max(0, boundingBox.top - heightMargin)
        val right = min(sourceBitmap.width, boundingBox.right + widthMargin)
        val bottom = min(sourceBitmap.height, boundingBox.bottom + heightMargin)

        val cropWidth = right - left
        val cropHeight = bottom - top

        if (cropWidth <= 0 || cropHeight <= 0) {
            return Bitmap.createScaledBitmap(sourceBitmap, TARGET_FACE_SIZE, TARGET_FACE_SIZE, true)
        }

        val cropped = Bitmap.createBitmap(sourceBitmap, left, top, cropWidth, cropHeight)
        return Bitmap.createScaledBitmap(cropped, TARGET_FACE_SIZE, TARGET_FACE_SIZE, true)
    }

    fun calculateLuminance(imageProxy: ImageProxy): Double {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        var sum = 0L
        var i = 0
        while (i < data.size) {
            sum += data[i].toInt() and 0xFF
            i += 8
        }

        val pixelCount = data.size / 8
        return if (pixelCount > 0) sum.toDouble() / pixelCount else 0.0
    }
}
