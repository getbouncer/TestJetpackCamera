package com.getbouncer.cardscan.base.ml.models

import android.content.Context
import android.graphics.RectF
import android.util.Size
import com.getbouncer.cardscan.base.R
import com.getbouncer.cardscan.base.domain.DetectionBox
import com.getbouncer.cardscan.base.domain.ScanImage
import com.getbouncer.cardscan.base.image.hasOpenGl31
import com.getbouncer.cardscan.base.image.scale
import com.getbouncer.cardscan.base.image.toRGBByteBuffer
import com.getbouncer.cardscan.base.ml.MLResourceModelFactory
import com.getbouncer.cardscan.base.ml.MLTFLResourceModel
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import kotlin.collections.ArrayList
import kotlin.time.ExperimentalTime


@ExperimentalTime
class FindFourModel(
    factory: MLResourceModelFactory,
    context: Context,
    private val originalImageSize: Size
) : MLTFLResourceModel<ScanImage, ByteBuffer, FindFourModel.Prediction, Array<Array<Array<FloatArray>>>>(factory) {

    override val logTag: String = "FIND_FOUR_MODEL"

    override fun buildEmptyMLOutput(): Array<Array<Array<FloatArray>>> =
        Array(1) { Array(ROWS) { Array(COLS) { FloatArray(CLASSES) } } }

    companion object {
        const val ROWS = 34
        const val COLS = 51

        private const val CLASSES = 3
        private const val NUM_THREADS = 4
        private const val USE_GPU = false

        private const val DIGIT_CLASSIFIER = 1
        private const val EXPIRY_CLASSIFIER = 2

        private fun hasDigits(mlOutput: Array<Array<Array<FloatArray>>>, row: Int, col: Int): Boolean = digitConfidence(mlOutput, row, col) >= 0.5
        private fun hasExpiry(mlOutput: Array<Array<Array<FloatArray>>>, row: Int, col: Int): Boolean = expiryConfidence(mlOutput, row, col) >= 0.5

        private fun digitConfidence(mlOutput: Array<Array<Array<FloatArray>>>, row: Int, col: Int): Float = mlOutput[0][row][col][DIGIT_CLASSIFIER]
        private fun expiryConfidence(mlOutput: Array<Array<Array<FloatArray>>>, row: Int, col: Int): Float = mlOutput[0][row][col][EXPIRY_CLASSIFIER]
    }

    override val modelFileResource: Int = R.raw.findfour
    override val trainedImageSize: Size = Size(480, 302)
    override val tfOptions: Interpreter.Options = Interpreter
        .Options()
        .setUseNNAPI(USE_GPU && hasOpenGl31(context))
        .setNumThreads(NUM_THREADS)

    override fun transformData(data: ScanImage): ByteBuffer = data.ocrImage.scale(trainedImageSize).toRGBByteBuffer()

    override fun interpretMLOutput(data: ScanImage, mlOutput: Array<Array<Array<FloatArray>>>): Prediction {
        val digitBoxes = ArrayList<DetectionBox>()
        for (row in 0..ROWS) {
            for (col in 0..COLS) {
                if (hasDigits(mlOutput, row, col)) {
                    digitBoxes.add(DetectionBox(
                        getBounds(row, col),
                        row,
                        col,
                        digitConfidence(mlOutput, row, col)
                    ))
                }
            }
        }

        val expiryBoxes = ArrayList<DetectionBox>()
        for (row in 0..ROWS) {
            for (col in 0..COLS) {
                if (hasExpiry(mlOutput, row, col)) {
                    expiryBoxes.add(DetectionBox(
                        getBounds(row, col),
                        row,
                        col,
                        expiryConfidence(mlOutput, row, col)
                    ))
                }
            }
        }

        return Prediction(digitBoxes, expiryBoxes)
    }

    /**
     * Resize the box to transform it from the model's coordinates into the image's coordinates
     */
    private fun getBounds(row: Int, col: Int): RectF {
        val w: Float = DetectionBox.BOX_SIZE.width * originalImageSize.width / trainedImageSize.width
        val h: Float = DetectionBox.BOX_SIZE.height * originalImageSize.height / trainedImageSize.height
        val x: Float = (originalImageSize.width - w) / (COLS - 1).toFloat() * col.toFloat()
        val y: Float = (originalImageSize.height - h) / (ROWS - 1).toFloat() * row.toFloat()

        return RectF(x, y, x + w, y + h)
    }

    data class Prediction(val detectedDigits: List<DetectionBox>, val detectedExpiry: List<DetectionBox>)

    override fun executeInterpreter(
        tfInterpreter: Interpreter,
        data: ByteBuffer,
        mlOutput: Array<Array<Array<FloatArray>>>
    ) = tfInterpreter.run(data, mlOutput)
}