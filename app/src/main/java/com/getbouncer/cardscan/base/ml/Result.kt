package com.getbouncer.cardscan.base.ml

import android.os.Handler
import android.os.Looper
import com.getbouncer.cardscan.base.domain.CardExpiry
import com.getbouncer.cardscan.base.domain.CardImage
import com.getbouncer.cardscan.base.domain.CardNumber
import com.getbouncer.cardscan.base.domain.CardOcrResult
import com.getbouncer.cardscan.base.domain.FixedMemorySize
import com.getbouncer.cardscan.base.util.CreditCardUtils
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.nanoseconds
import kotlin.time.seconds

/**
 * A result handler for data processing. This is called when results are available from an
 * [Analyzer].
 */
interface ResultHandler<Input, Output> {
    fun onResult(result: Output, data: Input)
}

@ExperimentalTime
data class Rate(val amount: Long, val duration: Duration)

@ExperimentalTime
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
     * @param overallRate: The total frame rate at which the analyzer is running
     * @param instantRate: The instantaneous frame rate at which the analyzer is running
     */
    fun onUpdateProcessingRate(overallRate: Rate, instantRate: Rate)
}

/**
 * A result handler which listens until some condition is met, then terminates
 */
abstract class FinishingResultHandler<Input, Output> : ResultHandler<Input, Output> {
    private var listening = true

    fun isListening(): Boolean = listening

    fun stopListening() {
        listening = false
    }
}

@ExperimentalTime
data class ResultAggregatorConfig(
    val maxTotalAggregationTime: Duration,
    val maxSavedFrames: Int,
    val frameStorageBytes: Int,
    val trackFrameRate: Boolean,
    val frameRateUpdateInterval: Duration
) {

    class Builder {
        companion object {
            private val DEFAULT_MAX_TOTAL_AGGREGATION_TIME = 1.5.seconds
            private const val DEFAULT_MAX_OBJECT_DETECTION_FRAMES = -1  // Unlimited saved frames
            private const val DEFAULT_FRAME_STORAGE_BYTES = 0x4000000 // 64MB
            private const val DEFAULT_TRACK_FRAME_RATE = false
            private val DEFAULT_FRAME_RATE_UPDATE_INTERVAL = 1.seconds
        }

        private var maxTotalAggregationTime: Duration =
            DEFAULT_MAX_TOTAL_AGGREGATION_TIME
        private var maxSavedFrames: Int =
            DEFAULT_MAX_OBJECT_DETECTION_FRAMES
        private var frameStorageBytes: Int =
            DEFAULT_FRAME_STORAGE_BYTES
        private var trackFrameRate: Boolean =
            DEFAULT_TRACK_FRAME_RATE
        private var frameRateUpdateInterval: Duration =
            DEFAULT_FRAME_RATE_UPDATE_INTERVAL

        fun withMaxTotalAggregationTime(maxTotalAggregationTime: Duration): Builder {
            this.maxTotalAggregationTime = maxTotalAggregationTime
            return this
        }

        fun withMaxSavedFrames(maxSavedFrames: Int): Builder {
            this.maxSavedFrames = maxSavedFrames
            return this
        }

        fun withFrameRateUpdateInterval(frameRateUpdateInterval: Duration): Builder {
            this.frameRateUpdateInterval = frameRateUpdateInterval
            return this
        }

        fun withFrameStorageBytes(frameStorageBytes: Int): Builder {
            this.frameStorageBytes = frameStorageBytes
            return this
        }

        fun withTrackFrameRate(trackFrameRate: Boolean): Builder {
            this.trackFrameRate = trackFrameRate
            return this
        }

        fun build() =
            ResultAggregatorConfig(
                maxTotalAggregationTime,
                maxSavedFrames,
                frameStorageBytes,
                trackFrameRate,
                frameRateUpdateInterval
            )
    }
}

@ExperimentalTime
abstract class ResultAggregator<DataFrame : FixedMemorySize, ModelResult>(
    private val config: ResultAggregatorConfig,
    private val listener: AggregateResultListener<DataFrame, ModelResult>
) : FinishingResultHandler<DataFrame, ModelResult>() {

    private var firstResultTime: Duration? = null
    private var firstFrameTime: Duration? = null
    private var lastNotifyTime: Duration? = null
    private var totalFramesProcessed: AtomicLong = AtomicLong(0)
    private var framesProcessedSinceLastUpdate: AtomicLong = AtomicLong(0)
    private var savedFramesSizeBytes: Long = 0

    private val savedFrames = mutableListOf<DataFrame>()

    override fun onResult(result: ModelResult, data: DataFrame) {
        if (!isListening()) {
            return
        }

        if (config.trackFrameRate) {
            trackAndNotifyOfFrameRate()
        }

        val validResult = isValidResult(result)
        if (validResult && firstResultTime == null) {
            firstResultTime = System.nanoTime().nanoseconds
        }

        if ((config.maxSavedFrames < 0 || savedFrames.size < config.maxSavedFrames)
            && (config.frameStorageBytes < 0 || savedFramesSizeBytes < config.frameStorageBytes)
            && shouldSaveFrame(result, data)
        ) {
            savedFramesSizeBytes += getFrameSizeBytes(data)
            savedFrames.add(data)
        }

        if (validResult) {
            notifyOfInterimResult(result, data)
        }

        val finalResult = aggregateResult(result, hasReachedTimeout())
        if (finalResult != null) {
            notifyOfResult(finalResult, savedFrames)
        }
    }

    /**
     * Aggregate a new result. Note that the [result] may be invalid.
     *
     * @param result: The result to aggregate
     * @param mustReturn: If true, this method must return a final result
     */
    abstract fun aggregateResult(result: ModelResult, mustReturn: Boolean): ModelResult?

    /**
     * Determine if a result is valid for tracking purposes.
     */
    abstract fun isValidResult(result: ModelResult): Boolean

    /**
     * Determine if a data frame should be saved for future processing. Note that [result] may be
     * invalid.
     */
    abstract fun shouldSaveFrame(result: ModelResult, frame: DataFrame): Boolean

    /**
     * Determine the size in memory that this data frame takes up
     */
    private fun getFrameSizeBytes(frame: DataFrame) = frame.sizeInBytes

    /**
     * Calculate the current rate at which the model is processing images. Notify the listener of
     * the result.
     */
    private fun trackAndNotifyOfFrameRate() {
        val now = System.nanoTime().nanoseconds

        val totalFrames = totalFramesProcessed.incrementAndGet()
        val framesSinceLastUpdate = framesProcessedSinceLastUpdate.incrementAndGet()

        if (shouldNotifyOfFrameRate(now)) {
            val lastNotifyTime = this.lastNotifyTime
            val firstFrameTime = this.firstFrameTime

            if (lastNotifyTime != null && firstFrameTime != null) {
                val totalFrameRate =
                    Rate(totalFrames, now - firstFrameTime)

                val instantFrameRate =
                    Rate(framesSinceLastUpdate, now - lastNotifyTime)

                notifyOfFrameRate(totalFrameRate, instantFrameRate)
            }

            framesProcessedSinceLastUpdate.set(0)
            this.lastNotifyTime = now

            if (this.firstFrameTime == null) {
                this.firstFrameTime = now
            }
        }
    }

    private fun shouldNotifyOfFrameRate(now: Duration) =
        now - (lastNotifyTime ?: Duration.ZERO) > config.frameRateUpdateInterval

    /**
     * Send the listener the current frame rates on the main thread.
     */
    private fun notifyOfFrameRate(overallRate: Rate, instantRate: Rate) {
        runOnMainThread(Runnable {
            listener.onUpdateProcessingRate(overallRate, instantRate)
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
        val firstResultTime = this.firstResultTime
        return firstResultTime != null &&
                System.nanoTime().nanoseconds - firstResultTime > config.maxTotalAggregationTime
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
@ExperimentalTime
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

@ExperimentalTime
class CardImageOcrResultAggregator(
    config: ResultAggregatorConfig,
    listener: AggregateResultListener<CardImage, CardOcrResult>,
    requiredAgreementCount: Int? = null
) : CardOcrResultAggregator<CardImage>(config, listener, requiredAgreementCount) {

    override fun isValidResult(result: CardOcrResult): Boolean =
        CreditCardUtils.isValidCardNumber(result.number?.number)

    // TODO: This should store the least blurry images available
    override fun shouldSaveFrame(
        result: CardOcrResult,
        frame: CardImage
    ): Boolean = isValidResult(result)
}
