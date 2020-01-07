package com.getbouncer.cardscan.base.domain

import android.util.Size
import java.nio.ByteBuffer

/**
 * An object with a known, fixed size in memory
 */
interface FixedMemorySize {
    val sizeInBytes: Int
}

/**
 *
 */
data class CardImage(val image: ByteBuffer, val rotationDegrees: Int, val size: Size) : FixedMemorySize {
    override val sizeInBytes by lazy { image.limit() }

    override fun toString(): String = "CARD IMAGE SIZE $sizeInBytes"
}