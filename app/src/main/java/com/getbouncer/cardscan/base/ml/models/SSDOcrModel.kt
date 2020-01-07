package com.getbouncer.cardscan.base.ml.models

import android.content.Context
import android.graphics.Rect
import com.getbouncer.cardscan.base.domain.CardImage
import com.getbouncer.cardscan.base.domain.CardOcrResult
import com.getbouncer.cardscan.base.image.crop
import com.getbouncer.cardscan.base.ml.Analyzer
import com.getbouncer.cardscan.base.ml.MLResourceModelFactory

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
class SSDOcrModel(
    private val factory: MLResourceModelFactory,
    private val context: Context,
    private val cardRect: Rect
) : Analyzer<CardImage, CardOcrResult> {

    override val isThreadSafe: Boolean = true

    override fun analyze(data: CardImage): CardOcrResult {
        val croppedImage = data.image.crop(data.size, cardRect)

        // TODO: implement OCR using the new model
        return CardOcrResult(null, null)
    }
}
