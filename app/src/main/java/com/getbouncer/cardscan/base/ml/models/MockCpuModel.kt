package com.getbouncer.cardscan.base.ml.models

import androidx.camera.core.ImageProxy
import com.getbouncer.cardscan.base.domain.CardExpiry
import com.getbouncer.cardscan.base.domain.CardImage
import com.getbouncer.cardscan.base.domain.CardNumber
import com.getbouncer.cardscan.base.domain.CardOcrResult
import com.getbouncer.cardscan.base.image.toCardImage
import com.getbouncer.cardscan.base.ml.MLImageAnalyzerModel
import com.getbouncer.cardscan.base.ml.ResultHandler
import kotlin.random.Random


class MockCpuModel(resultHandler: ResultHandler<CardImage, CardOcrResult>)
    : MLImageAnalyzerModel<CardImage, CardOcrResult>(resultHandler) {

    override val supportsMultiThreading: Boolean = false

    override fun analyze(data: CardImage): CardOcrResult {
        // Simulate analyzing a credit card

        return if (Random.nextInt(100) == 1) {
            CardOcrResult(CardNumber("1234 5678 9012 3456"), CardExpiry(1, 2, 23))
        } else {
            CardOcrResult(null, null)
        }
    }

    override fun convertImageData(image: ImageProxy, rotationDegrees: Int): CardImage =
        image.toCardImage(rotationDegrees)
}
