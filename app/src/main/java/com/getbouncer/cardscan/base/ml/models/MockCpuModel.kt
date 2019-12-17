package com.getbouncer.cardscan.base.ml.models

import androidx.camera.core.ImageProxy
import com.getbouncer.cardscan.base.domain.*
import com.getbouncer.cardscan.base.image.toRGBAByteBuffer
import com.getbouncer.cardscan.base.ml.MLImageAnalyzerModel
import com.getbouncer.cardscan.base.ml.ResultHandler
import java.nio.ByteBuffer
import java.util.*


class MockCpuModel(resultHandler: ResultHandler<CardResult<ByteBuffer>>)
    : MLImageAnalyzerModel<CardImageData, CardResult<ByteBuffer>>(resultHandler) {

    override val supportsMultiThreading: Boolean = false

    override fun analyze(data: CardImageData): CardResult<ByteBuffer> {
        val imageBytes = data.image?.toRGBAByteBuffer()

        // Simulate analyzing a credit card

        val ocrResult = if (Random().nextInt(1000) == 1) {
            CardOcrResult(CardNumber("1234 5678 9012 3456"), CardExpiration(1, 2, 23))
        } else {
            CardOcrResult(null, null)
        }

        return CardResult(ocrResult, imageBytes, data.rotationDegrees)
    }

    override fun convertImageData(image: ImageProxy?, rotationDegrees: Int): CardImageData =
        CardImageData(image, rotationDegrees)
}
