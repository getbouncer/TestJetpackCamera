package com.getbouncer.cardscan.base

import org.tensorflow.lite.Interpreter
import java.io.FileNotFoundException
import java.net.URL
import java.nio.ByteBuffer

/**
 * A factory to create analyzers.
 */
interface AnalyzerFactory<Output : Analyzer<*, *>> {
    val isThreadSafe: Boolean

    fun newInstance(): Output?
}

/**
 * A factory that creates tensorflow models as analyzers.
 */
sealed class TFLAnalyzerFactory<Output : Analyzer<*, *>> :
    AnalyzerFactory<Output> {
    abstract val tfOptions: Interpreter.Options

    abstract fun loadModel(): ByteBuffer

    internal fun createInterpreter() = try {
        Interpreter(loadModel(), tfOptions)
    } catch (t: Throwable) {
        null
    }
}

/**
 * A TensorFlowLite resource model is a TF model that is defined by a local resource within the android package. In this
 * case, models are stored in `res/raw`.
 */
abstract class TFLResourceAnalyzerFactory<Output : Analyzer<*, *>>(private val loader: ResourceLoader)
    : TFLAnalyzerFactory<Output>() {

    abstract val modelFileResource: Int

    override fun loadModel() = loader.loadModelFromResource(modelFileResource)
}

/**
 * A TensorFlowLite downloaded model is a TF model that is downloaded from a URL specified in the factory.
 */
abstract class TFLWebAnalyzerFactory<Output : Analyzer<*, *>>(private val loader: WebLoader)
    : TFLAnalyzerFactory<Output>() {

    abstract val url: URL

    abstract val hash: String

    /**
     * Pre-download the model from the web to speed up processing time later.
     */
    @Throws(FileNotFoundException::class)
    fun warmUp() = createInterpreter() != null

    private val localFileName: String by lazy { url.path.replace('/', '_') }

    override fun loadModel() = loader.loadModelFromWeb(url, localFileName, hash)
}
