package com.getbouncer.cardscan.base.camera

import android.graphics.Rect
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.getbouncer.cardscan.base.domain.ScanImage
import com.getbouncer.cardscan.base.image.crop
import com.getbouncer.cardscan.base.image.rotate
import com.getbouncer.cardscan.base.image.toBitmap
import com.getbouncer.cardscan.base.MemoryBoundAnalyzerLoop
import com.getbouncer.cardscan.base.util.Timer
import com.getbouncer.cardscan.base.util.calculateCardCrop
import com.getbouncer.cardscan.base.util.calculateObjectDetectionCrop
import kotlin.time.ExperimentalTime

/**
 * This class is an adaption of the [MemoryBoundAnalyzerLoop] to work with the Android CameraX
 * APIs. Since the loops require async enqueueing of images for analysis and execution of those
 * images, this class adapts the images from the CameraX APIs to be enqueued in the loop
 */
@ExperimentalTime
abstract class ImageAnalysisAdapter<ImageFormat, Output>(
    private val loop: MemoryBoundAnalyzerLoop<ImageFormat, Output>
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy, rotationDegrees: Int) {
        if (!loop.isFinished()) {
            loop.enqueueFrame(convertImageFormat(image, rotationDegrees))
        }
    }

    abstract fun convertImageFormat(image: ImageProxy, rotationDegrees: Int): ImageFormat
}

@ExperimentalTime
class CardImageAnalysisAdapter<Output>(
    private val previewSize: Size,
    private val cardFinder: Rect,
    loop: MemoryBoundAnalyzerLoop<ScanImage, Output>
) : ImageAnalysisAdapter<ScanImage, Output>(loop) {

    private val loggingTimer = Timer.newInstance("Bouncer", "Image cropping")

    override fun convertImageFormat(image: ImageProxy, rotationDegrees: Int): ScanImage {
        return loggingTimer.measure {
            val fullImage = image.toBitmap().rotate(rotationDegrees.toFloat())
            val fullImageSize = Size(fullImage.width, fullImage.height)
            val objCrop = calculateObjectDetectionCrop(fullImageSize, previewSize, cardFinder)
            val cardCrop = calculateCardCrop(fullImageSize, previewSize, cardFinder)

            ScanImage(
                fullImage = fullImage,
                objImage = fullImage.crop(objCrop),
                ocrImage = fullImage.crop(cardCrop)
            )
        }
    }
}
