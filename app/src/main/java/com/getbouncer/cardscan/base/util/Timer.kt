package com.getbouncer.cardscan.base.util

import android.util.Log
import android.util.TimingLogger
import com.getbouncer.cardscan.base.config.MEASURE_TIME
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.MonoClock
import kotlin.time.measureTime
import kotlin.time.seconds

@ExperimentalTime
sealed class Timer {

    companion object {
        fun newInstance(
            tag: String,
            name: String,
            updateInterval: Duration = 2.seconds,
            enabled: Boolean = MEASURE_TIME
        ) = if (enabled) {
            LoggingTimer(tag, name, updateInterval)
        } else {
            NoOpTimer
        }
    }

    abstract fun <T> measure(task: () -> T): T
}

@ExperimentalTime
private object NoOpTimer : Timer() {
    override fun <T> measure(task: () -> T): T = task()
}

@ExperimentalTime
private class LoggingTimer(
    private val tag: String,
    private val name: String,
    private val updateInterval: Duration
) : Timer() {
    private var executionCount = 0
    private var exectionTotalDuration = Duration.ZERO
    private var updateClock = MonoClock.markNow()

    override fun <T> measure(task: () -> T): T {
        val result: T
        val duration = measureTime {
            result = task()
        }

        executionCount++
        exectionTotalDuration += duration

        if (updateClock.elapsedNow() > updateInterval) {
            updateClock = MonoClock.markNow()
            Log.d(tag,
                "$name EXECUTING AT ${executionCount / exectionTotalDuration.inSeconds} FPS, " +
                        "${exectionTotalDuration.inMilliseconds / executionCount} MS/F"
            )
        }
        return result
    }
}