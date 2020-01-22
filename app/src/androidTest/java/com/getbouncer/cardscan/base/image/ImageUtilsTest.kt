package com.getbouncer.cardscan.base.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel

@RunWith(AndroidJUnit4::class)
class ImageUtilsTest {
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bitmap_toRGBByteBuffer_isCorrect() {
        // read in a sample bitmap file
        val bitmap = appContext.resources.getDrawable(R.drawable.ocr_card_numbers_clear).toBitmap()
//        val bitmapFileInputStream = FileInputStream("drawable/ocr_card_numbers_clear.png")
//        val bitmap = BitmapFactory.decodeStream(bitmapFileInputStream)
//        bitmap.config = Bitmap.Config.ARGB_8888
//        bitmapFileInputStream.close()
        assertNotNull(bitmap)

        var bitmapIsNotEmpty = false
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                bitmapIsNotEmpty = bitmapIsNotEmpty || bitmap.getPixel(x, y) != 0
            }
        }

        assertTrue("Sample bitmap was empty", bitmapIsNotEmpty)

        // convert the bitmap to a byte buffer
        val convertedImage = bitmap.toRGBByteBuffer(mean = 127.5f, std = 128.5f)
        assertEquals("Bitmap width is not expected", 600, bitmap.width)
        assertEquals("Bitmap height is not expected", 375, bitmap.height)

        // read in an expected converted file
        val rawFile = File("src/androidTest/resources/raw/ocr_card_numbers_clear.bin")
        assertTrue(rawFile.exists())
        val rawFileInputStream = FileInputStream(rawFile)
        val rawImage = rawFileInputStream.channel.map(FileChannel.MapMode.READ_ONLY, 0, rawFile.length())
        rawFileInputStream.close()

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
        val bitmapFileInputStream = FileInputStream("src/test/res/drawable/ocr_card_numbers_clear.png")
        val bitmap = BitmapFactory.decodeStream(bitmapFileInputStream)
        bitmap.config = Bitmap.Config.ARGB_8888
        bitmapFileInputStream.close()
        assertNotNull(bitmap)

        // scale the bitmap
        val scaledBitmap = bitmap.scale(Size(bitmap.width / 2, bitmap.height / 2))

        // check the expected sizes of the images
        assertEquals("Scaled images are of the wrong size", Size(bitmap.width / 2, bitmap.height / 2), Size(scaledBitmap.width, scaledBitmap.height))

        // check each pixel of the images
        var encounteredNonZeroPixel = false
        for (x in 0 until scaledBitmap.width) {
            for (y in 0 until scaledBitmap.height) {
                val originalPixel = bitmap.getPixel(x * 2, y * 2)
                val scaledPixel = scaledBitmap.getPixel(x, y)
                encounteredNonZeroPixel = encounteredNonZeroPixel || scaledPixel != 0
                assertEquals("Difference at pixel $x, $y", originalPixel, scaledPixel)
            }
        }

        assertTrue("Pixels were all zero", encounteredNonZeroPixel)
    }
}