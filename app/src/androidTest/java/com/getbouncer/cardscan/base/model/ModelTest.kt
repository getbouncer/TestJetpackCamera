package com.getbouncer.cardscan.base.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.getbouncer.cardscan.base.R
import com.getbouncer.cardscan.base.ml.MLResourceModelFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MLResourceModelFactoryTest {
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun loadModelFromResource_correct() {
        val mappedByteBuffer = MLResourceModelFactory(appContext).loadModelFromResource(R.raw.ssdelrond0136)
        assertEquals("File is not expected size", 3265588, mappedByteBuffer.limit())

        mappedByteBuffer.rewind()

        var encounteredNonZeroByte = false
        while (!encounteredNonZeroByte) {
            encounteredNonZeroByte = mappedByteBuffer.get().toInt() != 0
        }

        assertTrue("All bytes were zero", encounteredNonZeroByte)
    }
}