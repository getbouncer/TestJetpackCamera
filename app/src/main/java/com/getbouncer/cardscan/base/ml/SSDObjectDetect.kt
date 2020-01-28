package com.getbouncer.cardscan.base.ml

import android.content.Context
import android.util.Log
import android.util.Size
import com.getbouncer.cardscan.base.domain.ScanImage
import com.getbouncer.cardscan.base.image.hasOpenGl31
import com.getbouncer.cardscan.base.image.scale
import com.getbouncer.cardscan.base.image.toRGBByteBuffer
import com.getbouncer.cardscan.base.MLTensorFlowLiteAnalyzer
import com.getbouncer.cardscan.base.TFLWebAnalyzerFactory
import com.getbouncer.cardscan.base.WebLoader
import com.getbouncer.cardscan.base.ml.ssd.ArrUtils
import com.getbouncer.cardscan.base.ml.ssd.DetectionBox
import com.getbouncer.cardscan.base.ml.ssd.PredictionAPI
import com.getbouncer.cardscan.base.ml.ssd.PriorsGen
import org.tensorflow.lite.Interpreter
import java.io.FileNotFoundException
import java.net.URL
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.time.ExperimentalTime

@ExperimentalTime
class SSDObjectDetect private constructor(interpreter: Interpreter)
    : MLTensorFlowLiteAnalyzer<ScanImage, Array<ByteBuffer>, List<DetectionBox>, Map<Int, Array<FloatArray>>>(interpreter) {

    companion object {

        /** Training images are normalized with mean 127.5 and std 128.5. */
        private const val IMAGE_MEAN = 127.5f
        private const val IMAGE_STD = 128.5f

        /**
         * How tolerant this algorithm should be of aspect ratios.
         */
        private const val ASPECT_RATIO_TOLERANCE_PCT = 10

        /**
         * We use the output from last two layers with feature maps 19x19 and 10x10
         * and for each feature map activation we have 6 priors, so total priors are
         * 19x19x6 + 10x10x6 = 2766
         */
        private const val NUM_OF_PRIORS = 2766

        /**
         * For each activation in our feature map, we have predictions for 6 bounding boxes
         * of different aspect ratios
         */
        private const val NUM_OF_PRIORS_PER_ACTIVATION = 3

        /**
         * We can detect a total of 12 objects plus the background class
         */
        private const val NUM_OF_CLASSES = 13

        /**
         * Each prior or bounding box can be represented by 4 coordinates XMin, YMin, XMax, YMax.
         */
        private const val NUM_OF_COORDINATES = 4

        /**
         * Represents the total number of data points for locations
         */
        private const val NUM_LOC = NUM_OF_COORDINATES * NUM_OF_PRIORS

        /**
         * Represents the total number of data points for classes
         */
        private const val NUM_CLASS = NUM_OF_CLASSES * NUM_OF_PRIORS

        private const val PROB_THRESHOLD = 0.3f
        private const val IOU_THRESHOLD = 0.45f
        private const val CENTER_VARIANCE = 0.1f
        private const val SIZE_VARIANCE = 0.2f
        private const val TOP_K = 10

        private val FEATURE_MAP_SIZES = intArrayOf(19, 10)

        /**
         * This value should never change, and is thread safe.
         */
        private val PRIORS by lazy { PriorsGen.combinePriors() }
    }

    override val logTag: String = "ssd_object_detect"
    override val trainedImageSize: Size = Size(300, 300)

    /**
     * The model reshapes all the data to 1 x [All Data Points]
     */
    override fun buildEmptyMLOutput(): Map<Int, Array<FloatArray>> = mapOf(
        0 to arrayOf(FloatArray(NUM_CLASS)),
        1 to arrayOf(FloatArray(NUM_LOC))
    )

    override fun transformData(data: ScanImage): Array<ByteBuffer> =
        if (data.objImage.width != trainedImageSize.width || data.objImage.height != trainedImageSize.height) {
            val aspectRatio = data.objImage.width.toDouble() / data.objImage.height
            val targetAspectRatio = trainedImageSize.width.toDouble() / trainedImageSize.height
            if (abs(1 - aspectRatio / targetAspectRatio) * 100 > ASPECT_RATIO_TOLERANCE_PCT) {
                Log.w(logTag, "Provided image ${Size(data.objImage.width, data.objImage.height)} is outside " +
                        "target aspect ratio $targetAspectRatio tolerance $ASPECT_RATIO_TOLERANCE_PCT%")
            }
            arrayOf(data.objImage.scale(trainedImageSize).toRGBByteBuffer(mean = IMAGE_MEAN, std = IMAGE_STD))
        } else {
            arrayOf(data.objImage.toRGBByteBuffer(mean = IMAGE_MEAN, std = IMAGE_STD))
        }

    override fun interpretMLOutput(data: ScanImage, mlOutput: Map<Int, Array<FloatArray>>): List<DetectionBox> {
        val outputClasses = mlOutput[0] ?: arrayOf(FloatArray(NUM_CLASS))
        val outputLocations = mlOutput[1] ?: arrayOf(FloatArray(NUM_LOC))

        var kBoxes = ArrUtils.rearrangeArray(
            outputLocations,
            FEATURE_MAP_SIZES,
            NUM_OF_PRIORS_PER_ACTIVATION,
            NUM_OF_COORDINATES
        )
        kBoxes = ArrUtils.reshape(kBoxes,
            NUM_OF_PRIORS,
            NUM_OF_COORDINATES
        )
        kBoxes = ArrUtils.convertLocationsToBoxes(
            kBoxes,
            PRIORS,
            CENTER_VARIANCE,
            SIZE_VARIANCE
        )
        kBoxes = ArrUtils.centerFormToCornerForm(kBoxes)

        var kScores = ArrUtils.rearrangeArray(
            outputClasses,
            FEATURE_MAP_SIZES,
            NUM_OF_PRIORS_PER_ACTIVATION,
            NUM_OF_CLASSES
        )
        kScores = ArrUtils.reshape(kScores,
            NUM_OF_PRIORS,
            NUM_OF_CLASSES
        )
        kScores = ArrUtils.softmax2D(kScores)

        val result = PredictionAPI.predictionAPI(
            kScores,
            kBoxes,
            PROB_THRESHOLD,
            IOU_THRESHOLD,
            TOP_K
        )

        return if (result.pickedBoxProbabilities.isNotEmpty() && result.pickedLabels.isNotEmpty()) {
            result.pickedBoxProbabilities.indices.map { i ->
                DetectionBox(
                    result.pickedBoxes[i][0],
                    result.pickedBoxes[i][1],
                    result.pickedBoxes[i][2],
                    result.pickedBoxes[i][3],
                    result.pickedBoxProbabilities[i],
                    result.pickedLabels[i]
                )
            }
        } else {
            emptyList()
        }
    }

    override fun executeInference(
        tfInterpreter: Interpreter,
        data: Array<ByteBuffer>,
        mlOutput: Map<Int, Array<FloatArray>>
    ) = tfInterpreter.runForMultipleInputsOutputs(data, mlOutput)

    /**
     * A factory for creating instances of the [SSDObjectDetect]. This downloads the model from the web. If unable to
     * download from the web, this will throw a [FileNotFoundException].
     */
    class Factory(context: Context) : TFLWebAnalyzerFactory<SSDObjectDetect>(
        WebLoader(context)
    ) {
        companion object {
            private const val USE_GPU = false
            private const val NUM_THREADS = 1
        }

        override val isThreadSafe: Boolean = true

        override val url: URL = URL("https://downloads.getbouncer.com/object_detection/v0.0.2/android/ssd.tflite")

        override val hash: String = "b7331fd09bf479a20e01b77ebf1b5edbd312639edf8dd883aa7b86f4b7fbfa62"

        override val tfOptions: Interpreter.Options = Interpreter
            .Options()
            .setUseNNAPI(USE_GPU && hasOpenGl31(context))
            .setNumThreads(NUM_THREADS)

        /**
         * Pre-download the model from the web to speed up processing time later.
         */
        @Throws(FileNotFoundException::class)
        fun warmUp() { createInterpreter() }

        override fun newInstance(): SSDObjectDetect =
            SSDObjectDetect(createInterpreter())
    }
}