package com.getbouncer.cardscan.base.domain

import com.getbouncer.cardscan.base.ml.models.ssd.DetectionBox

data class CardOcrResult(val number: CardNumber?, val expiry: CardExpiry?)
data class CardNumber(val number: String, val boxes: Collection<DetectionBox>)
data class CardExpiry(val day: String?, val month: String, val year: String, val boxes: Collection<DetectionBox>)
