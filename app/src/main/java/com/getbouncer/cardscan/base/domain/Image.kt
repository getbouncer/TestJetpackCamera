package com.getbouncer.cardscan.base.domain

import android.graphics.Bitmap

/**
 * An image scanned and ready for image processing
 */
data class ScanImage(
    val fullImage: Bitmap,
    val objImage: Bitmap,
    val ocrImage: Bitmap
) {
    val sizeInBytes = fullImage.byteCount + objImage.byteCount + ocrImage.byteCount
}