package com.getbouncer.cardscan.base.ml.models

import android.util.Log
import android.util.Size
import com.getbouncer.cardscan.base.domain.CardExpiry
import com.getbouncer.cardscan.base.domain.ScanImage
import com.getbouncer.cardscan.base.domain.CardNumber
import com.getbouncer.cardscan.base.domain.CardOcrResult
import com.getbouncer.cardscan.base.image.scale
import com.getbouncer.cardscan.base.image.toRGBByteBuffer
import com.getbouncer.cardscan.base.ml.Analyzer
import com.getbouncer.cardscan.base.util.Timer
import kotlin.random.Random
import kotlin.time.ExperimentalTime


@ExperimentalTime
class MockCpuAnalyzer : Analyzer<ScanImage, CardOcrResult> {

    override val isThreadSafe: Boolean = false

    private val trainedImageSize = Size(600, 375)
    private val scaleTimer = Timer.newInstance("AGW", "SCALE")
    private val transformTimer = Timer.newInstance("AGW", "TRANSFORM")

    override fun analyze(data: ScanImage): CardOcrResult {
        // Simulate analyzing a credit card

        val scaledImage = scaleTimer.measure {
            data.ocrImage.scale(trainedImageSize)
        }

        val byteImage = transformTimer.measure{
            scaledImage.toRGBByteBuffer()
        }

        Log.v("AGW", "byte image is size ${byteImage.limit()}")
        return if (Random.nextInt(500) == 1) {
            CardOcrResult(CardNumber("4847 1860 9511 8770"), CardExpiry(1, 2, 23))
        } else {
            CardOcrResult(null, null)
        }
    }
}
