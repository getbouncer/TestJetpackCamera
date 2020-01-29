package com.getbouncer.cardscan.base.ml

import com.getbouncer.cardscan.base.domain.ScanImage
import com.getbouncer.cardscan.base.Analyzer
import com.getbouncer.cardscan.base.AnalyzerFactory
import com.getbouncer.cardscan.base.ml.ssd.DetectionBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

@ExperimentalTime
class ObjectAndScreenDetect(
    private val coroutineScope: CoroutineScope,
    private val objectFactory: SSDObjectDetect.Factory,
    private val screenFactory: ScreenDetect.Factory
) : Analyzer<ScanImage, ObjectAndScreenDetect.Prediction> {

    data class Prediction(val objectBoxes: List<DetectionBox>, val screenResult: List<Float>)

    private val objectDetect by lazy { objectFactory.newInstance() }
    private val screenDetect by lazy { screenFactory.newInstance() }

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

        fun warmUp() {
            coroutineScope.launch { objectFactory.warmUp() }
            coroutineScope.launch { screenFactory.warmUp() }
        }

        override fun newInstance(): ObjectAndScreenDetect =
            ObjectAndScreenDetect(
                coroutineScope,
                objectFactory,
                screenFactory
            )
    }
}