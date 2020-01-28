package com.getbouncer.cardscan.base.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.getbouncer.cardscan.base.R
import com.getbouncer.cardscan.base.ml.ResourceLoader
import com.getbouncer.cardscan.base.ml.WebLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URL

@RunWith(AndroidJUnit4::class)
class ModelFactoryTest {
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    @SmallTest
    fun loadModelFromResource_correct() {
        val byteBuffer = ResourceLoader(appContext).loadModelFromResource(R.raw.ssdelrond0136)
        assertEquals("File is not expected size", 3265588, byteBuffer.limit())
        byteBuffer.rewind()

        // ensure not all bytes are zero
        var encounteredNonZeroByte = false
        while (!encounteredNonZeroByte) {
            encounteredNonZeroByte = byteBuffer.get().toInt() != 0
        }
        assertTrue("All bytes were zero", encounteredNonZeroByte)

        // ensure bytes 5-8 are "TFL3" ASCII encoded
        byteBuffer.position(4)
        assertEquals(byteBuffer.get().toChar(), 'T')
        assertEquals(byteBuffer.get().toChar(), 'F')
        assertEquals(byteBuffer.get().toChar(), 'L')
        assertEquals(byteBuffer.get().toChar(), '3')
    }

    @Test
    @SmallTest
    fun loadModelFromWeb_correct() {
        val byteBuffer = WebLoader(appContext).loadModelFromWeb(
            URL("https://downloads.getbouncer.com/object_detection/v0.0.2/android/ssd.tflite"),
            "_object_detection_v0.0.2_android_ssd.tflite",
            "b7331fd09bf479a20e01b77ebf1b5edbd312639edf8dd883aa7b86f4b7fbfa62"
        )
        assertEquals("File is not expected size", 4983688, byteBuffer.limit())
        byteBuffer.rewind()

        // ensure not all bytes are zero
        var encounteredNonZeroByte = false
        while (!encounteredNonZeroByte) {
            encounteredNonZeroByte = byteBuffer.get().toInt() != 0
        }
        assertTrue("All bytes were zero", encounteredNonZeroByte)

        // ensure bytes 5-8 are "TFL3" ASCII encoded
        byteBuffer.position(4)
        assertEquals(byteBuffer.get().toChar(), 'T')
        assertEquals(byteBuffer.get().toChar(), 'F')
        assertEquals(byteBuffer.get().toChar(), 'L')
        assertEquals(byteBuffer.get().toChar(), '3')
    }
}
