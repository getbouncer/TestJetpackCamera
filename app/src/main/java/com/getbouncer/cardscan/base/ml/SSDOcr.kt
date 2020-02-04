package com.getbouncer.cardscan.base.ml

import android.content.Context
import android.util.Log
import android.util.Size
import com.getbouncer.cardscan.base.R
import com.getbouncer.cardscan.base.image.ScanImage
import com.getbouncer.cardscan.base.image.hasOpenGl31
import com.getbouncer.cardscan.base.image.scale
import com.getbouncer.cardscan.base.image.toRGBByteBuffer
import com.getbouncer.cardscan.base.ResourceLoader
import com.getbouncer.cardscan.base.MLTensorFlowLiteAnalyzer
import com.getbouncer.cardscan.base.TFLResourceAnalyzerFactory
import com.getbouncer.cardscan.base.ml.ssd.DetectionBox
import com.getbouncer.cardscan.base.ml.ssd.combinePriors
import com.getbouncer.cardscan.base.ml.ssd.domain.adjustLocations
import com.getbouncer.cardscan.base.ml.ssd.extractPredictions
import com.getbouncer.cardscan.base.util.reshape
import com.getbouncer.cardscan.base.ml.ssd.domain.softMax2D
import com.getbouncer.cardscan.base.ml.ssd.domain.toRectForm
import com.getbouncer.cardscan.base.util.updateEach
import com.getbouncer.cardscan.base.ml.ssd.rearrangeOCRArray
import com.getbouncer.cardscan.base.util.CreditCardUtils.isValidCardNumber
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.time.ExperimentalTime

/**
 * This model performs SSD OCR recognition on a card.
 */
@ExperimentalTime
class SSDOcr private constructor(interpreter: Interpreter)
    : MLTensorFlowLiteAnalyzer<ScanImage, Array<ByteBuffer>, CardOcrResult, Map<Int, Array<FloatArray>>>(interpreter) {

    companion object {

        val TRAINED_IMAGE_SIZE = Size(600, 375)

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
        private const val NUM_OF_PRIORS = 3420

        /**
         * For each activation in our feature map, we have predictions for 6 bounding boxes
         * of different aspect ratios
         */
        private const val NUM_OF_PRIORS_PER_ACTIVATION = 3

        /**
         * We can detect a total of 10 numbers (0 - 9) plus the background class
         */
        private const val NUM_OF_CLASSES = 11

        /**
         * Each prior or bounding box can be represented by 4 coordinates
         * XMin, YMin, XMax, YMax.
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

        private const val PROB_THRESHOLD = 0.50f
        private const val IOU_THRESHOLD = 0.50f
        private const val CENTER_VARIANCE = 0.1f
        private const val SIZE_VARIANCE = 0.2f
        private const val LIMIT = 20

        private val FEATURE_MAP_SIZES =
            OcrFeatureMapSizes(
                layerOneWidth = 38,
                layerOneHeight = 24,
                layerTwoWidth = 19,
                layerTwoHeight = 12
            )

        /**
         * This value should never change, and is thread safe.
         */
        private val PRIORS = combinePriors()
    }

    override val logTag: String = "ssd_ocr"

    /**
     * The model reshapes all the data to 1 x [All Data Points]
     */
    override fun buildEmptyMLOutput(): Map<Int, Array<FloatArray>> = mapOf(
        0 to arrayOf(FloatArray(NUM_CLASS)),
        1 to arrayOf(FloatArray(NUM_LOC))
    )

    override fun transformData(data: ScanImage): Array<ByteBuffer> =
        if (data.ocrImage.width != TRAINED_IMAGE_SIZE.width || data.ocrImage.height != TRAINED_IMAGE_SIZE.height) {
            val aspectRatio = data.ocrImage.width.toDouble() / data.ocrImage.height
            val targetAspectRatio = TRAINED_IMAGE_SIZE.width.toDouble() / TRAINED_IMAGE_SIZE.height
            if (abs(1 - aspectRatio / targetAspectRatio) * 100 > ASPECT_RATIO_TOLERANCE_PCT) {
                Log.w(logTag, "Provided image ${Size(data.ocrImage.width, data.ocrImage.height)} is outside " +
                        "target aspect ratio $targetAspectRatio tolerance $ASPECT_RATIO_TOLERANCE_PCT%")
            }
            arrayOf(data.ocrImage.scale(TRAINED_IMAGE_SIZE).toRGBByteBuffer(mean = IMAGE_MEAN, std = IMAGE_STD))
        } else {
            arrayOf(data.ocrImage.toRGBByteBuffer(mean = IMAGE_MEAN, std = IMAGE_STD))
        }

    override fun interpretMLOutput(data: ScanImage, mlOutput: Map<Int, Array<FloatArray>>): CardOcrResult {
        val outputClasses = mlOutput[0] ?: arrayOf(FloatArray(NUM_CLASS))
        val outputLocations = mlOutput[1] ?: arrayOf(FloatArray(NUM_LOC))

        val boxes = rearrangeOCRArray(
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
        boxes.updateEach { it.toRectForm() }

        val scores = rearrangeOCRArray(
            locations = outputClasses,
            featureMapSizes = FEATURE_MAP_SIZES,
            numberOfPriors = NUM_OF_PRIORS_PER_ACTIVATION,
            locationsPerPrior = NUM_OF_CLASSES
        ).reshape(NUM_OF_CLASSES)
        scores.forEach { it.softMax2D() }

        val detectedBoxes = extractPredictions(
            scores = scores,
            boxes = boxes,
            probabilityThreshold = PROB_THRESHOLD,
            intersectionOverUnionThreshold = IOU_THRESHOLD,
            limit = LIMIT,
            classifierToLabel = { if (it == 10) 0 else it }
        ).sortedBy { it.rect.left }

        val predictedNumber = detectedBoxes.map { it.label }.joinToString("")
        return if (isValidCardNumber(predictedNumber)) {
            CardOcrResult(
                number = CardNumber(
                    predictedNumber,
                    detectedBoxes
                ),
                expiry = null
            )
        } else {
            CardOcrResult(null, null)
        }
    }

    override fun executeInference(
        tfInterpreter: Interpreter,
        data: Array<ByteBuffer>,
        mlOutput: Map<Int, Array<FloatArray>>
    ) = tfInterpreter.runForMultipleInputsOutputs(data, mlOutput)

    /**
     * A factory for creating instances of the [SSDOcr].
     */
    class Factory(context: Context) : TFLResourceAnalyzerFactory<SSDOcr>(
        ResourceLoader(context)
    ) {
        companion object {
            private const val USE_GPU = false
            private const val NUM_THREADS = 1
        }

        override val isThreadSafe: Boolean = true

        override val modelFileResource: Int = R.raw.darknite

        override val tfOptions: Interpreter.Options = Interpreter
            .Options()
            .setUseNNAPI(USE_GPU && hasOpenGl31(context))
            .setNumThreads(NUM_THREADS)

        override fun newInstance(): SSDOcr? = createInterpreter()?.let {
            SSDOcr(it)
        }
    }
}

data class CardOcrResult(val number: CardNumber?, val expiry: CardExpiry?)
data class CardNumber(val number: String, val boxes: Collection<DetectionBox>)
data class CardExpiry(val day: String?, val month: String, val year: String, val boxes: Collection<DetectionBox>)

data class OcrFeatureMapSizes(
    val layerOneWidth: Int,
    val layerOneHeight: Int,
    val layerTwoWidth: Int,
    val layerTwoHeight: Int
)

