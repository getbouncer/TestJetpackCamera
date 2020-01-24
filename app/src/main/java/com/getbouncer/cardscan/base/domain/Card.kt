package com.getbouncer.cardscan.base.domain

import com.getbouncer.cardscan.base.ml.models.ssd.OcrDetectionBox

data class CardOcrResult(val number: CardNumber?, val expiry: CardExpiry?)
data class CardNumber(val number: String, val boxes: Collection<OcrDetectionBox>)
data class CardExpiry(val day: String?, val month: String, val year: String, val boxes: Collection<OcrDetectionBox>)
