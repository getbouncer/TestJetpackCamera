package com.getbouncer.cardscan.base.ml

import android.os.Handler
import android.os.Looper
import com.getbouncer.cardscan.base.domain.CardExpiration
import com.getbouncer.cardscan.base.domain.CardNumber
import com.getbouncer.cardscan.base.domain.CardOcrResult
import com.getbouncer.cardscan.base.domain.CardResult

/**
 * A listener to track when card results are available
 */
interface CardResultListener<ImageFormat> {

    /**
     * The final result of a model is available.
     *
     * @param result: the card result from the model
     * @param frames: frames captured during processing that can be used in the completion loop
     */
    fun onCardResult(result: CardOcrResult, frames: List<Pair<ImageFormat, Int>>)

    /**
     * A result is available, but the model is still processing more frames. This is useful for
     * displaying the debug window.
     *
     * @param result: the card result from the model
     * @param frame:  the frame that produced this result.
     */
    fun onInterimCardResult(result: CardOcrResult, frame: ImageFormat?)

    /**
     * The processing rate has been updated. This is useful for debugging and measuring performance.
     */
    fun onUpdateProcessingRate(avgFramesPerSecond: Double, currentFramesPerSecond: Double)
}

data class MLCardResultManagerConfig(
    val resultTimeoutMs: Int,
    val resultMaxCount: Int,
    val maxObjectDetectionFrames: Int
) {

    class Builder {
        companion object {
            private const val DEFAULT_RESULT_TIMEOUT_MS = 1500
            private const val DEFAULT_RESULT_MAX_COUNT = 20
            private const val DEFAULT_MAX_OBJECT_DETECTION_FRAMES = 3
        }

        private var resultTimeoutMs: Int =
            DEFAULT_RESULT_TIMEOUT_MS
        private var resultMaxCount: Int =
            DEFAULT_RESULT_MAX_COUNT
        private var maxObjectDetectionFrames: Int =
            DEFAULT_MAX_OBJECT_DETECTION_FRAMES

        fun withResultTimeoutMs(resultTimeoutMs: Int) {
            this.resultTimeoutMs = resultTimeoutMs
        }

        fun withResultMaxCount(resultMaxCount: Int) {
            this.resultMaxCount = resultMaxCount
        }

        fun withMaxObjectDetectionFrames(maxObjectDetectionFrames: Int) {
            this.maxObjectDetectionFrames = maxObjectDetectionFrames
        }

        fun build() =
            MLCardResultManagerConfig(
                resultTimeoutMs,
                resultMaxCount,
                maxObjectDetectionFrames
            )
    }
}

/**
 * Keep track of the results from the ML models. Count the number of times the ML models send each
 * number as a result, and when the first result is received.
 *
 * The listener will be notified of a result once a threshold number of matching results is received
 * or the time since the first result exceeds a threshold.
 */
class MLCardResultManager<ImageFormat>(
    private val config: MLCardResultManagerConfig,
    private val listener: CardResultListener<ImageFormat>
) : ResultHandler<CardResult<ImageFormat>> {

    private var firstFrameTimeMs: Long? = null
    private var lastFrameTimeMs: Long? = null
    private var framesProcessed: Long = 0

    private var firstResultTimeMs: Long? = null
    private val numberResults = mutableMapOf<CardNumber, Int>()
    private val expirationResults = mutableMapOf<CardExpiration, Int>()
    private val imageResults = mutableListOf<Pair<ImageFormat, Int>>()

    override fun onResult(result: CardResult<ImageFormat>) {
        trackAndCalculateFrameRate()

        // TODO: this should be based on memory, not fixed. It should also be the least blurry
        //       images available.
        if (result.image != null && imageResults.size < config.maxObjectDetectionFrames) {
            imageResults.add(Pair(result.image, result.rotationDegrees))
        }

        if (result.ocrResult.number != null && firstResultTimeMs == null) {
            firstResultTimeMs = System.currentTimeMillis()
        }

        val numberCount = storeNumber(result.ocrResult.number)
        storeExpiration(result.ocrResult.expiration)

        if (hasReachedTimeout() || numberCount >= config.resultMaxCount) {
            notifyOfResult()
        } else {
            notifyOfInterimResult(result.image)
        }
    }

    /**
     * Calculate the current rate at which the model is processing images. Notify the listener of
     * the result.
     */
    private fun trackAndCalculateFrameRate() {
        val currentTimeMs = System.currentTimeMillis()
        val lastFrameTimeMs = this.lastFrameTimeMs ?: currentTimeMs
        val firstFrameTimeMs = this.firstFrameTimeMs ?: currentTimeMs
        this.lastFrameTimeMs = currentTimeMs

        framesProcessed++

        if (this.firstFrameTimeMs == null) {
            this.firstFrameTimeMs = currentTimeMs
        }

        val currentFramesPerSecond = if (currentTimeMs > lastFrameTimeMs) {
            1000.0 / (currentTimeMs - lastFrameTimeMs)
        } else {
            0.0
        }

        val avgFramesPerSecond = if (currentTimeMs > firstFrameTimeMs) {
            framesProcessed * 1000.0 / (currentTimeMs - firstFrameTimeMs)
        } else {
            0.0
        }

        notifyOfFrameRate(avgFramesPerSecond, currentFramesPerSecond)
    }

    /**
     * Send the listener the current frame rates on the main thread.
     */
    private fun notifyOfFrameRate(avgFramesPerSecond: Double, currentFramesPerSecond: Double) {
        runOnMainThread(Runnable {
            listener.onUpdateProcessingRate(avgFramesPerSecond, currentFramesPerSecond)
        })
    }

    /**
     * Send the listener the result from this model on the main thread.
     */
    private fun notifyOfResult() {
        runOnMainThread(Runnable {
            listener.onCardResult(
                CardOcrResult(getMostLikelyNumber(), getMostLikelyExpiry()),
                imageResults
            )
        })
    }

    /**
     * Send the listener an interim result from this model on the main thread.
     */
    private fun notifyOfInterimResult(image: ImageFormat?) {
        runOnMainThread(Runnable {
            listener.onInterimCardResult(
                CardOcrResult(getMostLikelyNumber(), getMostLikelyExpiry()),
                image
            )
        })
    }

    private fun runOnMainThread(runnable: Runnable) {
        val handler = Handler(Looper.getMainLooper())
        handler.post(runnable)
    }

    private fun getMostLikelyNumber(): CardNumber? = numberResults.maxBy { it.value }?.key

    private fun getMostLikelyExpiry(): CardExpiration? = expirationResults.maxBy { it.value }?.key

    @Synchronized
    private fun storeNumber(number: CardNumber?): Int =
        if (number != null) {
            val count = 1 + (numberResults[number] ?: 0)
            numberResults[number] = count
            count
        } else {
            0
        }

    @Synchronized
    private fun storeExpiration(expiration: CardExpiration?): Int =
        if (expiration != null) {
            val count = 1 + (expirationResults[expiration] ?: 0)
            expirationResults[expiration] = count
            count
        } else {
            0
        }

    private fun hasReachedTimeout(): Boolean {
        val firstResultTimeMs = this.firstResultTimeMs
        return firstResultTimeMs != null &&
                System.currentTimeMillis() - firstResultTimeMs > config.resultTimeoutMs
    }
}