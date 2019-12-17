package com.getbouncer.cardscan.base.ml

import java.util.concurrent.Executor
import java.util.concurrent.Executors

private val singleThreadExecutor: Executor by lazy { Executors.newSingleThreadExecutor() }
private val multiThreadExecutor: Executor by lazy { Executors.newCachedThreadPool() }

/**
 * This factory assigns an [Executor] based on an [MLModel]. If the model supports multithreading,
 * the factory returns a multi-threaded executor. If not, it returns a single-threaded executor.
 */
class MLExecutorFactory(private val model: MLModel<out Any, out Any>) {
    fun getExecutor(): Executor = if (model.supportsMultiThreading) {
        multiThreadExecutor
    } else {
        singleThreadExecutor
    }
}
