package com.getbouncer.cardscan.base.domain

data class CardExpiry(val day: Int?, val month: Int, val year: Int)
data class CardNumber(val number: String)
data class CardOcrResult(val number: CardNumber?, val expiry: CardExpiry?)
