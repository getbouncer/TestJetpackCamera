package com.getbouncer.cardscan.base.ml

import android.util.Size
import com.getbouncer.cardscan.base.util.Timer
import org.tensorflow.lite.Interpreter
import kotlin.time.ExperimentalTime

/**
 * An analyzer takes some data as an input, and returns an analyzed output. Analyzers should not contain any state. They
 * must define whether they can run on a multithreaded executor, and provide a means of analyzing input data to return
 * some form of result.
 */
interface Analyzer<Input, Output> {
    fun analyze(data: Input): Output
}

/**
 * A TensorFlowLite analyzer uses an [Interpreter] to analyze data.
 */
@ExperimentalTime
abstract class MLTensorFlowLiteAnalyzer<Input, MLInput, Output, MLOutput>(
    private val tfInterpreter: Interpreter
) : Analyzer<Input, Output> {

    abstract val logTag: String

    abstract val trainedImageSize: Size

    abstract fun buildEmptyMLOutput(): MLOutput

    abstract fun interpretMLOutput(data: Input, mlOutput: MLOutput): Output

    private val loggingTransformTimer by lazy { Timer.newInstance(logTag, "transform ${this::class.java.simpleName}") }
    private val loggingMLOutputTimer by lazy { Timer.newInstance(logTag, "ml_output ${this::class.java.simpleName}") }
    private val loggingInferenceTimer by lazy { Timer.newInstance(logTag, "infer ${this::class.java.simpleName}") }
    private val loggingInterpretTimer by lazy { Timer.newInstance(logTag, "interpret ${this::class.java.simpleName}") }

    abstract fun transformData(data: Input): MLInput

    abstract fun executeInference(tfInterpreter: Interpreter, data: MLInput, mlOutput: MLOutput)

    override fun analyze(data: Input): Output {
        val mlInput = loggingTransformTimer.measure {
            transformData(data)
        }

        val mlOutput = loggingMLOutputTimer.measure {
            buildEmptyMLOutput()
        }

        loggingInferenceTimer.measure {
            executeInference(tfInterpreter, mlInput, mlOutput)
        }

        return loggingInterpretTimer.measure {
            interpretMLOutput(data, mlOutput)
        }
    }

    fun close() = tfInterpreter.close()
}
