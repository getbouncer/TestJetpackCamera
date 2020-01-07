package com.getbouncer.cardscan.base.ml

import com.getbouncer.cardscan.base.domain.FixedMemorySize
import com.getbouncer.cardscan.base.util.MessageBus
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private val singleThreadExecutor: Executor by lazy { Executors.newSingleThreadExecutor() }
private val multiThreadExecutor: Executor by lazy { Executors.newCachedThreadPool() }

/**
 * An analyzer takes some data as an input, and returns an analyzed output. Analyzers should not
 * contain any state. They must define whether they can run on a multithreaded executor, and provide
 * a means of analyzing input data to return some form of result.
 */
interface Analyzer<Input, Output> {
    val isThreadSafe: Boolean

    fun analyze(data: Input): Output
}

/**
 * A loop to execute repeated analysis. The loop uses an [Executor] to run the [Analyzer.analyze]
 * method. If the [Analyzer] is threadsafe, a cached thread pool executor will be used. If not, a
 * single threaded executor will be used.
 *
 * This will process data until [shouldContinueProcessing] returns false.
 *
 * Note: an analyzer loop can only be started once. Once it terminates, it cannot be restarted.
 */
abstract class AnalyzerLoop<DataFrame, Output>(
    private val analyzer: Analyzer<DataFrame, Output>,
    private val resultHandler: ResultHandler<DataFrame, Output>
) : Runnable {

    private var started: Boolean = false
    private var finished: Boolean = false

    private val analyzerExecutor by lazy {
        if (analyzer.isThreadSafe) multiThreadExecutor else singleThreadExecutor
    }

    val executor: Executor by lazy { Executors.newCachedThreadPool() }

    /**
     * True if more frames are available for processing.
     */
    internal abstract fun hasNextFrame(): Boolean

    /**
     * Get the next frame for processing. Not guaranteed to be non-null due to concurrency.
     */
    internal abstract fun getNextFrame(): DataFrame?

    /**
     * True if the loop should continue processing data frames. False will cause the loop to
     * terminate.
     */
    internal abstract fun shouldContinueProcessing(): Boolean

    /**
     * Determine if the loop has been started.
     */
    fun isStarted() = started

    /**
     * Determine if the loop has finished.
     */
    fun isFinished() = finished

    /**
     * A helper method to start this loop. If no executor is provided, this will use a default
     * multi-threaded, cached executor.
     */
    fun runLoop(executor: Executor = this.executor) {
        executor.execute(this)
    }

    /**
     * Execute the loop on the calling thread. This will spawn additional threads using either a
     * single threaded executor or multi threaded executor depending on what the analyzer supports.
     */
    override fun run() {
        synchronized(this) {
            if (started) {
                return
            }
            started = true
        }

        while (shouldContinueProcessing()) {
            // TODO: this should block the thread if no next frame is available to process
            if (hasNextFrame()) {
                analyzerExecutor.execute {
                    processNextDataFrame()
                }
            }
        }

        finished = true
    }

    private fun processNextDataFrame() {
        val image = getNextFrame()
        if (image != null) {
            resultHandler.onResult(analyzer.analyze(image), image)
        }
    }
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
class MemoryBoundAnalyzerLoop<DataFrame : FixedMemorySize, Output>(
    maximumFrameMemoryBytes: Int,
    analyzer: Analyzer<DataFrame, Output>,
    private val resultHandler: FinishingResultHandler<DataFrame, Output>
) : AnalyzerLoop<DataFrame, Output>(analyzer, resultHandler) {

    private val messageBus by lazy { MessageBus<DataFrame>(maximumFrameMemoryBytes) }

    fun enqueueFrame(frame: DataFrame) {
        if (!isFinished() && resultHandler.isListening()) {
            messageBus.publish(frame)
        }
    }

    override fun hasNextFrame(): Boolean = messageBus.notEmpty()

    override fun getNextFrame(): DataFrame? = messageBus.popMessage()

    override fun shouldContinueProcessing(): Boolean = resultHandler.isListening()
}

/**
 * This kind of [AnalyzerLoop] will process data provided as part of its constructor. Data will be
 * processed in the order provided.
 */
class FiniteAnalyzerLoop<DataFrame : FixedMemorySize, Output>(
    frames: Collection<DataFrame>,
    analyzer: Analyzer<DataFrame, Output>,
    resultHandler: ResultHandler<DataFrame, Output>
) : AnalyzerLoop<DataFrame, Output>(analyzer, resultHandler) {

    private val iterator = frames.iterator()

    override fun hasNextFrame(): Boolean = iterator.hasNext()

    override fun getNextFrame(): DataFrame? = if (hasNextFrame()) iterator.next() else null

    override fun shouldContinueProcessing(): Boolean = hasNextFrame()
}
