package com.getbouncer.cardscan.base.ml

import android.content.Context
import android.util.Log
import android.util.Size
import com.getbouncer.cardscan.base.image.ScanImage
import com.getbouncer.cardscan.base.image.hasOpenGl31
import com.getbouncer.cardscan.base.image.scale
import com.getbouncer.cardscan.base.image.toRGBByteBuffer
import com.getbouncer.cardscan.base.MLTensorFlowLiteAnalyzer
import com.getbouncer.cardscan.base.TFLWebAnalyzerFactory
import com.getbouncer.cardscan.base.WebLoader
import com.getbouncer.cardscan.base.ml.ssd.DetectionBox
import com.getbouncer.cardscan.base.ml.ssd.domain.adjustLocations
import com.getbouncer.cardscan.base.ml.ssd.domain.softMax2D
import com.getbouncer.cardscan.base.ml.ssd.domain.toRectForm
import com.getbouncer.cardscan.base.ml.ssd.extractPredictions
import com.getbouncer.cardscan.base.ml.ssd.rearrangeArray
import com.getbouncer.cardscan.base.util.reshape
import com.getbouncer.cardscan.base.ml.ssd.ObjectPriorsGen
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
        private const val NUM_OF_PRIORS_PER_ACTIVATION = 6

        /**
         * We can detect a total of 13 objects plus the background class
         */
        private const val NUM_OF_CLASSES = 14

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
        private const val LIMIT = 10

        private val TRAINED_IMAGE_SIZE = Size(300, 300)

        private val FEATURE_MAP_SIZES = intArrayOf(19, 10)

        /**
         * This value should never change, and is thread safe.
         */
        private val PRIORS by lazy { ObjectPriorsGen.combinePriors() }
    }

    override val logTag: String = "ssd_object_detect"

    /**
     * The model reshapes all the data to 1 x [All Data Points]
     */
    override fun buildEmptyMLOutput(): Map<Int, Array<FloatArray>> = mapOf(
        0 to arrayOf(FloatArray(NUM_CLASS)),
        1 to arrayOf(FloatArray(NUM_LOC))
    )

    override fun transformData(data: ScanImage): Array<ByteBuffer> =
        if (data.objImage.width != TRAINED_IMAGE_SIZE.width || data.objImage.height != TRAINED_IMAGE_SIZE.height) {
            val aspectRatio = data.objImage.width.toDouble() / data.objImage.height
            val targetAspectRatio = TRAINED_IMAGE_SIZE.width.toDouble() / TRAINED_IMAGE_SIZE.height
            if (abs(1 - aspectRatio / targetAspectRatio) * 100 > ASPECT_RATIO_TOLERANCE_PCT) {
                Log.w(logTag, "Provided image ${Size(data.objImage.width, data.objImage.height)} is outside " +
                        "target aspect ratio $targetAspectRatio tolerance $ASPECT_RATIO_TOLERANCE_PCT%")
            }
            arrayOf(data.objImage.scale(TRAINED_IMAGE_SIZE).toRGBByteBuffer(mean = IMAGE_MEAN, std = IMAGE_STD))
        } else {
            arrayOf(data.objImage.toRGBByteBuffer(mean = IMAGE_MEAN, std = IMAGE_STD))
        }

    override fun interpretMLOutput(data: ScanImage, mlOutput: Map<Int, Array<FloatArray>>): List<DetectionBox> {
        val outputClasses = mlOutput[0] ?: arrayOf(FloatArray(NUM_CLASS))
        val outputLocations = mlOutput[1] ?: arrayOf(FloatArray(NUM_LOC))

        val boxes = rearrangeArray(
            locations = outputLocations,
            featureMapSizes = FEATURE_MAP_SIZES,
            numberOfPriors = NUM_OF_PRIORS_PER_ACTIVATION,
            locationsPerPrior = NUM_OF_COORDINATES
        ).reshape(NUM_OF_COORDINATES)
        boxes.adjustLocations(
            priors = PRIORS,
            centerVariance = CENTER_VARIANCE,
            sizeVariance = SIZE_VARIANCE
        )
        boxes.forEach { it.toRectForm() }

        val scores = rearrangeArray(
            locations = outputClasses,
            featureMapSizes = FEATURE_MAP_SIZES,
            numberOfPriors = NUM_OF_PRIORS_PER_ACTIVATION,
            locationsPerPrior = NUM_OF_CLASSES
        ).reshape(NUM_OF_CLASSES)
        scores.forEach { it.softMax2D() }

        return extractPredictions(
            scores = scores,
            boxes = boxes,
            probabilityThreshold = PROB_THRESHOLD,
            intersectionOverUnionThreshold = IOU_THRESHOLD,
            limit = LIMIT
        )
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

        override val url: URL = URL("https://downloads.getbouncer.com/object_detection/v0.0.4/android/ssd.tflite")

        override val hash: String = "2f6fdf7abc37a0db4c06281f8b4c00037268b3c6b460840480bfe2c5e1af39e8"

        override val tfOptions: Interpreter.Options = Interpreter
            .Options()
            .setUseNNAPI(USE_GPU && hasOpenGl31(context))
            .setNumThreads(NUM_THREADS)

        override fun newInstance(): SSDObjectDetect? = createInterpreter()?.let { SSDObjectDetect(it) }
    }
}