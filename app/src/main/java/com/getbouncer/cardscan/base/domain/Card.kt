package com.getbouncer.cardscan.base.domain

import android.util.Size
import java.nio.ByteBuffer

data class CardExpiry(val day: Int?, val month: Int, val year: Int)
data class CardNumber(val number: String)
data class CardOcrResult(val number: CardNumber?, val expiry: CardExpiry?)
data class CardImage(val image: ByteBuffer, val rotationDegrees: Int, val size: Size)
