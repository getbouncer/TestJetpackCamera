package com.getbouncer.cardscan.base.domain

data class CardOcrResult(val number: CardNumber?, val expiry: CardExpiry?)
data class CardNumber(val number: String)
data class CardExpiry(val day: String?, val month: String, val year: String)
