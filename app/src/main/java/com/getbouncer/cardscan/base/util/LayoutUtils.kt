package com.getbouncer.cardscan.base.util

import android.content.res.Resources
import android.graphics.Rect
import android.util.Rational
import android.util.Size
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A standard credit card is 85.60mm x 53.98mm. This is close enough to a ratio of 8 x 5.
 */
private val STANDARD_CARD_RATIO = Rational(8, 5)

/**
 * Convert DP to pixels
 */
private fun dpToPx(dp: Int): Int = (dp * Resources.getSystem().displayMetrics.density).roundToInt()

/**
 * Calculate a rectangle where the card preview indicator should exist on the preview image.
 *
 * @param previewImage: The size of the preview image on which to place the card
 * @param minimumPaddingDp: The minimum padding in DP between the edge of the preview image and the
 *                          edge of the card crop.
 * @param verticalWeight: A float from 0 to 1 indicating where vertically the preview should be
 *                        positioned. 0 is at the top, 1 is at the bottom, 0.5 is in the middle.
 * @param horizontalWeight: A float from 0 to 1 indicating where horizontally the preview should be
 *                          positioned. 0 is at the left, 1 is at the right, 0.5 is in the middle.
 * @param cardRatio: The X/Y ratio of the card preview image. This should match the ratio of the ML
 *                   models that operate on the card.
 */
fun calculateCardPreviewRect(
    previewImage: Size,
    minimumPaddingDp: Int = 0,
    verticalWeight: Float = 0.5F,
    horizontalWeight: Float = 0.5F,
    cardRatio: Rational = STANDARD_CARD_RATIO
): Rect {
    val minimumPaddingPx =
        dpToPx(minimumPaddingDp)

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
    val verticalPosition = ((paddedSize.height - squareSize.height) * verticalWeight).roundToInt()
        + (squareSize.height - cardSize.height) / 2
    val horizontalPosition = ((paddedSize.width - squareSize.width) * horizontalWeight).roundToInt()
        + (squareSize.width - cardSize.width) / 2

    // calculate the bounds of the card preview
    val cardLeft = minimumPaddingPx + horizontalPosition
    val cardTop = minimumPaddingPx + verticalPosition
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
 * Determine the maximum size of rectangle with a given aspect ratio (X/Y) that can fit inside the
 * specified area.
 */
private fun maxAspectRatioInSize(area: Size, aspectRatio: Rational): Size {
    var width = area.width
    var height = (width / (aspectRatio.toFloat())).roundToInt()

    return if (height <= area.height) {
        Size(area.width, height)
    } else {
        height = area.height
        width = (height * (aspectRatio.toFloat())).roundToInt()
        Size(min(width, area.width), height)
    }
}

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
 * Calculate the crop from the [fullImage] for the credit card based on the [previewCard] within
 * the [previewImage].
 *
 * Note: This algorithm makes some assumptions:
 * 1. the previewImage and the fullImage are centered relative to each other.
 * 2. the fullImage circumscribes the previewImage. I.E. they share at least one dimension, and the
 *    previewImage is smaller than or the same size as the fullImage
 * 3. the fullImage and the previewImage have the same orientation
 */
fun calculateCardCrop(fullImage: Size, previewImage: Size, previewCard: Rect): Rect {
    assert(previewCard.left >= 0
            && previewCard.right <= previewImage.width
            && previewCard.top >= 0
            && previewCard.bottom <= previewImage.height
    ) { "Preview card is outside preview image bounds" }

    // Scale the previewImage to match the fullImage
    val scaledPreviewImage = scaleAndPositionPreviewImage(fullImage, previewImage)
    val previewScale = scaledPreviewImage.width().toFloat() / previewImage.width

    // Scale the previewCard to match the scaledPreviewImage
    val scaledPreviewCard = Rect(
        (previewCard.left * previewScale).roundToInt(),
        (previewCard.top * previewScale).roundToInt(),
        (previewCard.right * previewScale).roundToInt(),
        (previewCard.bottom * previewScale).roundToInt()
    )

    // Position the scaledPreviewCard on the fullImage
    return Rect(
        scaledPreviewCard.left + scaledPreviewImage.left,
        scaledPreviewCard.top + scaledPreviewImage.top,
        scaledPreviewCard.right + scaledPreviewImage.left,
        scaledPreviewCard.bottom + scaledPreviewImage.top
    )
}

private fun calculateObjectDetectionFromPreviewCard(previewImage: Size, previewCard: Rect ): Rect =
    if (previewCard.width() > previewCard.height()) {
        val objectDetectionSquareTop =
            max(0, previewCard.top + previewCard.height() / 2 - previewCard.width() / 2)
        val objectDetectionSquareBottom =
            min(previewImage.height, objectDetectionSquareTop + previewCard.width())
        Rect(
            /* left */ previewCard.left,
            /* top */ objectDetectionSquareTop,
            /* right */ previewCard.right,
            /* bottom */ objectDetectionSquareBottom
        )
    } else {
        val objectDetectionSquareLeft =
            max(0, previewCard.left + previewCard.width() / 2 - previewCard.height() / 2)
        val objectDetectionSquareRight =
            min(previewImage.width, objectDetectionSquareLeft + previewCard.height())
        Rect(
            /* left */ objectDetectionSquareLeft,
            /* top */ previewCard.top,
            /* right */ objectDetectionSquareRight,
            /* bottom */ previewCard.bottom
        )
    }

fun calculateObjectDetectionCrop(fullImage: Size, previewImage: Size, previewCard: Rect): Rect {
    assert(previewCard.left >= 0
            && previewCard.right <= previewImage.width
            && previewCard.top >= 0
            && previewCard.bottom <= previewImage.height
    ) { "Preview card is outside preview image bounds" }

    // Calculate the object detection square based on the previewCard, limited by the preview
    val objectDetectionSquare = calculateObjectDetectionFromPreviewCard(previewImage, previewCard)

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

/**
* Find the largest ratio crop that fits in the full image
*/
fun calculateScreenDetectionCrop(fullImage: Size, requiredRatio: Rational): Rect {
    val maximumSize = maxAspectRatioInSize(fullImage, requiredRatio)
    val offsetLeft = (fullImage.width - maximumSize.width) / 2
    val offsetTop = (fullImage.height - maximumSize.height) / 2

    return Rect(
        /* left */ offsetLeft,
        /* top */ offsetTop,
        /* right */ offsetLeft + maximumSize.width,
        /* bottom */ offsetTop + maximumSize.height
    )
}
