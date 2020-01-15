package com.getbouncer.cardscan.base.ml.models

import com.getbouncer.cardscan.base.domain.ScanImage
import com.getbouncer.cardscan.base.domain.CardOcrResult
import com.getbouncer.cardscan.base.image.toRGBByteBuffer
import com.getbouncer.cardscan.base.ml.Analyzer
import com.getbouncer.cardscan.base.ml.MLResourceModelFactory
import com.getbouncer.cardscan.base.util.Timer
import kotlin.time.ExperimentalTime

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
@ExperimentalTime
class SSDOcrModel(
    private val factory: MLResourceModelFactory
) : Analyzer<ScanImage, CardOcrResult> {

    companion object {
        private const val IMAGE_MEAN = 127.5f
        private const val IMAGE_STD = 128.5f
    }

    override val isThreadSafe: Boolean = true

    private val loggingTimer = Timer.newInstance("Bouncer", "OCR Analysis")

    override fun analyze(data: ScanImage): CardOcrResult = loggingTimer.measure {
        val imageBytes = data.ocrImage.toRGBByteBuffer(mean = IMAGE_MEAN, std = IMAGE_STD)

        // TODO: implement OCR using the new model
        CardOcrResult(null, null)
    }
}
