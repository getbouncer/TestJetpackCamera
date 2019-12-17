package com.getbouncer.cardscan.base.domain

import android.graphics.RectF
import android.util.SizeF

data class DetectionBox(
    val rectF: RectF,
    val row: Int,
    val col: Int,
    val confidence: Float
) : Comparable<DetectionBox> {
    companion object {
        val BOX_SIZE = SizeF(80F, 36F)
    }

    override fun compareTo(other: DetectionBox): Int = confidence.compareTo(other.confidence)
}
