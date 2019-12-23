package com.getbouncer.cardscan.base.domain

import androidx.camera.core.ImageProxy

data class CardExpiry(val day: Int?, val month: Int, val year: Int)
data class CardNumber(val number: String)
data class CardOcrResult(val number: CardNumber?, val expiry: CardExpiry?)
data class CardResult<ImageFormat>(val ocrResult: CardOcrResult, val image: ImageFormat?, val rotationDegrees: Int)
data class CardImageData(val image: ImageProxy?, val rotationDegrees: Int)
