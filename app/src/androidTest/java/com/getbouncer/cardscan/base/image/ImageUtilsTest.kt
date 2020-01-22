package com.getbouncer.cardscan.base.image

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Size
import androidx.core.graphics.drawable.toBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.getbouncer.cardscan.base.test.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer


@RunWith(AndroidJUnit4::class)
class ImageUtilsTest {

    private val testResources: Resources = InstrumentationRegistry.getInstrumentation().context.resources

    @Test
    fun bitmap_toRGBByteBuffer_fromPhoto_isCorrect() {
        val bitmap = testResources.getDrawable(R.drawable.ocr_card_numbers_clear, null).toBitmap()
        assertNotNull(bitmap)
        assertEquals("Bitmap width is not expected", 600, bitmap.width)
        assertEquals("Bitmap height is not expected", 375, bitmap.height)

        // convert the bitmap to a byte buffer
        val convertedImage = bitmap.toRGBByteBuffer(mean = 127.5f, std = 128.5f)

        // read in an expected converted file
        val rawStream = testResources.openRawResource(R.raw.ocr_card_numbers_clear)
        val rawBytes = rawStream.readBytes()
        val rawImage = ByteBuffer.wrap(rawBytes)
        rawStream.close()

        // check the size of the files
        assertEquals("File size mismatch", rawImage.limit(), convertedImage.limit())
        rawImage.rewind()
        convertedImage.rewind()

        // check each byte of the files
        var encounteredNonZeroByte = false
        while (convertedImage.position() < convertedImage.limit()) {
            val rawImageByte = rawImage.get()
            encounteredNonZeroByte = encounteredNonZeroByte || rawImageByte.toInt() != 0
            assertEquals("Difference at byte ${rawImage.position()}", rawImageByte, convertedImage.get())
        }

        assertTrue("Bytes were all zero", encounteredNonZeroByte)
    }

    @Test
    fun bitmap_toRGBByteBuffer_generated_isCorrect() {
        val bitmap = generateSampleBitmap()
        assertNotNull(bitmap)
        assertEquals("Bitmap width is not expected", 100, bitmap.width)
        assertEquals("Bitmap height is not expected", 100, bitmap.height)

        // convert the bitmap to a byte buffer
        val convertedImage = bitmap.toRGBByteBuffer(mean = 127.5f, std = 128.5f)

        // read in an expected converted file
        val rawStream = testResources.openRawResource(R.raw.sample_bitmap)
        val rawBytes = rawStream.readBytes()
        val rawImage = ByteBuffer.wrap(rawBytes)
        rawStream.close()

        // check the size of the files
        assertEquals("File size mismatch", rawImage.limit(), convertedImage.limit())
        rawImage.rewind()
        convertedImage.rewind()

        // check each byte of the files
        var encounteredNonZeroByte = false
        while (convertedImage.position() < convertedImage.limit()) {
            val rawImageByte = rawImage.get()
            encounteredNonZeroByte = encounteredNonZeroByte || rawImageByte.toInt() != 0
            assertEquals("Difference at byte ${rawImage.position()}", rawImageByte, convertedImage.get())
        }

        assertTrue("Bytes were all zero", encounteredNonZeroByte)
    }

    @Test
    fun bitmap_scale_isCorrect() {
        // read in a sample bitmap file
        val bitmap = testResources.getDrawable(R.drawable.ocr_card_numbers_clear, null).toBitmap()
        assertNotNull(bitmap)
        assertEquals("Bitmap width is not expected", 600, bitmap.width)
        assertEquals("Bitmap height is not expected", 375, bitmap.height)

        // scale the bitmap
        val scaledBitmap = bitmap.scale(Size(bitmap.width / 5, bitmap.height / 5))

        // check the expected sizes of the images
        assertEquals(
            "Scaled images are of the wrong size",
            Size(bitmap.width / 5, bitmap.height / 5),
            Size(scaledBitmap.width, scaledBitmap.height)
        )

        // check each pixel of the images
        var encounteredNonZeroPixel = false
        for (x in 0 until scaledBitmap.width) {
            for (y in 0 until scaledBitmap.height) {
                encounteredNonZeroPixel = encounteredNonZeroPixel || scaledBitmap.getPixel(x, y) != 0
            }
        }

        assertTrue("Pixels were all zero", encounteredNonZeroPixel)
    }

    private fun generateSampleBitmap(size: Size = Size(100, 100)): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        for (x in 0 until size.width) {
            for (y in 0 until size.height) {
                val red = 255 * x / size.width
                val green = 255 * y / size.height
                val blue = 255 * x / size.height
                paint.color = Color.rgb(red, green, blue)
                canvas.drawRect(RectF(x.toFloat(), y.toFloat(), x + 1F, y + 1F), paint)
            }
        }

        canvas.drawBitmap(bitmap, 0F, 0F, paint)

        return bitmap
    }
}