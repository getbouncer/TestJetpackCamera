package com.getbouncer.cardscan.base

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.ClockMark
import kotlin.time.ExperimentalTime
import kotlin.time.MonoClock
import kotlin.time.milliseconds

/**
 * The default number of analyzers to run in parallel.
 */
private const val DEFAULT_ANALYZER_PARALLEL_COUNT = 10

/**
 * This indicates the expected amount of time taken to process a single image. This, in combination with the number of
 * analyzers running in parallel, dictates how long to delay between accepting frames on the queue for the
 * [MemoryBoundAnalyzerLoop].
 */
@ExperimentalTime
private val EXPECTED_MAIN_LOOP_ITERATION_TIME = 500.milliseconds

/**
 * A loop to execute repeated analysis. The loop uses coroutines to run the [Analyzer.analyze]
 * method. If the [Analyzer] is threadsafe, multiple coroutines will be used. If not, a single
 * coroutine will be used.
 *
 * Any data enqueued while the analyzers are at capacity will be dropped.
 *
 * This will process data until [isFinished] returns false.
 *
 * Note: an analyzer loop can only be started once. Once it terminates, it cannot be restarted.
 */
sealed class AnalyzerLoop<DataFrame, Output>(
    private val analyzerFactory: AnalyzerFactory<out Analyzer<DataFrame, Output>>,
    private val coroutineScope: CoroutineScope,
    internal val coroutineCount: Int
) : ResultHandler<DataFrame, Output> {
    private val started = AtomicBoolean(false)
    private val channel by lazy { Channel<DataFrame>(calculateChannelBufferSize()) }

    abstract fun calculateChannelBufferSize(): Int

    fun enqueueFrame(frame: DataFrame) = if (shouldReceiveNewFrame()) { channel.offer(frame) } else false

    abstract fun shouldReceiveNewFrame(): Boolean

    fun start() {
        if (started.getAndSet(true)) {
            return
        }

        if (analyzerFactory.isThreadSafe) {
            repeat(coroutineCount) {
                startWorker()
            }
        } else {
            startWorker()
        }
    }

    /**
     * Launch a worker coroutine that has access to the analyzer's `analyze` method and the result handler
     */
    private fun startWorker() = coroutineScope.launch {
        val analyzer = analyzerFactory.newInstance()
        for (frame in channel) {
            onResult(analyzer.analyze(frame), frame)
            if (isFinished() && coroutineContext.isActive) {
                coroutineContext.cancel()
            }
        }
    }

    abstract fun isFinished(): Boolean
}

/**
 * This kind of [AnalyzerLoop] will process data until the result handler indicates that it has
 * reached a terminal state and is no longer listening.
 *
 * Data can be added to a queue for processing by a camera or other producer. It will be consumed by
 * FILO. If no data is available, the analyzer pauses until data becomes available.
 *
 * If the enqueued data exceeds the allowed memory size, the bottom of the data stack will be
 * dropped and will not be processed. This alleviates memory pressure when producers are faster than
 * the consuming analyzer.
 *
 * Any data enqueued via [enqueueFrame] will be dropped once this loop has terminated.
 */
@ExperimentalTime
class MemoryBoundAnalyzerLoop<DataFrame, Output>(
    analyzerFactory: AnalyzerFactory<out Analyzer<DataFrame, Output>>,
    private val resultHandler: FinishingResultHandler<DataFrame, Output>,
    coroutineScope: CoroutineScope,
    coroutineCount: Int = DEFAULT_ANALYZER_PARALLEL_COUNT
) : AnalyzerLoop<DataFrame, Output>(analyzerFactory, coroutineScope, coroutineCount) {

    private var lastFrameReceivedAt: ClockMark? = null

    override fun shouldReceiveNewFrame(): Boolean {
        val lastFrameReceivedAt = this.lastFrameReceivedAt
        val shouldReceiveNewFrame = !isFinished() && (
            lastFrameReceivedAt == null ||
            lastFrameReceivedAt.elapsedNow() > EXPECTED_MAIN_LOOP_ITERATION_TIME / coroutineCount
        )
        if (shouldReceiveNewFrame) {
            this.lastFrameReceivedAt = MonoClock.markNow()
        }
        return shouldReceiveNewFrame
    }

    override fun calculateChannelBufferSize(): Int = Channel.RENDEZVOUS

    override fun isFinished(): Boolean = !resultHandler.isListening()

    override fun onResult(result: Output, data: DataFrame) = resultHandler.onResult(result, data)
}

/**
 * This kind of [AnalyzerLoop] will process data provided as part of its constructor. Data will be
 * processed in the order provided.
 */
class FiniteAnalyzerLoop<DataFrame, Output>(
    private val frames: Collection<DataFrame>,
    analyzerFactory: AnalyzerFactory<out Analyzer<DataFrame, Output>>,
    private val resultHandler: ResultHandler<DataFrame, Output>,
    coroutineScope: CoroutineScope,
    coroutineCount: Int = DEFAULT_ANALYZER_PARALLEL_COUNT
) : AnalyzerLoop<DataFrame, Output>(analyzerFactory, coroutineScope, coroutineCount) {

    private val framesProcessed: AtomicInteger = AtomicInteger(0)

    init { frames.forEach { enqueueFrame(it) } }

    override fun calculateChannelBufferSize(): Int = frames.size

    override fun shouldReceiveNewFrame(): Boolean = true

    override fun onResult(result: Output, data: DataFrame) {
        framesProcessed.incrementAndGet()
        resultHandler.onResult(result, data)
    }

    override fun isFinished(): Boolean = framesProcessed.get() >= frames.size
}
