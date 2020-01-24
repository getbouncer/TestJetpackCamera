package com.getbouncer.cardscan.base.ml

import android.util.Log
import com.getbouncer.cardscan.base.domain.CardExpiry
import com.getbouncer.cardscan.base.domain.CardNumber
import com.getbouncer.cardscan.base.domain.ScanImage
import com.getbouncer.cardscan.base.domain.CardOcrResult
import com.getbouncer.cardscan.base.util.CreditCardUtils
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.time.ClockMark
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.MonoClock
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
interface AggregateResultListener<DataFrame, Result> {

    /**
     * The aggregated result of a model is available.
     *
     * @param result: the card result from the model
     * @param frames: data frames captured during processing that can be used in the completion loop
     */
    fun onResult(result: Result, frames: Map<String, List<DataFrame>>)

    /**
     * An interim result is available, but the model is still processing more data frames. This is
     * useful for displaying a debug window.
     *
     * @param result: the card result from the model
     * @param frame: the data frame that produced this result.
     */
    fun onInterimResult(result: Result, frame: DataFrame)

    /**
     * An invalid result was received, but the model is still processing more data frames. This is
     * useful for displaying a debug window
     */
    fun onInvalidResult(result: Result, frame: DataFrame, haveSeenValidResult: Boolean)

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
    val maxSavedFrames: Map<String, Int>,
    val defaultMaxSavedFrames: Int,
    val frameStorageBytes: Map<String, Int>,
    val defaultFrameStorageBytes: Int,
    val trackFrameRate: Boolean,
    val frameRateUpdateInterval: Duration
) {

    class Builder {
        companion object {
            private val DEFAULT_MAX_TOTAL_AGGREGATION_TIME = 1.5.seconds
            private const val DEFAULT_MAX_SAVED_FRAMES = -1  // Unlimited saved frames
            private const val DEFAULT_FRAME_STORAGE_BYTES = 0x4000000 // 64MB
            private const val DEFAULT_TRACK_FRAME_RATE = false
            private val DEFAULT_FRAME_RATE_UPDATE_INTERVAL = 1.seconds
        }

        private var maxTotalAggregationTime: Duration = DEFAULT_MAX_TOTAL_AGGREGATION_TIME
        private var maxSavedFrames: MutableMap<String, Int> = mutableMapOf()
        private var defaultMaxSavedFrames: Int = DEFAULT_MAX_SAVED_FRAMES
        private var frameStorageBytes: MutableMap<String, Int> = mutableMapOf()
        private var defaultFrameStorageBytes: Int = DEFAULT_FRAME_STORAGE_BYTES
        private var trackFrameRate: Boolean = DEFAULT_TRACK_FRAME_RATE
        private var frameRateUpdateInterval: Duration = DEFAULT_FRAME_RATE_UPDATE_INTERVAL

        fun withMaxTotalAggregationTime(maxTotalAggregationTime: Duration): Builder {
            this.maxTotalAggregationTime = maxTotalAggregationTime
            return this
        }

        fun withMaxSavedFrames(maxSavedFrames: Map<String, Int>): Builder {
            this.maxSavedFrames = maxSavedFrames.toMutableMap()
            return this
        }

        fun withMaxSavedFrames(frameType: String, maxSavedFrames: Int): Builder {
            this.maxSavedFrames[frameType] = maxSavedFrames
            return this
        }

        fun withDefaultMaxSavedFrames(defaultMaxSavedFrames: Int): Builder {
            this.defaultMaxSavedFrames = defaultMaxSavedFrames
            return this
        }

        fun withFrameRateUpdateInterval(frameRateUpdateInterval: Duration): Builder {
            this.frameRateUpdateInterval = frameRateUpdateInterval
            return this
        }

        fun withFrameStorageBytes(frameStorageBytes: Map<String, Int>): Builder {
            this.frameStorageBytes = frameStorageBytes.toMutableMap()
            return this
        }

        fun withFrameStorageBytes(frameType: String, frameStorageBytes: Int): Builder {
            this.frameStorageBytes[frameType] = frameStorageBytes
            return this
        }

        fun withDefaultFrameStorageBytes(defaultFrameStorageBytes: Int): Builder {
            this.defaultFrameStorageBytes = defaultFrameStorageBytes
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
                defaultMaxSavedFrames,
                frameStorageBytes,
                defaultFrameStorageBytes,
                trackFrameRate,
                frameRateUpdateInterval
            )
    }
}

@ExperimentalTime
abstract class ResultAggregator<DataFrame, Result>(
    private val config: ResultAggregatorConfig,
    private val listener: AggregateResultListener<DataFrame, Result>
) : FinishingResultHandler<DataFrame, Result>() {

    private var firstResultTime: ClockMark? = null
    private var firstFrameTime: ClockMark? = null
    private var lastNotifyTime: ClockMark = MonoClock.markNow()
    private var totalFramesProcessed: AtomicLong = AtomicLong(0)
    private var framesProcessedSinceLastUpdate: AtomicLong = AtomicLong(0)
    private var haveSeenValidResult: Boolean = false

    private val savedFrames = mutableMapOf<String, LinkedList<DataFrame>>()
    private val savedFramesSizeBytes = mutableMapOf<String, Int>()

    override fun onResult(result: Result, data: DataFrame) {
        if (!isListening()) {
            return
        }

        if (config.trackFrameRate) {
            trackAndNotifyOfFrameRate()
        }

        val validResult = isValidResult(result)
        if (validResult && firstResultTime == null) {
            firstResultTime = MonoClock.markNow()
        }

        saveFrames(result, data)

        if (validResult) {
            haveSeenValidResult = true
            notifyOfInterimResult(result, data)
        } else {
            notifyOfInvalidResult(result, data, haveSeenValidResult)
        }

        val finalResult = aggregateResult(result, hasReachedTimeout())
        if (finalResult != null) {
            notifyOfResult(finalResult, savedFrames)
        }
    }

    @Synchronized
    fun saveFrames(result: Result, data: DataFrame) {
        val savedFrameType = getSaveFrameIdentifier(result, data)
        val typedSavedFrames = savedFrames[savedFrameType] ?: LinkedList()

        if (savedFrameType != null) {
            val maxSavedFrames = config.maxSavedFrames[savedFrameType] ?: config.defaultMaxSavedFrames
            val storageBytes = config.frameStorageBytes[savedFrameType] ?: config.defaultFrameStorageBytes

            var typedSizeBytes = savedFramesSizeBytes[savedFrameType] ?: 0 + getFrameSizeBytes(data)
            while (storageBytes in 0 until typedSizeBytes) {
                // saved frames is over storage limit, reduce until it's not
                if (typedSavedFrames.size > 0) {
                    val removedFrame = typedSavedFrames.removeFirst()
                    typedSizeBytes -= getFrameSizeBytes(removedFrame)
                } else {
                    typedSizeBytes = 0
                }
            }

            while (maxSavedFrames >= 0 && typedSavedFrames.size > maxSavedFrames) {
                // saved frames is over size limit, reduce until it's not
                val removedFrame = typedSavedFrames.removeFirst()
                typedSizeBytes = max(0, typedSizeBytes - getFrameSizeBytes(removedFrame))
            }

            savedFramesSizeBytes[savedFrameType] = typedSizeBytes
            typedSavedFrames.add(data)
            savedFrames[savedFrameType] = typedSavedFrames
        }
    }

    /**
     * Aggregate a new result. Note that the [result] may be invalid.
     *
     * @param result: The result to aggregate
     * @param mustReturn: If true, this method must return a final result
     */
    abstract fun aggregateResult(result: Result, mustReturn: Boolean): Result?

    /**
     * Determine if a result is valid for tracking purposes.
     */
    abstract fun isValidResult(result: Result): Boolean

    /**
     * Determine if a data frame should be saved for future processing. Note that [result] may be
     * invalid.
     *
     * If this method returns a non-null string, the frame will be saved under that identifier.
     */
    abstract fun getSaveFrameIdentifier(result: Result, frame: DataFrame): String?

    /**
     * Determine the size in memory that this data frame takes up
     */
    abstract fun getFrameSizeBytes(frame: DataFrame): Int

    /**
     * Calculate the current rate at which the model is processing images. Notify the listener of
     * the result.
     */
    private fun trackAndNotifyOfFrameRate() {
        val totalFrames = totalFramesProcessed.incrementAndGet()
        val framesSinceLastUpdate = framesProcessedSinceLastUpdate.incrementAndGet()

        if (shouldNotifyOfFrameRate()) {
            val lastNotifyTime = this.lastNotifyTime
            val firstFrameTime = this.firstFrameTime

            if (firstFrameTime != null) {
                val totalFrameRate =
                    Rate(totalFrames, firstFrameTime.elapsedNow())

                val instantFrameRate =
                    Rate(framesSinceLastUpdate, lastNotifyTime.elapsedNow())

                notifyOfFrameRate(totalFrameRate, instantFrameRate)
            }

            framesProcessedSinceLastUpdate.set(0)
            this.lastNotifyTime = MonoClock.markNow()

            if (this.firstFrameTime == null) {
                this.firstFrameTime = MonoClock.markNow()
            }
        }
    }

    private fun shouldNotifyOfFrameRate() =
        lastNotifyTime.elapsedNow() > config.frameRateUpdateInterval

    /**
     * Send the listener the current frame rates on the main thread.
     */
    private fun notifyOfFrameRate(overallRate: Rate, instantRate: Rate) =
        listener.onUpdateProcessingRate(overallRate, instantRate)

    /**
     * Send the listener the result from this model on the main thread.
     */
    private fun notifyOfResult(result: Result, frames: Map<String, List<DataFrame>>) {
        stopListening()
        listener.onResult(result, frames)
    }

    /**
     * Send the listener an interim result from this model on the main thread.
     */
    private fun notifyOfInterimResult(result: Result, frame: DataFrame) = listener.onInterimResult(result, frame)

    /**
     * Send the listener an invalid result from this model on the main thread.
     */
    private fun notifyOfInvalidResult(result: Result, frame: DataFrame, haveSeenValidResult: Boolean) =
        listener.onInvalidResult(result, frame, haveSeenValidResult)

    /**
     * Determine if the timeout from the config has been reached
     */
    private fun hasReachedTimeout(): Boolean {
        val firstResultTime = this.firstResultTime
        return firstResultTime != null &&
                firstResultTime.elapsedNow() > config.maxTotalAggregationTime
    }
}

/**
 * Keep track of the results from the ML models. Count the number of times the ML models send each number as a result,
 * and when the first result is received.
 *
 * The [listener] will be notified of a result once [requiredAgreementCount] matching results are received or the time
 * since the first result exceeds the [ResultAggregatorConfig.maxTotalAggregationTime].
 */
@ExperimentalTime
abstract class CardOcrResultAggregator<ImageFormat>(
    config: ResultAggregatorConfig,
    listener: AggregateResultListener<ImageFormat, CardOcrResult>,
    private val requiredAgreementCount: Int? = null
) : ResultAggregator<ImageFormat, CardOcrResult>(config, listener) {

    private val numberResults = mutableMapOf<CardNumber, Int>()
    private val expiryResults = mutableMapOf<CardExpiry, Int>()

    override fun aggregateResult(result: CardOcrResult, mustReturn: Boolean): CardOcrResult? {
        val numberCount = if (isValidResult(result)) {
            storeExpiry(result.expiry)
            storeNumber(result.number)
        } else 0

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

/**
 * Identify valid cards to be those with valid numbers. If a [requiredCardNumber] is provided, only matching cards are
 * considered valid.
 *
 * The [listener] will be notified of a result once [requiredAgreementCount] matching results are received or the time
 * since the first result exceeds the [ResultAggregatorConfig.maxTotalAggregationTime].
 */
@ExperimentalTime
class CardImageOcrResultAggregator(
    config: ResultAggregatorConfig,
    listener: AggregateResultListener<ScanImage, CardOcrResult>,
    requiredAgreementCount: Int? = null,
    private val requiredCardNumber: String? = null
) : CardOcrResultAggregator<ScanImage>(config, listener, requiredAgreementCount) {

    companion object {
        const val FRAME_TYPE_VALID_NUMBER = "valid_number"
        const val FRAME_TYPE_INVALID_NUMBER = "invalid_number"
    }

    init {
        assert(requiredCardNumber == null || CreditCardUtils.isValidCardNumber(requiredCardNumber)) {
            "Invalid required credit card supplied"
        }
    }

    override fun isValidResult(result: CardOcrResult): Boolean =
        if (requiredCardNumber != null) {
            requiredCardNumber == result.number?.number
        } else {
            CreditCardUtils.isValidCardNumber(result.number?.number)
        }

    override fun getFrameSizeBytes(frame: ScanImage): Int = frame.sizeInBytes

    // TODO: This should store the least blurry images available
    override fun getSaveFrameIdentifier(result: CardOcrResult, frame: ScanImage): String? =
        if (isValidResult(result)) {
            FRAME_TYPE_VALID_NUMBER
        } else {
            FRAME_TYPE_INVALID_NUMBER
        }
}
