package com.getbouncer.cardscan.base.ml.ssd

import android.graphics.RectF

class DetectionBox(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    val confidence: Float,
    val label: Int
) : Comparable<DetectionBox>, RectF(left, top, right, bottom) {

    override fun compareTo(other: DetectionBox): Int {
        return left.compareTo(other.left)
    }
}