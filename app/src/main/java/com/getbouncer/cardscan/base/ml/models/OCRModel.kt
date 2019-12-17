package com.getbouncer.cardscan.base.ml.models

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Size
import androidx.camera.core.ImageProxy
import com.getbouncer.cardscan.base.image.crop
import com.getbouncer.cardscan.base.image.toBitmap
import com.getbouncer.cardscan.base.image.toRGBAByteBuffer
import com.getbouncer.cardscan.base.ml.MLImageAnalyzerModel
import com.getbouncer.cardscan.base.ml.MLResourceModelFactory
import com.getbouncer.cardscan.base.ml.ResultHandler

/**
 * This is actually an aggregating model. While not a model itself, it makes use of other models
 * to perform analysis.
 *
 * This requires some dependencies to work
 * - factory:       A factory to create the sub models. Alternatively, sub models could be created
 *                  externally and passed in as constructor parameters
 * - context:       An android context used by some sub models. This dependency can be removed if
 *                  sub models are constructed externally
 * - cardRect:      The location of the card within the preview image. This is used for cropping
 *                  the preview image.
 * - resultHandler: A handler for the result. Usually this is the main activity.
 */
class OCRModel(
    private val factory: MLResourceModelFactory,
    private val context: Context,
    private val cardRect: RectF,
    resultHandler: ResultHandler<String>
) : MLImageAnalyzerModel<Bitmap?, String>(resultHandler) {

    override val supportsMultiThreading: Boolean = true

    override fun analyze(data: Bitmap?): String {
        if (data != null) {
            val croppedImage = data.crop(cardRect)
            val findFourPrediction = FindFourModel(factory, context, Size(data.width, data.height))
                .analyze(croppedImage.toRGBAByteBuffer())
        }

        // TODO: implement OCR using the new model
        return ""
    }

    override fun convertImageData(image: ImageProxy?, rotationDegrees: Int): Bitmap? {
        /* TODO: decide if bitmap is the best way to transport the image, or if we should use a
             byte buffer instead. The goal here is to minimize the number of transformations
             we make to the image (crop, rotate, resize) to speed up processing time. */
        return image?.toBitmap()
    }
}
