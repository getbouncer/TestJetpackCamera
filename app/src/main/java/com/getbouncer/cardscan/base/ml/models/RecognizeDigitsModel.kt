package com.getbouncer.cardscan.base.ml.models

//import android.util.Size
//import android.util.TimingLogger
//import com.getbouncer.cardscan.base.R
//import com.getbouncer.cardscan.base.ml.MLResourceModelFactory
//import com.getbouncer.cardscan.base.ml.MLTFLResourceModel
//import org.tensorflow.lite.Interpreter
//import java.nio.ByteBuffer
//
//class RecognizeDigitsModel(factory: MLResourceModelFactory)
//    : MLTFLResourceModel<ByteBuffer, RecognizeDigitsModel.Prediction>(factory) {
//
//    companion object {
//        private const val LOG_TAG = "RECOGNIZE_DIGITS_MODEL"
//
//        const val NUMBER_OF_PREDICTIONS = 17
//        const val NUMBER_OF_CLASSIFIERS = 11
//
//        private const val BACKGROUND_CLASSIFIER = 10
//        private const val MINIMUM_DIGIT_CONFIDENCE = 0.15F
//
//        private fun argAndValueMax(classifiers: Array<Array<Array<FloatArray>>>, col: Int): Pair<Int, Float> {
//            var maxIdx = -1
//            var maxValue = (-1.0).toFloat()
//            for (idx in 0 until NUMBER_OF_CLASSIFIERS) {
//                val value: Float = classifiers[0][0][col][idx]
//                if (value > maxValue) {
//                    maxIdx = idx
//                    maxValue = value
//                }
//            }
//            return Pair(maxIdx, maxValue)
//        }
//    }
//
//    override val modelFileResource: Int = R.raw.fourrecognize
//    override val isThreadSafe: Boolean = true
//    override val trainedImageSize: Size = Size(80, 36)
//    override val tfOptions: Interpreter.Options = Interpreter
//        .Options()
//        .setUseNNAPI(false)
//
//    override fun analyze(data: ByteBuffer): Prediction {
//        val classifiers = Array(1) { Array(1) { Array(NUMBER_OF_PREDICTIONS) { FloatArray(NUMBER_OF_CLASSIFIERS) } } }
//        val executionTimer = TimingLogger(LOG_TAG, "analyze")
//        tfInterpreter.run(data, classifiers)
//        executionTimer.addSplit("model_execution")
//        val prediction = interpretMLResults(classifiers)
//        executionTimer.addSplit("result_interpretation")
//        executionTimer.dumpToLog()
//        return prediction
//    }
//
//    private fun interpretMLResults(classifiers: Array<Array<Array<FloatArray>>>): Prediction {
//        // TODO: implement this
//        return Prediction(emptyList(), emptyList())
//    }
//
////    fun from(classifiers: Array<Array<Array<FloatArray>>>, image: Bitmap, box: RectF): Prediction {
////        val frame: Bitmap = Bitmap.createBitmap(
////            image, box.left.roundToInt(), box.top.roundToInt(),
////            box.width().roundToInt(), box.height().roundToInt()
////        )
////        model.classifyFrame(frame)
////        val digits = ArrayList<Int>()
////        val confidence = ArrayList<Float>()
////        for (col in 0 until NUMBER_OF_PREDICTIONS) {
////            val argAndConf = argAndValueMax(classifiers, col)
////            if (argAndConf.confidence < RecognizedDigits.kDigitMinConfidence) {
////                digits.add(RecognizedDigits.kBackgroundClass)
////            } else {
////                digits.add(argAndConf.argMax)
////            }
////            confidence.add(argAndConf.confidence)
////        }
////        return RecognizedDigits(digits, confidence)
////    }
//
//    data class Prediction(val digits: List<Int>, val confidence: List<Float>)
//
//}
