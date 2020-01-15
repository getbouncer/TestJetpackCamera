package com.getbouncer.cardscan.base.ml.models

import com.getbouncer.cardscan.base.domain.CardExpiry
import com.getbouncer.cardscan.base.domain.ScanImage
import com.getbouncer.cardscan.base.domain.CardNumber
import com.getbouncer.cardscan.base.domain.CardOcrResult
import com.getbouncer.cardscan.base.ml.Analyzer
import kotlin.random.Random


class MockCpuAnalyzer : Analyzer<ScanImage, CardOcrResult> {

    override val isThreadSafe: Boolean = false

    override fun analyze(data: ScanImage): CardOcrResult {
        // Simulate analyzing a credit card

        return if (Random.nextInt(100) == 1) {
            CardOcrResult(CardNumber("4847 1860 9511 8770"), CardExpiry(1, 2, 23))
        } else {
            CardOcrResult(null, null)
        }
    }
}
