package com.getbouncer.cardscan.base.ml

import android.content.Context
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * A result handler for data processing. This is called when results are available from an
 * [MLImageAnalyzerModel].
 */
interface ResultHandler<T, U> {
    fun onResult(result: U, data: T)
}

/**
 * The basic concept of an ML model. Models should not contain any state. They must define whether
 * they can run on a multithreaded executor, and provide a means of analyzing input data to return
 * some form of result.
 */
interface MLModel<T, U> {
    val supportsMultiThreading: Boolean

    fun analyze(data: T): U
}

/**
 * This class is an adaption of the [MLModel] to work with the Android CameraX APIs. Since those
 * APIs require a analyzer with a callback, this adapts the ML model to use a [ResultHandler] as a
 * callback for its results.
 */
abstract class MLImageAnalyzerModel<T, U>(private val resultHandler: ResultHandler<T, U>)
    : ImageAnalysis.Analyzer, MLModel<T, U> {
    override fun analyze(image: ImageProxy, rotationDegrees: Int) {
        val data = convertImageData(image, rotationDegrees)
        resultHandler.onResult(analyze(data), data)
    }

    /**
     * Convert the Android CameraX [ImageProxy] to something this model can work with.
     */
    abstract fun convertImageData(image: ImageProxy, rotationDegrees: Int): T
}

/**
 * A TensorFlowLite model defines a [MappedByteBuffer] and [Interpreter.Options] to construct a TF
 * interpreter. This interpreter can be used in the model's [analyze] method.
 */
abstract class MLTensorFlowLiteModel<T, U> : MLModel<T, U> {

    abstract val trainedImageSize: Size

    abstract val tfModel: MappedByteBuffer

    abstract val tfOptions: Interpreter.Options

    val tfInterpreter: Interpreter by lazy { Interpreter(tfModel, tfOptions) }
}

/**
 * A TensorFlowLite resource model is a TF model that is defined by a local resource within the
 * android package. In this case, models are stored in `res/raw`.
 */
abstract class MLTFLResourceModel<T, U>(private val factory: MLResourceModelFactory)
    : MLTensorFlowLiteModel<T, U>() {

    abstract val modelFileResource: Int

    override val tfModel: MappedByteBuffer = loadModelFile()

    @Throws(IOException::class)
    fun loadModelFile() = factory.loadModelFromResource(modelFileResource)
}

/**
 * A factory for creating [MappedByteBuffer] objects from an android resource. This is used by the
 * [MLTFLResourceModel] to create TF models.
 */
class MLResourceModelFactory(private val context: Context) {

    @Throws(IOException::class)
    fun loadModelFromResource(resource: Int): MappedByteBuffer {
        val fileDescriptor = context.resources.openRawResourceFd(resource)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val result = fileChannel.map(
            FileChannel.MapMode.READ_ONLY, startOffset,
            declaredLength
        )
        inputStream.close()
        fileDescriptor.close()
        return result
    }
}
