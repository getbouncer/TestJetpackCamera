package com.getbouncer.cardscan.base.model

import androidx.core.graphics.drawable.toBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.getbouncer.cardscan.base.image.ScanImage
import com.getbouncer.cardscan.base.ml.SSDObjectDetect
import com.getbouncer.cardscan.base.ml.SSDOcr
import com.getbouncer.cardscan.base.test.R
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime

@ExperimentalTime
@RunWith(AndroidJUnit4::class)
class ModelTest {
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext = InstrumentationRegistry.getInstrumentation().context

    @Test
    @MediumTest
    fun resourceModelExecution_works() = runBlocking {
        val bitmap = testContext.resources.getDrawable(R.drawable.ocr_card_numbers_clear, null).toBitmap()
        val model = SSDOcr.Factory(appContext).newInstance()
        assertNotNull(model)

        val prediction = model.analyze(
            ScanImage(
                fullImage = bitmap,
                objImage = bitmap,
                ocrImage = bitmap
            )
        )
        assertNotNull(prediction)
        assertNull(prediction.expiry)
        assertNotNull(prediction.number)
        assertEquals("4557095462268383", prediction.number?.number)
    }

    @Test
    @MediumTest
    fun resourceModelExecution_worksRepeatedly() = runBlocking  {
        val bitmap = testContext.resources.getDrawable(R.drawable.ocr_card_numbers_clear, null).toBitmap()
        val model = SSDOcr.Factory(appContext).newInstance()
        assertNotNull(model)

        val prediction1 = model.analyze(
            ScanImage(
                fullImage = bitmap,
                objImage = bitmap,
                ocrImage = bitmap
            )
        )
        val prediction2 = model.analyze(
            ScanImage(
                fullImage = bitmap,
                objImage = bitmap,
                ocrImage = bitmap
            )
        )
        assertNotNull(prediction1)
        assertNull(prediction1.expiry)
        assertNotNull(prediction1.number)
        assertEquals("4557095462268383", prediction1.number?.number)

        assertNotNull(prediction2)
        assertNull(prediction2.expiry)
        assertNotNull(prediction2.number)
        assertEquals("4557095462268383", prediction2.number?.number)
    }

    @Test
    @MediumTest
    fun webModelExecution_works() = runBlocking {
        val bitmap = testContext.resources.getDrawable(R.drawable.obj_card_numbers_clear, null).toBitmap()
        val model = SSDObjectDetect.Factory(appContext).newInstance()
        assertNotNull(model)

        val prediction = model.analyze(
            ScanImage(
                fullImage = bitmap,
                objImage = bitmap,
                ocrImage = bitmap
            )
        )
        assertNotNull(prediction)
        assertEquals(1, prediction.size)
        val object1 = prediction[0]
        assertEquals(0.7029851F, object1.confidence)
        assertEquals(3, object1.label)
    }

}