package com.getbouncer.cardscan.base.ml

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.getbouncer.cardscan.base.domain.CardExpiry
import com.getbouncer.cardscan.base.domain.CardImage
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
     * @param dataFrame: the data frame that produced this result.
     */
    fun onInterimResult(result: ModelResult, dataFrame: DataFrame)

    /**
     * The processing rate has been updated. This is useful for debugging and measuring performance.
     *
     * @param avgFramesPerSecond: The average frame rate at which the model is running
     * @param instFramesPerSecond: The instantaneous frame rate at which the model is running
     */
    fun onUpdateProcessingRate(avgFramesPerSecond: Double, instFramesPerSecond: Double)
}

data class MLResultAggregatorConfig(
    val maxTotalAggregationTimeNs: Long,
    val maxSavedFrames: Int,
    val frameRateUpdateIntervalNs: Long,
    val frameStorageBytes: Int
) {

    class Builder {
        companion object {
            private const val DEFAULT_MAX_TOTAL_AGGREGATION_TIME_NS = 1500000000L // 1.5 seconds
            private const val DEFAULT_MAX_OBJECT_DETECTION_FRAMES = -1  // Unlimited saved frames
            private const val DEFAULT_FRAME_RATE_UPDATE_INTERVAL_NS = 1000000000L // 1 second
            private const val DEFAULT_FRAME_STORAGE_BYTES = 0x4000000 // 64MB
        }

        private var maxTotalAggregationTimeNs: Long =
            DEFAULT_MAX_TOTAL_AGGREGATION_TIME_NS
        private var maxSavedFrames: Int =
            DEFAULT_MAX_OBJECT_DETECTION_FRAMES
        private var frameRateUpdateIntervalNs: Long =
            DEFAULT_FRAME_RATE_UPDATE_INTERVAL_NS
        private var frameStorageBytes: Int =
            DEFAULT_FRAME_STORAGE_BYTES

        fun withMaxTotalAggregationTimeNs(maxTotalAggregationTimeNs: Long) {
            this.maxTotalAggregationTimeNs = maxTotalAggregationTimeNs
        }

        fun withMaxSavedFrames(maxSavedFrames: Int) {
            this.maxSavedFrames = maxSavedFrames
        }

        fun withFrameRateUpdateIntervalNs(frameRateUpdateIntervalNs: Long) {
            this.frameRateUpdateIntervalNs = frameRateUpdateIntervalNs
        }

        fun withFrameStorageBytes(frameStorageBytes: Int) {
            this.frameStorageBytes = frameStorageBytes
        }

        fun build() =
            MLResultAggregatorConfig(
                maxTotalAggregationTimeNs,
                maxSavedFrames,
                frameRateUpdateIntervalNs,
                frameStorageBytes
            )
    }
}

abstract class MLResultAggregator<DataFrame, ModelResult>(
    private val config: MLResultAggregatorConfig,
    private val listener: MLAggregateResultListener<DataFrame, ModelResult>
) : ResultHandler<DataFrame, ModelResult> {

    companion object {
        private const val NANOS_IN_SECONDS = 1000000000
    }

    private var firstResultTimeNs: Long? = null
    private var firstFrameTimeNs: Long? = null
    private var lastNotifyTimeNs: Long? = null
    private var totalFramesProcessed: Long = 0
    private var framesProcessedSinceLastUpdate: Long = 0

    private val savedFrames = mutableListOf<DataFrame>()

    override fun onResult(result: ModelResult, data: DataFrame) {
        trackAndCalculateFrameRate()

        if (isValidResult(result) && firstResultTimeNs == null) {
            firstResultTimeNs = System.nanoTime()
        }

        // TODO: This should store the least blurry images available.
        if (shouldSaveFrame(result, data)
            && (config.maxSavedFrames < 0 || savedFrames.size < config.maxSavedFrames)
            && (config.frameStorageBytes < 0 || savedFrames.size * getFrameSizeBytes(data) < config.frameStorageBytes)
            && (config.maxSavedFrames >= 0 || config.frameStorageBytes >= 0)
        ) {
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
     * Determine the size in memory that this data frame takes up
     */
    abstract fun getFrameSizeBytes(dataFrame: DataFrame): Int

    /**
     * Calculate the current rate at which the model is processing images. Notify the listener of
     * the result.
     */
    private fun trackAndCalculateFrameRate() {
        val nowNs = System.nanoTime()

        if (this.firstFrameTimeNs == null) {
            this.firstFrameTimeNs = nowNs
        }

        totalFramesProcessed++
        framesProcessedSinceLastUpdate++

        if (shouldNotifyOfFrameRate(nowNs)) {
            val lastNotifyTimeNs = this.lastNotifyTimeNs ?: nowNs
            val firstFrameTimeNs = this.firstFrameTimeNs ?: nowNs

            Log.d("AGW", "DURATION: ${(nowNs - lastNotifyTimeNs) / NANOS_IN_SECONDS}  RECENT FRAMES: $framesProcessedSinceLastUpdate")

            val instFramesPerSecond = if (nowNs > lastNotifyTimeNs) {
                framesProcessedSinceLastUpdate.toDouble() * NANOS_IN_SECONDS / (nowNs - lastNotifyTimeNs)
            } else {
                0.0
            }

            val avgFramesPerSecond = if (nowNs > firstFrameTimeNs) {
                totalFramesProcessed.toDouble() * NANOS_IN_SECONDS / (nowNs - firstFrameTimeNs)
            } else {
                0.0
            }

            framesProcessedSinceLastUpdate = 0
            this.lastNotifyTimeNs = nowNs
            notifyOfFrameRate(avgFramesPerSecond, instFramesPerSecond)
        }
    }

    private fun shouldNotifyOfFrameRate(nowNs: Long): Boolean =
        nowNs - (lastNotifyTimeNs ?: 0) > config.frameRateUpdateIntervalNs

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
abstract class MLCardResultAggregator<ImageFormat>(
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

class MLCardImageResultAggregator(
    config: MLResultAggregatorConfig,
    listener: MLAggregateResultListener<CardImage, CardOcrResult>,
    requiredAgreementCount: Int? = null
) : MLCardResultAggregator<CardImage>(config, listener, requiredAgreementCount) {
    override fun getFrameSizeBytes(dataFrame: CardImage): Int = dataFrame.image.limit()
}
