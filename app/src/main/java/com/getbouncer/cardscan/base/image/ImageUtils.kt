package com.getbouncer.cardscan.base.image

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ConfigurationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Size
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import kotlin.math.roundToInt

private const val DIM_PIXEL_SIZE = 3
private const val NUM_BYTES_PER_CHANNEL = 4 // Float.size / Byte.size

/**
 * Pixel3: 149.71 FPS, 6.68 MS/F
 */
fun ImageProxy.toBitmap(
    crop: Rect = Rect(0, 0, this.width, this.height),
    quality: Int = 85
): Bitmap {
    val yBuffer = planes[0].buffer // Y
    val uBuffer = planes[1].buffer // U
    val vBuffer = planes[2].buffer // V

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    //U and V are swapped
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(crop, quality, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

/**
 * Pixel3: 39.10 FPS, 25.58 MS/F
 */
fun Bitmap.toRGBByteBuffer(mean: Float = 0F, std: Float = 255F): ByteBuffer {
    val argb = IntArray(width * height).also {
        getPixels(it, 0, width, 0, 0, width, height)
    }

    val rgbFloat = ByteBuffer.allocateDirect(this.width * this.height * DIM_PIXEL_SIZE * NUM_BYTES_PER_CHANNEL)
    rgbFloat.order(ByteOrder.nativeOrder())
    argb.forEach {
        // ignore the alpha value ((it shr 24 and 0xFF) - mean) / std)
        rgbFloat.putFloat(((it shr 16 and 0xFF) - mean) / std)
        rgbFloat.putFloat(((it shr 8 and 0xFF) - mean) / std)
        rgbFloat.putFloat(((it and 0xFF) - mean) / std)
    }

    rgbFloat.rewind()
    return rgbFloat
}

data class ImageTransformValues(val red: Float, val green: Float, val blue: Float)

fun Bitmap.toRGBByteBuffer(mean: ImageTransformValues, std: ImageTransformValues): ByteBuffer {
    val argb = IntArray(width * height).also {
        getPixels(it, 0, width, 0, 0, width, height)
    }

    val rgbFloat = ByteBuffer.allocateDirect(this.width * this.height * DIM_PIXEL_SIZE * NUM_BYTES_PER_CHANNEL)
    rgbFloat.order(ByteOrder.nativeOrder())
    argb.forEach {
        // ignore the alpha value ((it shr 24 and 0xFF) - mean) / std)
        rgbFloat.putFloat(((it shr 16 and 0xFF) - mean.red) / std.red)
        rgbFloat.putFloat(((it shr 8 and 0xFF) - mean.green) / std.green)
        rgbFloat.putFloat(((it and 0xFF) - mean.blue) / std.blue)
    }

    rgbFloat.rewind()
    return rgbFloat
}

fun hasOpenGl31(context: Context): Boolean {
    val openGlVersion = 0x00030001
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val configInfo = activityManager.deviceConfigurationInfo

    return if (configInfo.reqGlEsVersion != ConfigurationInfo.GL_ES_VERSION_UNDEFINED) {
        configInfo.reqGlEsVersion >= openGlVersion
    } else {
        false
    }
}

/**
 * Pixel3: 31.96 FPS, 31.29 MS/F
 */
fun ByteBuffer.rbgaToBitmap(size: Size, mean: Float = 0F, std: Float = 255F): Bitmap {
    this.rewind()
    assert(this.limit() == size.width * size.height) { "ByteBuffer limit does not match expected size" }
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val rgba = IntBuffer.allocate(size.width * size.height)
    while (this.hasRemaining()) {
        rgba.put(
            (0xFF shl 24)  // set 0xFF for the alpha value
            + (((this.float * std) + mean).roundToInt())
            + (((this.float * std) + mean).roundToInt() shl 8)
            + (((this.float * std) + mean).roundToInt() shl 16)
        )
    }
    rgba.rewind()
    bitmap.copyPixelsFromBuffer(rgba)
    return bitmap
}

/**
 * Pixel3: 1739.62 FPS, 0.57 MS/F
 */
fun Bitmap.crop(crop: Rect): Bitmap {
    assert(crop.left < crop.right && crop.top < crop.bottom) { "Cannot use negative crop" }
    assert(crop.left >= 0 && crop.top >= 0 && crop.bottom <= this.height && crop.right <= this.width) { "Crop is larger than source image" }
    return Bitmap.createBitmap(
        this,
        crop.left,
        crop.top,
        crop.width(),
        crop.height()
    )
}

fun Bitmap.rotate(rotationDegrees: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(rotationDegrees)
    return Bitmap.createBitmap(this, 0, 0, this.width, this.height, matrix, true)
}

fun Bitmap.scale(size: Size, filter: Boolean = false): Bitmap =
    Bitmap.createScaledBitmap(this, size.width, size.height, filter)
