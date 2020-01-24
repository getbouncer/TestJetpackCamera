package com.getbouncer.cardscan.base.util

import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.util.Rational
import android.util.Size
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A standard credit card is 85.60mm x 53.98mm.
 */
val STANDARD_CARD_RATIO = Rational(8560, 5398)

/**
 * Calculate a rectangle where the card preview indicator should exist on the preview image.
 *
 * @param previewImage: The size of the preview image on which to place the card
 * @param minimumPaddingPx: The minimum padding in pixels between the edge of the preview image and the edge of the card
 *                          crop.
 * @param verticalWeight: A float from 0 to 1 indicating where vertically the preview should be positioned. 0 is at the
 *                        top, 1 is at the bottom, 0.5 is in the middle.
 * @param horizontalWeight: A float from 0 to 1 indicating where horizontally the preview should be positioned. 0 is at
 *                          the left, 1 is at the right, 0.5 is in the middle.
 * @param cardRatio: The X/Y ratio of the card preview image. This should match the ratio of the ML models that operate
 *                   on the card.
 */
fun calculateCardFinderRect(
    previewImage: Size,
    minimumPaddingPx: Int = 0,
    verticalWeight: Float = 0.5F,
    horizontalWeight: Float = 0.5F,
    cardRatio: Float = STANDARD_CARD_RATIO.toFloat()
): Rect {

    // calculate the actual available size of the image given the padding requirements.
    val paddedSize = Size(
        previewImage.width - 2 * minimumPaddingPx,
        previewImage.height - 2 * minimumPaddingPx
    )

    // calculate the maximum size of the card. Ensure that a square will fit as well for object
    // detection
    val squareSize = maxAspectRatioInSize(paddedSize, Rational(1, 1))
    val cardSize = maxAspectRatioInSize(squareSize, cardRatio)

    // calculate the card position
    val topLeftPoint = Point(
        ((paddedSize.width - squareSize.width) * horizontalWeight).roundToInt()
                + (squareSize.width - cardSize.width) / 2,
        ((paddedSize.height - squareSize.height) * verticalWeight).roundToInt()
                + (squareSize.height - cardSize.height) / 2
    )

    // calculate the bounds of the card preview
    val cardLeft = minimumPaddingPx + topLeftPoint.x
    val cardTop = minimumPaddingPx + topLeftPoint.y
    val cardRight = cardLeft + cardSize.width
    val cardBottom = cardTop + cardSize.height

    return Rect(
        /* left */ max(cardLeft, minimumPaddingPx),
        /* top */ max(cardTop, minimumPaddingPx),
        /* right */ min(cardRight, previewImage.width - minimumPaddingPx),
        /* bottom */ min(cardBottom, previewImage.height - minimumPaddingPx)
    )
}

/**
 * Determine the maximum size of rectangle with a given aspect ratio (X/Y) that can fit inside the specified area.
 */
private fun maxAspectRatioInSize(area: Size, aspectRatio: Float): Size {
    var width = area.width
    var height = (width / (aspectRatio)).roundToInt()

    return if (height <= area.height) {
        Size(area.width, height)
    } else {
        height = area.height
        width = (height * (aspectRatio)).roundToInt()
        Size(min(width, area.width), height)
    }
}

/**
 * Determine the maximum size of rectangle with a given aspect ratio (X/Y) that can fit inside the specified area.
 */
private fun maxAspectRatioInSize(area: Size, aspectRatio: Rational): Size =
    maxAspectRatioInSize(area, aspectRatio.toFloat())

/**
 * Calculate the position of the [previewImage] within the [fullImage]. This makes a few
 * assumptions:
 * 1. the previewImage and the fullImage are centered relative to each other.
 * 2. the fullImage and the previewImage have the same orientation
 * 3. the fullImage and the previewImage share either a horizontal or vertical field of view
 * 4. the non-shared field of view must be smaller on the previewImage than the fullImage
 *
 * Note that the [previewImage] and the [fullImage] are allowed to have completely independent
 * resolutions.
 */
private fun scaleAndPositionPreviewImage(fullImage: Size, previewImage: Size): Rect {
    // Since the preview image may be at a different resolution than the full image, scale the
    // preview image to be circumscribed by the fullImage.
    val scaledPreviewImageSize = maxAspectRatioInSize(
            fullImage,
            Rational(previewImage.width, previewImage.height)
        )
    val scaledPreviewImageLeft = (fullImage.width - scaledPreviewImageSize.width) / 2
    val scaledPreviewImageTop = (fullImage.height - scaledPreviewImageSize.height) / 2
    return Rect(
        /* left */ scaledPreviewImageLeft,
        /* top */ scaledPreviewImageTop,
        /* right */ scaledPreviewImageLeft + scaledPreviewImageSize.width,
        /* bottom */ scaledPreviewImageTop + scaledPreviewImageSize.height
    )
}

/**
 * Calculate the crop from the [fullImage] for the credit card based on the [cardFinder] within the [previewImage].
 *
 * Note: This algorithm makes some assumptions:
 * 1. the previewImage and the fullImage are centered relative to each other.
 * 2. the fullImage circumscribes the previewImage. I.E. they share at least one field of view, and the previewImage's
 *    fields of view are smaller than or the same size as the fullImage's
 * 3. the fullImage and the previewImage have the same orientation
 */
fun calculateCardCrop(fullImage: Size, previewImage: Size, cardFinder: Rect): Rect {
    assert(cardFinder.left >= 0
            && cardFinder.right <= previewImage.width
            && cardFinder.top >= 0
            && cardFinder.bottom <= previewImage.height
    ) { "Card finder is outside preview image bounds" }

    // Scale the previewImage to match the fullImage
    val scaledPreviewImage = scaleAndPositionPreviewImage(fullImage, previewImage)
    val previewScale = scaledPreviewImage.width().toFloat() / previewImage.width

    // Scale the cardFinder to match the scaledPreviewImage
    val scaledCardFinder = Rect(
        (cardFinder.left * previewScale).roundToInt(),
        (cardFinder.top * previewScale).roundToInt(),
        (cardFinder.right * previewScale).roundToInt(),
        (cardFinder.bottom * previewScale).roundToInt()
    )

    // Position the scaledCardFinder on the fullImage
    return Rect(
        scaledCardFinder.left + scaledPreviewImage.left,
        scaledCardFinder.top + scaledPreviewImage.top,
        scaledCardFinder.right + scaledPreviewImage.left,
        scaledCardFinder.bottom + scaledPreviewImage.top
    )
}

/**
 * Given a card finder region of a preview image, calculate the associated object detection square.
 */
private fun calculateObjectDetectionFromCardFinder(previewImage: Size, cardFinder: Rect): Rect =
    if (cardFinder.width() > cardFinder.height()) {
        val objectDetectionSquareTop =
            max(0, cardFinder.top + cardFinder.height() / 2 - cardFinder.width() / 2)
        val objectDetectionSquareBottom =
            min(previewImage.height, objectDetectionSquareTop + cardFinder.width())
        Rect(
            /* left */ cardFinder.left,
            /* top */ objectDetectionSquareTop,
            /* right */ cardFinder.right,
            /* bottom */ objectDetectionSquareBottom
        )
    } else {
        val objectDetectionSquareLeft =
            max(0, cardFinder.left + cardFinder.width() / 2 - cardFinder.height() / 2)
        val objectDetectionSquareRight =
            min(previewImage.width, objectDetectionSquareLeft + cardFinder.height())
        Rect(
            /* left */ objectDetectionSquareLeft,
            /* top */ cardFinder.top,
            /* right */ objectDetectionSquareRight,
            /* bottom */ cardFinder.bottom
        )
    }

/**
 * Calculate what portion of the full image should be cropped for object detection based on the position of card finder
 * within the preview image.
 */
fun calculateObjectDetectionCrop(fullImage: Size, previewImage: Size, cardFinder: Rect): Rect {
    assert(cardFinder.left >= 0
            && cardFinder.right <= previewImage.width
            && cardFinder.top >= 0
            && cardFinder.bottom <= previewImage.height
    ) { "Card finder is outside preview image bounds" }

    // Calculate the object detection square based on the card finder, limited by the preview
    val objectDetectionSquare = calculateObjectDetectionFromCardFinder(previewImage, cardFinder)

    val scaledPreviewImage = scaleAndPositionPreviewImage(fullImage, previewImage)
    val previewScale = scaledPreviewImage.width().toFloat() / previewImage.width

    // Scale the objectDetectionSquare to match the scaledPreviewImage
    val scaledObjectDetectionSquare = Rect(
        (objectDetectionSquare.left * previewScale).roundToInt(),
        (objectDetectionSquare.top * previewScale).roundToInt(),
        (objectDetectionSquare.right * previewScale).roundToInt(),
        (objectDetectionSquare.bottom * previewScale).roundToInt()
    )

    // Position the scaledObjectDetectionSquare on the fullImage
    return Rect(
        scaledObjectDetectionSquare.left + scaledPreviewImage.left,
        scaledObjectDetectionSquare.top + scaledPreviewImage.top,
        scaledObjectDetectionSquare.right + scaledPreviewImage.left,
        scaledObjectDetectionSquare.bottom + scaledPreviewImage.top
    )
}
