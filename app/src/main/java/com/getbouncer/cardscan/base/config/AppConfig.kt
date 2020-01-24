package com.getbouncer.cardscan.base.config

import com.getbouncer.cardscan.base.domain.CardExpiry
import com.getbouncer.cardscan.base.domain.CardNumber

/**
 * Set to true to enable debugging logging that measures various execution times.
 */
const val MEASURE_TIME: Boolean = true

/**
 * Set to true to enable the debug view that shows what the model is processing.
 */
const val IS_DEBUG: Boolean = true

/**
 * Set to a non-null value to require a certain card be scanned.
 */
val TEST_CARD_NUMBER: CardNumber? = CardNumber("4847186095118770")
val TEST_CARD_EXPIRY: CardExpiry? = null