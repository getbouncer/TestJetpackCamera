package com.getbouncer.cardscan.base.ml.models.ssd

import android.graphics.RectF
import android.util.Size

class OcrDetectionBox(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    val imageSize: Size,
    val confidence: Float,
    val label: Int
) : Comparable<OcrDetectionBox>, RectF(left, top, right, bottom) {

    override fun compareTo(other: OcrDetectionBox): Int {
        return left.compareTo(other.left)
    }
}