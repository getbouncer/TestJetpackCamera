package com.getbouncer.cardscan.base.config

import com.getbouncer.cardscan.base.domain.CardNumber
import com.getbouncer.cardscan.base.domain.CardOcrResult

/**
 * Set to true to enable debugging logging that measures various execution times.
 */
const val MEASURE_TIME: Boolean = true

val TEST_CARD: CardOcrResult? = CardOcrResult(CardNumber("4847186095118770"), null)