package com.getbouncer.cardscan.base.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.getbouncer.cardscan.base.R
import com.getbouncer.cardscan.base.ResourceLoader
import com.getbouncer.cardscan.base.WebLoader
import com.getbouncer.cardscan.base.ml.SSDObjectDetect
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@ExperimentalTime
@RunWith(AndroidJUnit4::class)
class ModelFactoryTest {
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    @SmallTest
    fun loadModelFromResource_correct() {
        val byteBuffer = ResourceLoader(appContext).loadModelFromResource(R.raw.ssdelrond0136)
        assertEquals(3265588, byteBuffer.limit(), "File is not expected size")
        byteBuffer.rewind()

        // ensure not all bytes are zero
        var encounteredNonZeroByte = false
        while (!encounteredNonZeroByte) {
            encounteredNonZeroByte = byteBuffer.get().toInt() != 0
        }
        assertTrue(encounteredNonZeroByte, "All bytes were zero")

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
        val localFileName = "test_loadModelFromWeb_correct"
        val localFile = File(appContext.cacheDir, localFileName)
        if (localFile.exists()) {
            localFile.delete()
        }

        val factory = SSDObjectDetect.Factory(appContext)
        val byteBuffer = WebLoader(appContext).loadModelFromWeb(
            factory.url,
            localFileName,
            factory.hash
        )
        assertEquals(4983688, byteBuffer.limit(), "File is not expected size")
        byteBuffer.rewind()

        // ensure not all bytes are zero
        var encounteredNonZeroByte = false
        while (!encounteredNonZeroByte) {
            encounteredNonZeroByte = byteBuffer.get().toInt() != 0
        }
        assertTrue(encounteredNonZeroByte, "All bytes were zero")

        // ensure bytes 5-8 are "TFL3" ASCII encoded
        byteBuffer.position(4)
        assertEquals(byteBuffer.get().toChar(), 'T')
        assertEquals(byteBuffer.get().toChar(), 'F')
        assertEquals(byteBuffer.get().toChar(), 'L')
        assertEquals(byteBuffer.get().toChar(), '3')
    }

    @Test
    @SmallTest
    fun loadModelFromWeb_fail() {
        val localFileName = "test_loadModelFromWeb_fail"
        val localFile = File(appContext.cacheDir, localFileName)
        if (localFile.exists()) {
            localFile.delete()
        }

        val factory = SSDObjectDetect.Factory(appContext)
        assertFailsWith<FileNotFoundException> {
            WebLoader(appContext).loadModelFromWeb(
                URL(factory.url.toString() + "-BROKEN"),
                localFileName,
                factory.hash
            )
        }
    }
}
