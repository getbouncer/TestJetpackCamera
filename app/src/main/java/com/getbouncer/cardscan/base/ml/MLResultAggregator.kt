package com.getbouncer.cardscan.base.ml

import android.os.Handler
import android.os.Looper
import com.getbouncer.cardscan.base.domain.CardExpiry
import com.getbouncer.cardscan.base.domain.CardNumber
import com.getbouncer.cardscan.base.domain.CardOcrResult

interface MLAggregateResultListener<DataFrame, ModelResult> {

    /**
     * The aggregated result of a model is available.
     *
     * @param result: the card result from the model
     * @param dataFrames: data frames captured during processing that can be used in the completion loop
     */
    fun onResult(result: ModelResult, dataFrames: List<DataFrame>)

    /**
     * An interim result is available, but the model is still processing more data frames. This is
     * useful for displaying a debug window.
     *
     * @param result: the card result from the model
     * @param dataFrames: the data frame that produced this result.
     */
    fun onInterimResult(result: ModelResult, dataFrames: DataFrame)

    /**
     * The processing rate has been updated. This is useful for debugging and measuring performance.
     *
     * @param avgFramesPerSecond: The average frame rate at which the model is running
     * @param instFramesPerSecond: The instantaneous frame rate at which the model is running
     */
    fun onUpdateProcessingRate(avgFramesPerSecond: Double, instFramesPerSecond: Double)
}

data class MLResultAggregatorConfig(
    val maxTotalAggregationTimeNs: Int,
    val maxSavedFrames: Int
) {

    class Builder {
        companion object {
            private const val DEFAULT_MAX_TOTAL_AGGREGATION_TIME_NS = 1500000 // 1.5 seconds
            private const val DEFAULT_MAX_OBJECT_DETECTION_FRAMES = 3
        }

        private var maxTotalAggregationTimeNs: Int =
            DEFAULT_MAX_TOTAL_AGGREGATION_TIME_NS
        private var maxSavedFrames: Int =
            DEFAULT_MAX_OBJECT_DETECTION_FRAMES

        fun withMaxTotalAggregationTimeNs(maxTotalAggregationTimeNs: Int) {
            this.maxTotalAggregationTimeNs = maxTotalAggregationTimeNs
        }

        fun withMaxSavedFrames(maxSavedFrames: Int) {
            this.maxSavedFrames = maxSavedFrames
        }

        fun build() =
            MLResultAggregatorConfig(
                maxTotalAggregationTimeNs,
                maxSavedFrames
            )
    }
}

abstract class MLResultAggregator<DataFrame, ModelResult>(
    private val config: MLResultAggregatorConfig,
    private val listener: MLAggregateResultListener<DataFrame, ModelResult>
) : ResultHandler<DataFrame, ModelResult> {

    private var firstResultTimeNs: Long? = null
    private var firstFrameTimeNs: Long? = null
    private var lastFrameTimeNs: Long? = null
    private var framesProcessed: Long = 0

    private val savedFrames = mutableListOf<DataFrame>()

    override fun onResult(result: ModelResult, data: DataFrame) {
        trackAndCalculateFrameRate()

        if (isValidResult(result) && firstResultTimeNs == null) {
            firstResultTimeNs = System.nanoTime()
        }

        // TODO: this should be based on memory, not fixed. It should also be the least blurry
        //       images available.
        if (shouldSaveFrame(result, data) && savedFrames.size < config.maxSavedFrames) {
            savedFrames.add(data)
        }

        notifyOfInterimResult(result, data)

        val finalResult = aggregateResult(result, hasReachedTimeout())
        if (finalResult != null) {
            notifyOfResult(finalResult, savedFrames)
        }
    }

    /**
     * Aggregate a new result.
     *
     * @param result: The result to aggregate
     * @param mustReturn: If true, this method must return a final result
     */
    abstract fun aggregateResult(result: ModelResult, mustReturn: Boolean): ModelResult?

    /**
     * Determine if a result is valid for tracking purposes
     */
    abstract fun isValidResult(result: ModelResult): Boolean

    /**
     * Determine if a data frame should be saved for the final result
     */
    abstract fun shouldSaveFrame(result: ModelResult, dataFrame: DataFrame): Boolean

    /**
     * Calculate the current rate at which the model is processing images. Notify the listener of
     * the result.
     */
    private fun trackAndCalculateFrameRate() {
        val currentTimeNs = System.nanoTime()
        val lastFrameTimeNs = this.lastFrameTimeNs ?: currentTimeNs
        val firstFrameTimeNs = this.firstFrameTimeNs ?: currentTimeNs
        this.lastFrameTimeNs = currentTimeNs

        framesProcessed++

        if (this.firstFrameTimeNs == null) {
            this.firstFrameTimeNs = currentTimeNs
        }

        val instFramesPerSecond = if (currentTimeNs > lastFrameTimeNs) {
            1000.0 / (currentTimeNs - lastFrameTimeNs)
        } else {
            0.0
        }

        val avgFramesPerSecond = if (currentTimeNs > firstFrameTimeNs) {
            framesProcessed * 1000.0 / (currentTimeNs - firstFrameTimeNs)
        } else {
            0.0
        }

        notifyOfFrameRate(avgFramesPerSecond, instFramesPerSecond)
    }

    /**
     * Send the listener the current frame rates on the main thread.
     */
    private fun notifyOfFrameRate(avgFramesPerSecond: Double, instantaneousFramesPerSecond: Double) {
        runOnMainThread(Runnable {
            listener.onUpdateProcessingRate(avgFramesPerSecond, instantaneousFramesPerSecond)
        })
    }

    /**
     * Send the listener the result from this model on the main thread.
     */
    private fun notifyOfResult(result: ModelResult, dataFrames: List<DataFrame>) {
        runOnMainThread(Runnable { listener.onResult(result, dataFrames) })
    }

    /**
     * Send the listener an interim result from this model on the main thread.
     */
    private fun notifyOfInterimResult(result: ModelResult, dataFrame: DataFrame) {
        runOnMainThread(Runnable { listener.onInterimResult(result, dataFrame) })
    }

    /**
     * Determine if the timeout from the config has been reached
     */
    private fun hasReachedTimeout(): Boolean {
        val firstResultTimeNs = this.firstResultTimeNs
        return firstResultTimeNs != null &&
                System.currentTimeMillis() - firstResultTimeNs > config.maxTotalAggregationTimeNs
    }

    private fun runOnMainThread(runnable: Runnable) {
        val handler = Handler(Looper.getMainLooper())
        handler.post(runnable)
    }
}

/**
 * Keep track of the results from the ML models. Count the number of times the ML models send each
 * number as a result, and when the first result is received.
 *
 * The listener will be notified of a result once a threshold number of matching results is received
 * or the time since the first result exceeds a threshold.
 */
class MLCardResultAggregator<ImageFormat>(
    config: MLResultAggregatorConfig,
    listener: MLAggregateResultListener<ImageFormat, CardOcrResult>,
    private val requiredAgreementCount: Int? = null
) : MLResultAggregator<ImageFormat, CardOcrResult>(config, listener) {

    private val numberResults = mutableMapOf<CardNumber, Int>()
    private val expiryResults = mutableMapOf<CardExpiry, Int>()

    override fun aggregateResult(result: CardOcrResult, mustReturn: Boolean): CardOcrResult? {
        val numberCount = storeNumber(result.number)
        storeExpiry(result.expiry)

        val hasMetRequiredAgreementCount =
            if (requiredAgreementCount != null) numberCount >= requiredAgreementCount else false

        return if (mustReturn || hasMetRequiredAgreementCount) {
            CardOcrResult(getMostLikelyNumber(), getMostLikelyExpiry())
        } else {
            null
        }
    }

    private fun getMostLikelyNumber(): CardNumber? = numberResults.maxBy { it.value }?.key

    private fun getMostLikelyExpiry(): CardExpiry? = expiryResults.maxBy { it.value }?.key

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
    private fun storeExpiry(expiry: CardExpiry?): Int =
        if (expiry != null) {
            val count = 1 + (expiryResults[expiry] ?: 0)
            expiryResults[expiry] = count
            count
        } else {
            0
        }

    override fun isValidResult(result: CardOcrResult): Boolean = result.number != null

    override fun shouldSaveFrame(result: CardOcrResult, dataFrame: ImageFormat): Boolean =
        result.number != null
}