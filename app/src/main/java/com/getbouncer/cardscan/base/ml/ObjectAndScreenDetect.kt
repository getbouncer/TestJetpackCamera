package com.getbouncer.cardscan.base.ml

import com.getbouncer.cardscan.base.image.ScanImage
import com.getbouncer.cardscan.base.Analyzer
import com.getbouncer.cardscan.base.AnalyzerFactory
import com.getbouncer.cardscan.base.ml.ssd.DetectionBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlin.time.ExperimentalTime

@ExperimentalTime
class ObjectAndScreenDetect(
    private val coroutineScope: CoroutineScope,
    private val objectDetect: SSDObjectDetect,
    private val screenDetect: ScreenDetect
) : Analyzer<ScanImage, ObjectAndScreenDetect.Prediction> {

    data class Prediction(val objectBoxes: List<DetectionBox>, val screenResult: List<Float>)

    override suspend fun analyze(data: ScanImage): Prediction {
        val objectFuture = coroutineScope.async { objectDetect.analyze(data) }
        val screenFuture = coroutineScope.async { screenDetect.analyze(data) }

        return Prediction(
            objectFuture.await(),
            screenFuture.await()
        )
    }

    class Factory(
        private val coroutineScope: CoroutineScope,
        private val objectFactory: SSDObjectDetect.Factory,
        private val screenFactory: ScreenDetect.Factory
    ) : AnalyzerFactory<ObjectAndScreenDetect> {
        override val isThreadSafe: Boolean = true

        suspend fun warmUp(): Boolean {
            val objWarmUp = coroutineScope.async { objectFactory.warmUp() }
            val screenWarmUp = coroutineScope.async { screenFactory.warmUp() }
            return objWarmUp.await() && screenWarmUp.await()
        }

        override fun newInstance(): ObjectAndScreenDetect? =
            objectFactory.newInstance()?.let { objectDetect ->
                screenFactory.newInstance()?.let { screenDetect ->
                    ObjectAndScreenDetect(coroutineScope, objectDetect, screenDetect)
                }
            }
    }
}