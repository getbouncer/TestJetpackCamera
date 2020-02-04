package com.getbouncer.cardscan.base.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Size
import android.util.TypedValue
import android.view.View
import androidx.core.graphics.toRectF
import com.getbouncer.cardscan.base.R
import com.getbouncer.cardscan.base.ui.util.CARD_PREVIEW_FRAME_BUFFER
import com.getbouncer.cardscan.base.ui.util.STANDARD_CARD_RATIO
import com.getbouncer.cardscan.base.ui.util.calculateCardFinderRect

/**
 * Render a view finder overlay based on the specified theme. This was originally based on the medium post here:
 * https://medium.com/@rgomez/android-how-to-draw-an-overlay-with-a-transparent-hole-471af6cf3953
 */
class ViewFinderOverlay(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    enum class State(val value: Int) {
        SCANNING(0),
        FOUND(1),
        WRONG(2);

        companion object {
            fun valueOf(int: Int) =
                when (int) {
                    1 -> FOUND
                    2 -> WRONG
                    else -> SCANNING
                }
        }
    }

    private val theme = context.theme
    private val attributes = theme.obtainStyledAttributes(attrs, R.styleable.ViewFinderOverlay, 0, 0)
    private val verticalWeight = attributes.run { getFloat(R.styleable.ViewFinderOverlay_viewFinderVerticalWeight, 0.5F) }
    private val horizontalWeight = attributes.run { getFloat(R.styleable.ViewFinderOverlay_viewFinderHorizontalWeight, 0.5F) }
    private val cardRatio = attributes.run { getFraction(R.styleable.ViewFinderOverlay_viewFinderCardAspectRatio, 1, 1, STANDARD_CARD_RATIO.toFloat()) }
    private val bufferPercent = attributes.run { getFloat(R.styleable.ViewFinderOverlay_viewFinderBufferPercent,
        CARD_PREVIEW_FRAME_BUFFER
    ) }

    private var showBackground = attributes.run { getBoolean(R.styleable.ViewFinderOverlay_showBackground, true) }
    private var showCorners = attributes.run { getBoolean(R.styleable.ViewFinderOverlay_showCorners, false) }
    private var showOutline = attributes.run { getBoolean(R.styleable.ViewFinderOverlay_showOutline, false) }
    private var state = attributes.run { State.valueOf(getInt(R.styleable.ViewFinderOverlay_state, State.SCANNING.value)) }

    private val backgroundColor = TypedValue()
    private val cornerColor = TypedValue()
    private val cornerWidthValue = TypedValue()
    private val cornerLengthValue = TypedValue()
    private val cornerRadiusValue = TypedValue()
    private val outlineColor = TypedValue()
    private val outlineWidthValue = TypedValue()

    private val cornerRadius by lazy { cornerRadiusValue.getDimension(context.resources.displayMetrics) }
    private val cornerLength by lazy { cornerLengthValue.getDimension(context.resources.displayMetrics) }
    private val cornerWidth by lazy { cornerWidthValue.getDimension(context.resources.displayMetrics) }
    private val outlineWidth by lazy { outlineWidthValue.getDimension(context.resources.displayMetrics) }

    private val transferMode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private val paintBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintWindow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = transferMode
        style = Paint.Style.FILL
    }
    private val paintOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val paintCorner = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (canvas == null) {
            return
        }

        resolveColorValues()
        val cardFinderRect = getCardFinderRectangle().toRectF()

        if (showBackground) {
            drawBackground(canvas, cardFinderRect)
        }

        if (showOutline) {
            drawOutline(canvas, cardFinderRect)
        }

        if (showCorners) {
            drawCorners(canvas, cardFinderRect)
        }
    }

    /**
     * Draw the background with a credit-card shaped hole.
     */
    private fun drawBackground(canvas: Canvas, cardFinderRect: RectF) {
        paintBackground.color = backgroundColor.data
        canvas.drawPaint(paintBackground)
        canvas.drawRoundRect(cardFinderRect, cornerRadius, cornerRadius, paintWindow)
    }

    /**
     * Draw an outline around the card view finder window.
     */
    private fun drawOutline(canvas: Canvas, cardFinderRect: RectF) {
        paintOutline.color = outlineColor.data
        paintOutline.strokeWidth = outlineWidth
        canvas.drawRoundRect(cardFinderRect, cornerRadius, cornerRadius, paintOutline)
    }

    /**
     * Draw bold corners around the card view finder window.
     */
    private fun drawCorners(canvas: Canvas, cardFinderRect: RectF) {
        paintCorner.color = cornerColor.data
        paintCorner.strokeWidth = cornerWidth

        val topLeft = RectF(
            cardFinderRect.left,
            cardFinderRect.top,
            cardFinderRect.left + 2 * cornerRadius,
            cardFinderRect.top + 2 * cornerRadius
        )

        val topRight = RectF(
            cardFinderRect.right - 2 * cornerRadius,
            cardFinderRect.top,
            cardFinderRect.right,
            cardFinderRect.top + 2 * cornerRadius
        )

        val bottomLeft = RectF(
            cardFinderRect.left,
            cardFinderRect.bottom - 2 * cornerRadius,
            cardFinderRect.left + 2 * cornerRadius,
            cardFinderRect.bottom
        )

        val bottomRight = RectF(
            cardFinderRect.right - 2 * cornerRadius,
            cardFinderRect.bottom - 2 * cornerRadius,
            cardFinderRect.right,
            cardFinderRect.bottom
        )

        canvas.drawArc(topLeft, 180F, 90F, false, paintCorner)
        canvas.drawLine(topLeft.left, topLeft.bottom - cornerRadius, topLeft.left, topLeft.bottom - cornerRadius + cornerLength, paintCorner)
        canvas.drawLine(topLeft.right - cornerRadius, topLeft.top, topLeft.right - cornerRadius + cornerLength, topLeft.top, paintCorner)

        canvas.drawArc(topRight, 270F, 90F, false, paintCorner)
        canvas.drawLine(topRight.right, topRight.bottom - cornerRadius, topRight.right, topRight.bottom - cornerRadius + cornerLength, paintCorner)
        canvas.drawLine(topRight.right - cornerRadius, topRight.top, topRight.right - cornerRadius - cornerLength, topRight.top, paintCorner)

        canvas.drawArc(bottomLeft, 90F, 90F, false, paintCorner)
        canvas.drawLine(bottomLeft.left, bottomLeft.bottom - cornerRadius, bottomLeft.left, bottomLeft.bottom - cornerRadius - cornerLength, paintCorner)
        canvas.drawLine(bottomLeft.right - cornerRadius, bottomLeft.bottom, bottomLeft.right - cornerRadius + cornerLength, bottomLeft.bottom, paintCorner)

        canvas.drawArc(bottomRight, 0F, 90F, false, paintCorner)
        canvas.drawLine(bottomRight.right, bottomRight.bottom - cornerRadius, bottomRight.right, bottomRight.bottom - cornerRadius - cornerLength, paintCorner)
        canvas.drawLine(bottomRight.right - cornerRadius, bottomRight.bottom, bottomRight.right - cornerRadius - cornerLength, bottomRight.bottom, paintCorner)
    }

    /**
     * Determine what color the background, outline, and corners should be based on the current [state].
     */
    private fun resolveColorValues() {
        when (state) {
            State.SCANNING -> {
                theme.resolveAttribute(R.attr.cardScanScanningBackgroundColor, backgroundColor, true)
                theme.resolveAttribute(R.attr.cardScanScanningCornerColor, cornerColor, true)
                theme.resolveAttribute(R.attr.cardScanScanningOutlineColor, outlineColor, true)
            }

            State.FOUND -> {
                theme.resolveAttribute(R.attr.cardScanFoundBackgroundColor, backgroundColor, true)
                theme.resolveAttribute(R.attr.cardScanFoundCornerColor, cornerColor, true)
                theme.resolveAttribute(R.attr.cardScanFoundOutlineColor, outlineColor, true)
            }

            State.WRONG -> {
                theme.resolveAttribute(R.attr.cardScanWrongBackgroundColor, backgroundColor, true)
                theme.resolveAttribute(R.attr.cardScanWrongCornerColor, cornerColor, true)
                theme.resolveAttribute(R.attr.cardScanWrongOutlineColor, outlineColor, true)
            }
        }

        theme.resolveAttribute(R.attr.cardScanCornerLength, cornerLengthValue, true)
        theme.resolveAttribute(R.attr.cardScanCornerRadius, cornerRadiusValue, true)
        theme.resolveAttribute(R.attr.cardScanCornerWidth, cornerWidthValue, true)
        theme.resolveAttribute(R.attr.cardScanOutlineWidth, outlineWidthValue, true)
    }

    /**
     * Retrieve the rectangle of the card finder from this view.
     */
    fun getCardFinderRectangle() = calculateCardFinderRect(
        previewImage = Size(this.width, this.height),
        verticalWeight = verticalWeight,
        horizontalWeight = horizontalWeight,
        cardRatio = cardRatio,
        bufferPercent = bufferPercent
    )

    fun getState() = state
    fun setState(state: State) {
        this.state = state
        invalidate()
        requestLayout()
    }

    fun isShowBackground() = showBackground
    fun setShowBackground(showBackground: Boolean) {
        this.showBackground = showBackground
        invalidate()
        requestLayout()
    }

    fun isShowCorners() = showCorners
    fun setShowCorners(showCorners: Boolean) {
        this.showCorners = showCorners
        invalidate()
        requestLayout()
    }

    fun isShowOutline() = showOutline
    fun setShowOutline(showOutline: Boolean) {
        this.showOutline = showOutline
        invalidate()
        requestLayout()
    }
}