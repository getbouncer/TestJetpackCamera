package com.getbouncer.cardscan.base.ml

import android.os.Handler
import android.os.Looper
import com.getbouncer.cardscan.base.domain.CardExpiry
import com.getbouncer.cardscan.base.domain.CardImage
import com.getbouncer.cardscan.base.domain.CardNumber
import com.getbouncer.cardscan.base.domain.CardOcrResult
import com.getbouncer.cardscan.base.domain.FixedMemorySize
import com.getbouncer.cardscan.base.util.CreditCardUtils

/**
 * A result handler for data processing. This is called when results are available from an
 * [Analyzer].
 */
interface ResultHandler<Input, Output> {
    fun onResult(result: Output, data: Input)
}

interface AggregateResultListener<DataFrame : FixedMemorySize, ModelResult> {

    /**
     * The aggregated result of a model is available.
     *
     * @param result: the card result from the model
     * @param frames: data frames captured during processing that can be used in the completion loop
     */
    fun onResult(result: ModelResult, frames: List<DataFrame>)

    /**
     * An interim result is available, but the model is still processing more data frames. This is
     * useful for displaying a debug window.
     *
     * @param result: the card result from the model
     * @param frame: the data frame that produced this result.
     */
    fun onInterimResult(result: ModelResult, frame: DataFrame)

    /**
     * The processing rate has been updated. This is useful for debugging and measuring performance.
     *
     * @param avgFramesPerSecond: The average frame rate at which the model is running
     * @param instFramesPerSecond: The instantaneous frame rate at which the model is running
     */
    fun onUpdateProcessingRate(avgFramesPerSecond: Double, instFramesPerSecond: Double)
}

/**
 * A result handler which listens until some condition is met, then terminates
 */
abstract class TerminatingResultHandler<Input, Output> : ResultHandler<Input, Output> {
    private var listening = true

    fun isListening(): Boolean = listening

    fun stopListening() {
        listening = false
    }
}

data class ResultAggregatorConfig(
    val maxTotalAggregationTimeNs: Long,
    val maxSavedFrames: Int,
    val frameStorageBytes: Int,
    val trackFrameRate: Boolean,
    val frameRateUpdateIntervalNs: Long
) {

    class Builder {
        companion object {
            private const val DEFAULT_MAX_TOTAL_AGGREGATION_TIME_NS = 1500000000L // 1.5 seconds
            private const val DEFAULT_MAX_OBJECT_DETECTION_FRAMES = -1  // Unlimited saved frames
            private const val DEFAULT_FRAME_STORAGE_BYTES = 0x4000000 // 64MB
            private const val DEFAULT_TRACK_FRAME_RATE = false
            private const val DEFAULT_FRAME_RATE_UPDATE_INTERVAL_NS = 1000000000L // 1 second
        }

        private var maxTotalAggregationTimeNs: Long =
            DEFAULT_MAX_TOTAL_AGGREGATION_TIME_NS
        private var maxSavedFrames: Int =
            DEFAULT_MAX_OBJECT_DETECTION_FRAMES
        private var frameRateUpdateIntervalNs: Long =
            DEFAULT_FRAME_RATE_UPDATE_INTERVAL_NS
        private var frameStorageBytes: Int =
            DEFAULT_FRAME_STORAGE_BYTES
        private var trackFrameRate: Boolean =
            DEFAULT_TRACK_FRAME_RATE

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

        fun withTrackFrameRate(trackFrameRate: Boolean) {
            this.trackFrameRate = trackFrameRate
        }

        fun build() =
            ResultAggregatorConfig(
                maxTotalAggregationTimeNs,
                maxSavedFrames,
                frameStorageBytes,
                trackFrameRate,
                frameRateUpdateIntervalNs
            )
    }
}

abstract class ResultAggregator<DataFrame : FixedMemorySize, ModelResult>(
    private val config: ResultAggregatorConfig,
    private val listener: AggregateResultListener<DataFrame, ModelResult>
) : TerminatingResultHandler<DataFrame, ModelResult>() {

    companion object {
        private const val NANOS_IN_SECONDS = 1000000000
    }

    private var firstResultTimeNs: Long? = null
    private var firstFrameTimeNs: Long? = null
    private var lastNotifyTimeNs: Long? = null
    private var totalFramesProcessed: Long = 0
    private var framesProcessedSinceLastUpdate: Long = 0
    private var savedFramesSizeBytes: Long = 0

    private val savedFrames = mutableListOf<DataFrame>()

    override fun onResult(result: ModelResult, data: DataFrame) {
        if (!isListening()) {
            return
        }

        if (config.trackFrameRate) {
            trackAndNotifyOfFrameRate()
        }

        if (isValidResult(result) && firstResultTimeNs == null) {
            firstResultTimeNs = System.nanoTime()
        }

        if ((config.maxSavedFrames < 0 || savedFrames.size < config.maxSavedFrames)
            && (config.frameStorageBytes < 0 || savedFramesSizeBytes < config.frameStorageBytes)
            && isFrameSuitableForCompletionLoop(result, data)
        ) {
            savedFramesSizeBytes += getFrameSizeBytes(data)
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
     * Determine if a data frame should be saved for future processing
     */
    abstract fun isFrameSuitableForCompletionLoop(result: ModelResult, frame: DataFrame): Boolean

    /**
     * Determine the size in memory that this data frame takes up
     */
    private fun getFrameSizeBytes(frame: DataFrame) = frame.sizeInBytes

    /**
     * Calculate the current rate at which the model is processing images. Notify the listener of
     * the result.
     */
    private fun trackAndNotifyOfFrameRate() {
        val nowNs = System.nanoTime()

        if (this.firstFrameTimeNs == null) {
            this.firstFrameTimeNs = nowNs
        }

        totalFramesProcessed++
        framesProcessedSinceLastUpdate++

        if (shouldNotifyOfFrameRate(nowNs)) {
            val lastNotifyTimeNs = this.lastNotifyTimeNs ?: nowNs
            val firstFrameTimeNs = this.firstFrameTimeNs ?: nowNs

            val instFramesPerSecond =
                calculateFrameRate(framesProcessedSinceLastUpdate, nowNs - lastNotifyTimeNs)

            val avgFramesPerSecond =
                calculateFrameRate(totalFramesProcessed, nowNs - firstFrameTimeNs)

            framesProcessedSinceLastUpdate = 0
            this.lastNotifyTimeNs = nowNs
            notifyOfFrameRate(avgFramesPerSecond, instFramesPerSecond)
        }
    }

    /**
     * Calculate the frame rate in frames / second.
     *
     * @param frames: the number of frames processed
     * @param durationNs: how long these frames were tracked for in nanoseconds
     */
    private fun calculateFrameRate(frames: Long, durationNs: Long): Double =
        if (durationNs > 0) {
            frames.toDouble() * NANOS_IN_SECONDS / durationNs
        } else {
            0.0
        }

    private fun shouldNotifyOfFrameRate(nowNs: Long): Boolean =
        nowNs - (lastNotifyTimeNs ?: 0) > config.frameRateUpdateIntervalNs

    /**
     * Send the listener the current frame rates on the main thread.
     */
    private fun notifyOfFrameRate(
        avgFramesPerSecond: Double,
        instantaneousFramesPerSecond: Double
    ) {
        runOnMainThread(Runnable {
            listener.onUpdateProcessingRate(avgFramesPerSecond, instantaneousFramesPerSecond)
        })
    }

    /**
     * Send the listener the result from this model on the main thread.
     */
    private fun notifyOfResult(result: ModelResult, frames: List<DataFrame>) {
        stopListening()
        runOnMainThread(Runnable { listener.onResult(result, frames) })
    }

    /**
     * Send the listener an interim result from this model on the main thread.
     */
    private fun notifyOfInterimResult(result: ModelResult, frame: DataFrame) {
        runOnMainThread(Runnable { listener.onInterimResult(result, frame) })
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
abstract class CardOcrResultAggregator<ImageFormat : FixedMemorySize>(
    config: ResultAggregatorConfig,
    listener: AggregateResultListener<ImageFormat, CardOcrResult>,
    private val requiredAgreementCount: Int? = null
) : ResultAggregator<ImageFormat, CardOcrResult>(config, listener) {

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
}

class CardImageOcrResultAggregator(
    config: ResultAggregatorConfig,
    listener: AggregateResultListener<CardImage, CardOcrResult>,
    requiredAgreementCount: Int? = null
) : CardOcrResultAggregator<CardImage>(config, listener, requiredAgreementCount) {

    override fun isValidResult(result: CardOcrResult): Boolean =
        CreditCardUtils.isValidCardNumber(result.number?.number)

    // TODO: This should store the least blurry images available
    override fun isFrameSuitableForCompletionLoop(
        result: CardOcrResult,
        frame: CardImage
    ): Boolean = result.number != null
}
