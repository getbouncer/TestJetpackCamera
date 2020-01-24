package com.getbouncer.cardscan.base.ml.models

import android.content.Context
import android.util.Log
import android.util.Size
import com.getbouncer.cardscan.base.R
import com.getbouncer.cardscan.base.domain.CardNumber
import com.getbouncer.cardscan.base.domain.CardOcrResult
import com.getbouncer.cardscan.base.domain.ScanImage
import com.getbouncer.cardscan.base.image.hasOpenGl31
import com.getbouncer.cardscan.base.image.scale
import com.getbouncer.cardscan.base.image.toRGBByteBuffer
import com.getbouncer.cardscan.base.ml.ResourceLoader
import com.getbouncer.cardscan.base.ml.MLTensorFlowLiteAnalyzer
import com.getbouncer.cardscan.base.ml.TFLResourceAnalyzerFactory
import com.getbouncer.cardscan.base.ml.models.ssd.ArrUtils
import com.getbouncer.cardscan.base.ml.models.ssd.OcrDetectionBox
import com.getbouncer.cardscan.base.ml.models.ssd.OcrPriorsGen
import com.getbouncer.cardscan.base.ml.models.ssd.PredictionAPI
import com.getbouncer.cardscan.base.util.CreditCardUtils.isValidCardNumber
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.util.Hashtable
import kotlin.math.abs
import kotlin.time.ExperimentalTime

/**
 * This is actually an aggregating model. While not a model itself, it makes use of other models
 * to perform analysis.
 *
 * This requires some dependencies to work
 * - factory:       A factory to create the sub models. Alternatively, sub models could be created
 *                  externally and passed in as constructor parameters
 * - context:       An android context used by some sub models. This dependency can be removed if
 *                  sub models are constructed externally
 * - cardRect:      The location of the card within the preview image. This is used for cropping
 *                  the preview image.
 * - resultHandler: A handler for the result. Usually this is the main activity.
 */
@ExperimentalTime
class SSDOcr private constructor(interpreter: Interpreter)
    : MLTensorFlowLiteAnalyzer<ScanImage, Array<ByteBuffer>, CardOcrResult, Map<Int, Array<FloatArray>>>(interpreter) {

    companion object {
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
         * We can detect a total of 12 objects plus the background class
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
        private const val TOP_K = 20
        private val featureMapSizes by lazy {
            Hashtable<String, Int>().apply {
                this["layerOneWidth"] = 38
                this["layerOneHeight"] = 24
                this["layerTwoWidth"] = 19
                this["layerTwoHeight"] = 12
            }
        }

        /**
         * This value should never change, and is thread safe.
         */
        private val priors by lazy { OcrPriorsGen.combinePriors() }
    }

    override val logTag: String = "ssd_ocr"
    override val trainedImageSize: Size = Size(600, 375)

    /**
     * The model reshapes all the data to 1 x [All Data Points]
     */
    override fun buildEmptyMLOutput(): Map<Int, Array<FloatArray>> = mapOf(
        0 to arrayOf(FloatArray(NUM_CLASS)),
        1 to arrayOf(FloatArray(NUM_LOC))
    )

    override fun transformData(data: ScanImage): Array<ByteBuffer> =
        if (data.ocrImage.width != trainedImageSize.width || data.ocrImage.height != trainedImageSize.height) {
            val aspectRatio = data.ocrImage.width.toDouble() / data.ocrImage.height
            val targetAspectRatio = trainedImageSize.width.toDouble() / trainedImageSize.height.toDouble()
            if (abs(1 - aspectRatio / targetAspectRatio) > ASPECT_RATIO_TOLERANCE_PCT / 100F) {
                Log.w(logTag, "Provided image ${Size(data.ocrImage.width, data.ocrImage.height)} is outside " +
                        "target aspect ratio $targetAspectRatio tolerance $ASPECT_RATIO_TOLERANCE_PCT%")
            }
            arrayOf(data.ocrImage.scale(trainedImageSize).toRGBByteBuffer(mean = IMAGE_MEAN, std = IMAGE_STD))
        } else {
            arrayOf(data.ocrImage.toRGBByteBuffer(mean = IMAGE_MEAN, std = IMAGE_STD))
        }

    @Synchronized
    override fun interpretMLOutput(data: ScanImage, mlOutput: Map<Int, Array<FloatArray>>): CardOcrResult {
        val arrUtils = ArrUtils()
        val outputClasses = mlOutput[0] ?: arrayOf(FloatArray(NUM_CLASS))
        val outputLocations = mlOutput[1] ?: arrayOf(FloatArray(NUM_LOC))

        var kBoxes = arrUtils.rearrangeOCRArray(
            outputLocations,
            featureMapSizes,
            NUM_OF_PRIORS_PER_ACTIVATION,
            NUM_OF_COORDINATES
        )
        kBoxes = arrUtils.reshape(
            kBoxes,
            NUM_OF_PRIORS,
            NUM_OF_COORDINATES
        )
        kBoxes = arrUtils.convertLocationsToBoxes(
            kBoxes,
            priors,
            CENTER_VARIANCE,
            SIZE_VARIANCE
        )
        kBoxes = arrUtils.centerFormToCornerForm(kBoxes)

        var kScores = arrUtils.rearrangeOCRArray(
            outputClasses,
            featureMapSizes,
            NUM_OF_PRIORS_PER_ACTIVATION,
            NUM_OF_CLASSES
        )
        kScores = arrUtils.reshape(
            kScores,
            NUM_OF_PRIORS,
            NUM_OF_CLASSES
        )
        kScores = arrUtils.softmax2D(kScores)

        val result = PredictionAPI.predictionAPI(
            kScores,
            kBoxes,
            PROB_THRESHOLD,
            IOU_THRESHOLD,
            TOP_K
        )

        val objectBoxes = if (result.pickedBoxProbabilities.isNotEmpty() && result.pickedLabels.isNotEmpty()) {
            result.pickedBoxProbabilities.indices.map { i ->
                val label = if (result.pickedLabels[i] == 10) 0 else result.pickedLabels[i]
                OcrDetectionBox(
                    result.pickedBoxes[i][0],
                    result.pickedBoxes[i][1],
                    result.pickedBoxes[i][2],
                    result.pickedBoxes[i][3],
                    Size(data.ocrImage.width, data.ocrImage.height),
                    result.pickedBoxProbabilities[i],
                    label
                )
            }
        } else {
            emptyList()
        }.sorted()

        val predictedNumber = objectBoxes.map { it.label }.joinToString("")
        return if (isValidCardNumber(predictedNumber)) {
            CardOcrResult(CardNumber(predictedNumber, objectBoxes), null)
        } else {
            CardOcrResult(null, null)
        }
    }

    override fun executeInterpreter(
        tfInterpreter: Interpreter,
        data: Array<ByteBuffer>,
        mlOutput: Map<Int, Array<FloatArray>>
    ) = tfInterpreter.runForMultipleInputsOutputs(data, mlOutput)

    /**
     * A factory for creating instances of the [SSDOcr].
     */
    class Factory(context: Context) : TFLResourceAnalyzerFactory<SSDOcr>(ResourceLoader(context)) {
        companion object {
            private const val USE_GPU = false
            private const val NUM_THREADS = 1
        }

        override val isThreadSafe: Boolean = true

        override val modelFileResource: Int = R.raw.ssdelrond0136

        override val tfOptions: Interpreter.Options = Interpreter
            .Options()
            .setUseNNAPI(USE_GPU && hasOpenGl31(context))
            .setNumThreads(NUM_THREADS)

        override fun newInstance(): SSDOcr = SSDOcr(createInterpreter())
    }
}
