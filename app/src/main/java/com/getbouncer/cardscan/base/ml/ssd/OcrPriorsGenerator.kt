package com.getbouncer.cardscan.base.ml.ssd

import android.util.Size
import com.getbouncer.cardscan.base.ml.SSDOcr
import com.getbouncer.cardscan.base.ml.ssd.domain.SizeAndCenter
import com.getbouncer.cardscan.base.ml.ssd.domain.clampAll
import com.getbouncer.cardscan.base.ml.ssd.domain.sizeAndCenter
import kotlin.math.sqrt
import kotlin.time.ExperimentalTime

private const val NUMBER_OF_PRIORS = 3

@ExperimentalTime
fun combinePriors(): Array<SizeAndCenter> {
    val priorsOne: Array<SizeAndCenter> =
        generatePriors(
            featureMapSize = Size(38, 24),
            shrinkage = Size(16, 16),
            boxSizeMin = 14F,
            boxSizeMax = 30F,
            aspectRatio = 3F
        )

    val priorsTwo: Array<SizeAndCenter> =
        generatePriors(
            featureMapSize = Size(19, 12),
            shrinkage = Size(31, 31),
            boxSizeMin = 30F,
            boxSizeMax = 45F,
            aspectRatio = 3F
        )

    return (priorsOne + priorsTwo).apply { forEach { it.clampAll(0F, 1F) } }
}

@ExperimentalTime
private fun generatePriors(
    featureMapSize: Size,
    shrinkage: Size,
    boxSizeMin: Float,
    boxSizeMax: Float,
    aspectRatio: Float
): Array<SizeAndCenter> {
    val scaleWidth = SSDOcr.TRAINED_IMAGE_SIZE.width / shrinkage.width.toFloat()
    val scaleHeight = SSDOcr.TRAINED_IMAGE_SIZE.height / shrinkage.height.toFloat()
    val ratio = sqrt(aspectRatio)

    fun generatePrior(column: Int, row: Int, sizeFactor: Float, ratio: Float) =
        sizeAndCenter(
            centerX = (column + 0.5F) / scaleWidth,
            centerY = (row + 0.5F) / scaleHeight,
            width = sizeFactor / SSDOcr.TRAINED_IMAGE_SIZE.width,
            height = sizeFactor / SSDOcr.TRAINED_IMAGE_SIZE.height * ratio
        )

    return Array(featureMapSize.width * featureMapSize.height * NUMBER_OF_PRIORS) { index ->
        val row = index / NUMBER_OF_PRIORS / featureMapSize.width
        val column = (index / NUMBER_OF_PRIORS) % featureMapSize.width
        when (index % NUMBER_OF_PRIORS) {
            0 -> generatePrior(column, row, boxSizeMin, 1F)
            1 -> generatePrior(column, row, sqrt(boxSizeMax * boxSizeMin), ratio)
            else -> generatePrior(column, row, boxSizeMin, ratio)
        }
    }
}