package com.getbouncer.cardscan.base.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Size
import android.view.View
import com.getbouncer.cardscan.base.R
import com.getbouncer.cardscan.base.ml.models.ssd.OcrDetectionBox

fun RectF.scaled(scaledSize: Size): RectF {
    return RectF(
        this.left * scaledSize.width,
        this.top * scaledSize.height,
        this.right * scaledSize.width,
        this.bottom * scaledSize.height
    )
}

class DebugOverlay(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private var boxes: Collection<OcrDetectionBox>? = null

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (canvas != null) drawBoxes(canvas)
    }

    private fun drawBoxes(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = context.resources.getColor(R.color.cardScanWrongBackground)
        canvas.drawPaint(paint)
        paint.style = Paint.Style.STROKE
        boxes?.forEach {
            paint.color = getPaintColor(it.confidence)
            canvas.drawRect(it.scaled(Size(this.width, this.height)), paint)
        }
    }

    private fun getPaintColor(confidence: Float) = context.resources.getColor(when {
        confidence > 0.75 -> R.color.cardScanFoundOutline
        confidence > 0.5 -> R.color.cardScanScanningOutline
        else -> R.color.cardScanWrongOutline
    })

    fun setBoxes(boxes: Collection<OcrDetectionBox>?) {
        this.boxes = boxes
        invalidate()
        requestLayout()
    }
}