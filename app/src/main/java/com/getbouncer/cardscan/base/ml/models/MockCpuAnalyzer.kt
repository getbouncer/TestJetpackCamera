package com.getbouncer.cardscan.base.ml.models

import com.getbouncer.cardscan.base.domain.CardExpiry
import com.getbouncer.cardscan.base.domain.CardImage
import com.getbouncer.cardscan.base.domain.CardNumber
import com.getbouncer.cardscan.base.domain.CardOcrResult
import com.getbouncer.cardscan.base.ml.Analyzer
import kotlin.random.Random


class MockCpuAnalyzer : Analyzer<CardImage, CardOcrResult> {

    override val isThreadSafe: Boolean = false

    override fun analyze(data: CardImage): CardOcrResult {
        // Simulate analyzing a credit card

        Thread.sleep(300)
        return if (Random.nextInt(4) == 1) {
            CardOcrResult(CardNumber("4847 1860 9511 8770"), CardExpiry(1, 2, 23))
        } else {
            CardOcrResult(null, null)
        }
    }
}
