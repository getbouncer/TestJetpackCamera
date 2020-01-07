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
 * A loop to execute image analysis
 */
abstract class ImageAnalyzerLoop<ImageFormat, Output>(
    private val analyzer: Analyzer<ImageFormat, Output>,
    private val resultHandler: ResultHandler<ImageFormat, Output>
) : Runnable {

    private var running: Boolean = false

    private val analyzerExecutor by lazy {
        if (analyzer.isThreadSafe) multiThreadExecutor else singleThreadExecutor
    }

    val executor: Executor by lazy { Executors.newCachedThreadPool() }

    internal abstract fun getNextFrame(): ImageFormat?

    internal abstract fun shouldContinueProcessing(): Boolean

    fun isRunning() = running

    fun runLoop(executor: Executor = this.executor) {
        executor.execute(this)
    }

    override fun run() {
        synchronized(this) {
            if (running) {
                return
            }
            running = true
        }

        while (shouldContinueProcessing()) {
            analyzerExecutor.execute {
                processNextImage()
            }
        }

        running = false
    }

    private fun processNextImage() {
        val image = getNextFrame()
        if (image != null) {
            resultHandler.onResult(analyzer.analyze(image), image)
        }
    }
}

class TerminatingImageAnalyzerLoop<ImageFormat : FixedMemorySize, Output>(
    maximumFrameMemoryBytes: Int,
    analyzer: Analyzer<ImageFormat, Output>,
    private val resultHandler: TerminatingResultHandler<ImageFormat, Output>
) : ImageAnalyzerLoop<ImageFormat, Output>(analyzer, resultHandler) {

    private val messageBus by lazy { MessageBus<ImageFormat>(maximumFrameMemoryBytes) }

    fun enqueueFrame(frame: ImageFormat) {
        if (resultHandler.isListening()) {
            messageBus.publish(frame)
        }
    }

    override fun getNextFrame(): ImageFormat? = messageBus.popMessage()

    override fun shouldContinueProcessing(): Boolean = resultHandler.isListening()
}

class BoundImageAnalyzerLoop<ImageFormat : FixedMemorySize, Output>(
    frames: Collection<ImageFormat>,
    analyzer: Analyzer<ImageFormat, Output>,
    resultHandler: ResultHandler<ImageFormat, Output>
) : ImageAnalyzerLoop<ImageFormat, Output>(analyzer, resultHandler) {

    private val iterator = frames.iterator()

    override fun getNextFrame(): ImageFormat? = if (iterator.hasNext()) iterator.next() else null

    override fun shouldContinueProcessing(): Boolean = iterator.hasNext()
}
