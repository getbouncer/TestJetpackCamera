package com.getbouncer.cardscan.base.camera

import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.getbouncer.cardscan.base.domain.CardImage
import com.getbouncer.cardscan.base.domain.FixedMemorySize
import com.getbouncer.cardscan.base.image.toRGBAByteBuffer
import com.getbouncer.cardscan.base.ml.MemoryBoundAnalyzerLoop

/**
 * This class is an adaption of the [MemoryBoundAnalyzerLoop] to work with the Android CameraX
 * APIs. Since the loops require async enqueueing of images for analysis and execution of those
 * images, this class adapts the images from the CameraX APIs to be enqueued in the loop
 */
abstract class ImageAnalysisAdapter<ImageFormat : FixedMemorySize, Output>(
    private val loop: MemoryBoundAnalyzerLoop<ImageFormat>
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy, rotationDegrees: Int) {
        loop.enqueueFrame(convertImageFormat(image, rotationDegrees))
    }

    abstract fun convertImageFormat(image: ImageProxy, rotationDegrees: Int): ImageFormat
}

class CardImageAnalysisAdapter<Output>(loop: MemoryBoundAnalyzerLoop<CardImage>)
    : ImageAnalysisAdapter<CardImage, Output>(loop) {

    companion object {
        private const val IMAGE_MEAN = 127.5f
        private const val IMAGE_STD = 128.5f
    }

    override fun convertImageFormat(image: ImageProxy, rotationDegrees: Int): CardImage =
        CardImage(
            image = image.toRGBAByteBuffer(mean = IMAGE_MEAN, std = IMAGE_STD),
            rotationDegrees = rotationDegrees,
            size = Size(image.width, image.height)
        )
}
