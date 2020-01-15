package com.getbouncer.cardscan.base.ml

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

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
    private val analyzer: Analyzer<DataFrame, Output>,
    private val resultHandler: ResultHandler<DataFrame, Output>,
    private val coroutineCount: Int = DEFAULT_COROUTINE_COUNT
) : CoroutineScope by CoroutineScope(Dispatchers.Default) {

    companion object {
        private const val DEFAULT_COROUTINE_COUNT = 1000
    }

    private val started: AtomicBoolean = AtomicBoolean(false)
    private val frames: Channel<DataFrame> = Channel(5)

    @ExperimentalCoroutinesApi
    fun enqueueFrame(frame: DataFrame) = if (!frames.isClosedForSend) frames.offer(frame) else false

    @ExperimentalCoroutinesApi
    fun hasMoreFrames() = !frames.isEmpty

    fun start() {
        if (started.getAndSet(true)) {
            return
        }

        if (analyzer.isThreadSafe) {
            repeat(coroutineCount) {
                startWorker()
            }
        } else {
            startWorker()
        }
    }

    private fun startWorker() =
        launch {
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
    analyzer: Analyzer<DataFrame, Output>,
    private val resultHandler: FinishingResultHandler<DataFrame, Output>
) : AnalyzerLoop<DataFrame, Output>(analyzer, resultHandler) {

    override fun isFinished(): Boolean = !resultHandler.isListening()
}

/**
 * This kind of [AnalyzerLoop] will process data provided as part of its constructor. Data will be
 * processed in the order provided.
 */
@ExperimentalCoroutinesApi
class FiniteAnalyzerLoop<DataFrame, Output>(
    frames: Collection<DataFrame>,
    analyzer: Analyzer<DataFrame, Output>,
    resultHandler: ResultHandler<DataFrame, Output>
) : AnalyzerLoop<DataFrame, Output>(analyzer, resultHandler) {

    init {
        frames.forEach { enqueueFrame(it) }
    }

    override fun isFinished(): Boolean = !hasMoreFrames()
}
