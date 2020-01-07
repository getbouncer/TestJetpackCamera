package com.getbouncer.cardscan.base.image

/**
 * Perform an image transform.
 */
interface ImageTransformer<OriginalFormat, TransformedFormat> {
    fun transformImage(image: OriginalFormat): TransformedFormat
}
