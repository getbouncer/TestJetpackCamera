package com.getbouncer.cardscan.base.image

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ConfigurationInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.util.Size
import androidx.camera.core.ImageProxy
import com.getbouncer.cardscan.base.domain.CardImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import kotlin.math.roundToInt

private const val DIM_PIXEL_SIZE = 3
private const val NUM_BYTES_PER_CHANNEL = 4 // Float.size / Byte.size

fun ImageProxy.toBitmap(): Bitmap {
    val nv21 = this.toNV21ByteArray()

    val argb = IntArray(this.width * this.height).also {
        YUVDecoder.yuvToRGBA(nv21, this.width, this.height, it)
    }

    val bitmap = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(IntBuffer.wrap(argb))
    return bitmap
}

fun Bitmap.toRGBAByteBuffer(mean: Float = 0F, std: Float = 255F): ByteBuffer {
    val rgba = IntArray(width * height).also {
        getPixels(it, 0, width, 0, 0, width, height)
    }

    val rgbaFloat = ByteBuffer.allocateDirect(this.width * this.height * DIM_PIXEL_SIZE * NUM_BYTES_PER_CHANNEL)
    rgbaFloat.order(ByteOrder.nativeOrder())
    rgba.forEach {
        rgbaFloat.putFloat(((it shr 16 and 0xFF) - mean) / std)
        rgbaFloat.putFloat(((it shr 8 and 0xFF) - mean) / std)
        rgbaFloat.putFloat(((it and 0xFF) - mean) / std)
    }

    return rgbaFloat
}

fun ImageProxy.toNV21ByteArray(): ByteArray {
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

    return nv21
}

fun ImageProxy.toRGBAByteBuffer(mean: Float = 0F, std: Float = 255F): ByteBuffer {
    val nv21 = this.toNV21ByteArray()

    val rgba = IntArray(width * height).also {
        YUVDecoder.yuvToRGBA(nv21, width, height, it)
    }

    val rgbaFloat = ByteBuffer.allocateDirect(this.width * this.height * DIM_PIXEL_SIZE * NUM_BYTES_PER_CHANNEL)
    rgbaFloat.order(ByteOrder.nativeOrder())
    rgba.forEach {
        rgbaFloat.putFloat(((it shr 16 and 0xFF) - mean) / std)
        rgbaFloat.putFloat(((it shr 8 and 0xFF) - mean) / std)
        rgbaFloat.putFloat(((it and 0xFF) - mean) / std)
    }

    return rgbaFloat
}

fun ImageProxy.toCardImage(rotationDegrees: Int): CardImage =
    CardImage(
        this.toRGBAByteBuffer(),
        rotationDegrees,
        Size(this.width, this.height)
    )

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

fun ByteBuffer.rbgaToBitmap(size: Size, mean: Float = 0F, std: Float = 255F): Bitmap {
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val rgba = IntBuffer.allocate(size.width * size.height)
    while (this.hasRemaining()) {
        this.float.also {
            rgba.put(
                (((it * std) + mean).toInt() shl 16)
                + (((it * std) + mean).toInt() shl 8)
                + (((it * std) + mean).toInt())
            )
        }
    }
    rgba.reset()
    bitmap.copyPixelsFromBuffer(rgba)
    return bitmap
}

fun Bitmap.crop(crop: RectF): Bitmap =
    Bitmap.createBitmap(
        this,
        crop.left.roundToInt(),
        crop.top.roundToInt(),
        crop.width().roundToInt(),
        crop.height().roundToInt()
    )

fun ByteBuffer.crop(originalSize: Size, crop: Rect): ByteBuffer {
    val cropped = ByteBuffer.allocateDirect(crop.width() * crop.height() * DIM_PIXEL_SIZE * NUM_BYTES_PER_CHANNEL)
    for (y in crop.top..crop.bottom) {
        this.position((DIM_PIXEL_SIZE * NUM_BYTES_PER_CHANNEL * y * originalSize.width) + (crop.left * DIM_PIXEL_SIZE * NUM_BYTES_PER_CHANNEL))
        for (x in 0..crop.width() * DIM_PIXEL_SIZE * NUM_BYTES_PER_CHANNEL) {
            cropped.put(this.get())
        }
    }
    return cropped
}
