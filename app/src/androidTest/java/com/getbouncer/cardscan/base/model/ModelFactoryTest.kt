package com.getbouncer.cardscan.base.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.getbouncer.cardscan.base.R
import com.getbouncer.cardscan.base.ml.ResourceLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelFactoryTest {
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    @SmallTest
    fun loadModelFromResource_correct() {
        val mappedByteBuffer = ResourceLoader(appContext).loadModelFromResource(R.raw.ssdelrond0136)
        assertEquals("File is not expected size", 3265588, mappedByteBuffer.limit())
        mappedByteBuffer.rewind()

        // ensure not all bytes are zero
        var encounteredNonZeroByte = false
        while (!encounteredNonZeroByte) {
            encounteredNonZeroByte = mappedByteBuffer.get().toInt() != 0
        }
        assertTrue("All bytes were zero", encounteredNonZeroByte)

        // ensure bytes 5-8 are "TFL3" ASCII encoded
        mappedByteBuffer.position(4)
        assertEquals(mappedByteBuffer.get().toChar(), 'T')
        assertEquals(mappedByteBuffer.get().toChar(), 'F')
        assertEquals(mappedByteBuffer.get().toChar(), 'L')
        assertEquals(mappedByteBuffer.get().toChar(), '3')
    }
}
