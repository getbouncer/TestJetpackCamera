package com.getbouncer.cardscan.base.camera

import android.graphics.Rect
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.getbouncer.cardscan.base.domain.ScanImage
import com.getbouncer.cardscan.base.image.crop
import com.getbouncer.cardscan.base.image.toBitmap
import com.getbouncer.cardscan.base.ml.MemoryBoundAnalyzerLoop
import com.getbouncer.cardscan.base.util.Timer
import com.getbouncer.cardscan.base.util.calculateCardCrop
import com.getbouncer.cardscan.base.util.calculateObjectDetectionCrop
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.time.ExperimentalTime

/**
 * This class is an adaption of the [MemoryBoundAnalyzerLoop] to work with the Android CameraX
 * APIs. Since the loops require async enqueueing of images for analysis and execution of those
 * images, this class adapts the images from the CameraX APIs to be enqueued in the loop
 */
abstract class ImageAnalysisAdapter<ImageFormat, Output>(
    private val loop: MemoryBoundAnalyzerLoop<ImageFormat, Output>
) : ImageAnalysis.Analyzer {

    @ExperimentalCoroutinesApi
    override fun analyze(image: ImageProxy, rotationDegrees: Int) {
        loop.enqueueFrame(convertImageFormat(image, rotationDegrees))
    }

    abstract fun convertImageFormat(image: ImageProxy, rotationDegrees: Int): ImageFormat
}

@ExperimentalTime
class CardImageAnalysisAdapter<Output>(
    private val previewSize: Size,
    private val previewCard: Rect,
    loop: MemoryBoundAnalyzerLoop<ScanImage, Output>
)
    : ImageAnalysisAdapter<ScanImage, Output>(loop) {

    private val loggingTimer = Timer.newInstance("Bouncer", "Image cropping")

    override fun convertImageFormat(image: ImageProxy, rotationDegrees: Int): ScanImage =
        loggingTimer.measure {
            val fullImage = image.toBitmap()
            val fullImageSize = Size(image.width, image.height)
            val objCrop = calculateObjectDetectionCrop(fullImageSize, previewSize, previewCard)
            val cardCrop = calculateCardCrop(fullImageSize, previewSize, previewCard)

            ScanImage(
                fullImage = fullImage,
                objImage = fullImage.crop(objCrop),
                ocrImage = fullImage.crop(cardCrop),
                rotationDegrees = rotationDegrees
            )
        }
}
