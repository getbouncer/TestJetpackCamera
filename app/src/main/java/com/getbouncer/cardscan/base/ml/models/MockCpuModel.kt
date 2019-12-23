package com.getbouncer.cardscan.base.ml.models

import androidx.camera.core.ImageProxy
import com.getbouncer.cardscan.base.domain.*
import com.getbouncer.cardscan.base.image.toRGBAByteBuffer
import com.getbouncer.cardscan.base.ml.MLImageAnalyzerModel
import com.getbouncer.cardscan.base.ml.ResultHandler
import java.util.*


class MockCpuModel(resultHandler: ResultHandler<CardImageData, CardOcrResult>)
    : MLImageAnalyzerModel<CardImageData, CardOcrResult>(resultHandler) {

    override val supportsMultiThreading: Boolean = false

    override fun analyze(data: CardImageData): CardOcrResult {
        val imageBytes = data.image?.toRGBAByteBuffer()

        // Simulate analyzing a credit card

        return if (Random().nextInt(1000) == 1) {
            CardOcrResult(CardNumber("1234 5678 9012 3456"), CardExpiry(1, 2, 23))
        } else {
            CardOcrResult(null, null)
        }
    }

    override fun convertImageData(image: ImageProxy?, rotationDegrees: Int): CardImageData =
        CardImageData(image, rotationDegrees)
}
