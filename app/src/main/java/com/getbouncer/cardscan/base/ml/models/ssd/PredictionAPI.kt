package com.getbouncer.cardscan.base.ml.models.ssd

import java.util.ArrayList

/**
 * A utility class that applies non-max suppression to each class. Picks out the remaining boxes,
 * the class probabilities for classes that are kept, and composes all the information in one place
 * to be returned as an object.
 */
object PredictionAPI {

    fun predictionAPI(
        k_scores: Array<FloatArray>,
        k_boxes: Array<FloatArray>,
        probThreshold: Float,
        iouThreshold: Float,
        topK: Int
    ): Result {
        val pickedBoxProbabilities = ArrayList<Float>()
        val pickedLabels = ArrayList<Int>()
        val pickedBoxes = ArrayList<FloatArray>()

        var probabilities: ArrayList<Float>
        var subsetBoxes: ArrayList<FloatArray>
        var indices: ArrayList<Int>

        // skip the background class
        for (classIndex in 1 until k_scores[0].size) {
            probabilities = ArrayList()
            subsetBoxes = ArrayList()
            for (rowIndex in k_scores.indices) {
                if (k_scores[rowIndex][classIndex] > probThreshold) {
                    probabilities.add(k_scores[rowIndex][classIndex])
                    subsetBoxes.add(k_boxes[rowIndex])
                }
            }
            if (probabilities.size == 0) {
                continue
            }
            indices = NMS.hardNMS(subsetBoxes, probabilities, iouThreshold, topK)
            for (Index in indices) {
                pickedBoxProbabilities.add(probabilities[Index])
                pickedBoxes.add(subsetBoxes[Index])
                pickedLabels.add(classIndex)
            }
        }

        return Result(
            pickedBoxes,
            pickedBoxProbabilities,
            pickedLabels
        )
    }

    /**
     * This class is used to encapsulate the results from the SSD prediction API. The results are
     * immutable.
     */
    data class Result(

        /**
         * The picked bounding boxes that pass through the non-max suppression as well as the
         * confidence thresholds.
         */
        val pickedBoxes: List<FloatArray>,

        /**
         * The probabilities associated with the picked boxes.
         */
        val pickedBoxProbabilities: List<Float>,

        /**
         * The classes associated with the pickedBoxes.
         */
        val pickedLabels: List<Int>
    )
}