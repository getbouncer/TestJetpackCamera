package com.getbouncer.cardscan.base.ml

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private const val DEFAULT_COROUTINE_COUNT = 10

/**
 * An analyzer takes some data as an input, and returns an analyzed output. Analyzers should not
 * contain any state. They must define whether they can run on a multithreaded executor, and provide
 * a means of analyzing input data to return some form of result.
 */
interface Analyzer<Input, Output> {
    fun analyze(data: Input): Output
}

/**
 * A factory to create analyzers that will be run as part of the loops
 */
interface AnalyzerFactory<Output : Analyzer<*, *>> {
    val isThreadSafe: Boolean

    fun newInstance(): Output
}

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
abstract class AnalyzerLoop<DataFrame, Output>(
    private val analyzerFactory: AnalyzerFactory<out Analyzer<DataFrame, Output>>,
    private val resultHandler: ResultHandler<DataFrame, Output>,
    private val coroutineScope: CoroutineScope,
    private val coroutineCount: Int
) {

    private val started: AtomicBoolean = AtomicBoolean(false)
    private val frames: Channel<DataFrame> = Channel(calculateChannelBufferSize())

    private fun calculateChannelBufferSize(): Int = Channel.RENDEZVOUS //if (analyzerFactory.isThreadSafe) 5 else Channel.RENDEZVOUS

    @ExperimentalCoroutinesApi
    fun enqueueFrame(frame: DataFrame) = if (!frames.isClosedForSend) frames.offer(frame) else false

    @ExperimentalCoroutinesApi
    fun hasMoreFrames() = !frames.isEmpty

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
        for (frame in frames) {
            resultHandler.onResult(analyzer.analyze(frame), frame)
            if (isFinished()) {
                frames.close()
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
class MemoryBoundAnalyzerLoop<DataFrame, Output>(
    analyzerFactory: AnalyzerFactory<out Analyzer<DataFrame, Output>>,
    private val resultHandler: FinishingResultHandler<DataFrame, Output>,
    coroutineScope: CoroutineScope,
    coroutineCount: Int = DEFAULT_COROUTINE_COUNT
) : AnalyzerLoop<DataFrame, Output>(analyzerFactory, resultHandler, coroutineScope, coroutineCount) {

    override fun isFinished(): Boolean = !resultHandler.isListening()
}

/**
 * This kind of [AnalyzerLoop] will process data provided as part of its constructor. Data will be
 * processed in the order provided.
 */
@ExperimentalCoroutinesApi
class FiniteAnalyzerLoop<DataFrame, Output>(
    frames: Collection<DataFrame>,
    analyzerFactory: AnalyzerFactory<out Analyzer<DataFrame, Output>>,
    resultHandler: ResultHandler<DataFrame, Output>,
    coroutineScope: CoroutineScope,
    coroutineCount: Int = DEFAULT_COROUTINE_COUNT
) : AnalyzerLoop<DataFrame, Output>(analyzerFactory, resultHandler, coroutineScope, coroutineCount) {

    init {
        frames.forEach { enqueueFrame(it) }
    }

    override fun isFinished(): Boolean = !hasMoreFrames()
}
