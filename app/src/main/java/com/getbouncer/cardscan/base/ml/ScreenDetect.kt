package com.getbouncer.cardscan.base.ml

import android.content.Context
import android.util.Log
import android.util.Size
import com.getbouncer.cardscan.base.domain.ScanImage
import com.getbouncer.cardscan.base.image.ImageTransformValues
import com.getbouncer.cardscan.base.image.hasOpenGl31
import com.getbouncer.cardscan.base.image.scale
import com.getbouncer.cardscan.base.image.toRGBByteBuffer
import com.getbouncer.cardscan.base.MLTensorFlowLiteAnalyzer
import com.getbouncer.cardscan.base.TFLWebAnalyzerFactory
import com.getbouncer.cardscan.base.WebLoader
import org.tensorflow.lite.Interpreter
import java.io.FileNotFoundException
import java.net.URL
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.time.ExperimentalTime

@ExperimentalTime
class ScreenDetect private constructor(interpreter: Interpreter)
    : MLTensorFlowLiteAnalyzer<ScanImage, ByteBuffer, List<Float>, Array<FloatArray>>(interpreter) {

    companion object {
        /** images are normalized using mean and standard deviation of Imagenet  */
        private val IMAGE_MEAN = ImageTransformValues(123.7F, 116.3F, 103.5F)
        private val IMAGE_STD = ImageTransformValues(58.4F, 57.1F, 57.4F)

        /**
         * How tolerant this algorithm should be of aspect ratios.
         */
        private const val ASPECT_RATIO_TOLERANCE_PCT = 10

        /** model returns whether or not there is a screen present  */
        const val NUM_CLASS = 2
    }

    override val logTag: String = "screen_detect"
    override val trainedImageSize: Size = Size(200, 200)

    override fun buildEmptyMLOutput() = arrayOf(FloatArray(NUM_CLASS))

    override fun interpretMLOutput(data: ScanImage, mlOutput: Array<FloatArray>): List<Float> =
        listOf(mlOutput[0][0], mlOutput[0][1])

    override fun transformData(data: ScanImage): ByteBuffer =
        if (data.fullImage.width != trainedImageSize.width || data.fullImage.height != trainedImageSize.height) {
            val aspectRatio = data.fullImage.width.toDouble() / data.fullImage.height
            val targetAspectRatio = trainedImageSize.width.toDouble() / trainedImageSize.height
            if (abs(1 - aspectRatio / targetAspectRatio) * 100 > ASPECT_RATIO_TOLERANCE_PCT) {
                Log.w(logTag, "Provided image ${Size(data.fullImage.width, data.fullImage.height)} is outside " +
                        "target aspect ratio $targetAspectRatio tolerance $ASPECT_RATIO_TOLERANCE_PCT%")
            }
            data.fullImage.scale(trainedImageSize).toRGBByteBuffer(mean = IMAGE_MEAN, std = IMAGE_STD)
        } else {
            data.fullImage.toRGBByteBuffer(mean = IMAGE_MEAN, std = IMAGE_STD)
        }

    override fun executeInference(
        tfInterpreter: Interpreter,
        data: ByteBuffer,
        mlOutput: Array<FloatArray>
    ) = tfInterpreter.run(data, mlOutput)

    /**
     * A factory for creating instances of the [ScreenDetect]. This downloads the model from the web. If unable to
     * download from the web, this will throw a [FileNotFoundException].
     */
    class Factory(context: Context) : TFLWebAnalyzerFactory<ScreenDetect>(
        WebLoader(context)
    ) {
        companion object {
            private const val USE_GPU = false
            private const val NUM_THREADS = 1
        }

        override val isThreadSafe: Boolean = true

        override val url = URL("https://downloads.getbouncer.com/bob/v0.0.2/android/bob.tflite")

        override val hash = "15b4c25b9bfffa3a2c4a84a5e258d577d571ec152b03d493a29ead36cb9bf668"

        override val tfOptions: Interpreter.Options = Interpreter
            .Options()
            .setUseNNAPI(USE_GPU && hasOpenGl31(context))
            .setNumThreads(NUM_THREADS)

        /**
         * Pre-download the model from the web to speed up processing time later.
         */
        @Throws(FileNotFoundException::class)
        fun warmUp() { createInterpreter() }

        override fun newInstance(): ScreenDetect =
            ScreenDetect(createInterpreter())
    }
}