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

        return if (Random.nextInt(200) == 1) {
            CardOcrResult(CardNumber("1234 5678 9012 3456"), CardExpiry(1, 2, 23))
        } else {
            CardOcrResult(null, null)
        }
    }
}
