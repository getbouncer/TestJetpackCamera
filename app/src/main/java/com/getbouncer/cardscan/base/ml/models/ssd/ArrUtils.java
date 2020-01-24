package com.getbouncer.cardscan.base.ml.models.ssd;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;

/**
 * Basic Matrix handling utilities needed for SSD Framework
 */
public class ArrUtils {

    public float[][] reshape(float[][] nums, int r, int c) {
        int totalElements = nums.length * nums[0].length;
        if (totalElements != r * c || totalElements % r != 0) {
            return nums;
        }

        final float[][] result = new float[r][c];
        int newR = 0;
        int newC = 0;

        for (float[] num : nums) {
            for (float v : num) {
                result[newR][newC] = v;
                newC++;
                if (newC == c) {
                    newC = 0;
                    newR++;
                }
            }
        }

        return result;
    }

    public float[][] rearrangeOCRArray(
            float[][] locations,
            Hashtable<String, Integer> featureMapSizes,
            int noOfPriors,
            int locationsPerPrior
    ) {
        int totalLocationsForAllLayers = featureMapSizes.get("layerOneWidth")
                * featureMapSizes.get("layerOneHeight")
                * noOfPriors * locationsPerPrior
                + featureMapSizes.get("layerTwoWidth")
                * featureMapSizes.get("layerTwoHeight")
                * noOfPriors * locationsPerPrior;

        float[][] rearranged = new float[1][totalLocationsForAllLayers];
        Integer[] featureMapHeights = {featureMapSizes.get("layerOneHeight"),
                featureMapSizes.get("layerTwoHeight")};

        Integer[] featureMapWidths = {featureMapSizes.get("layerOneWidth"),
                featureMapSizes.get("layerTwoWidth")};

        Iterator<Integer> heightIterator = Arrays.asList(featureMapHeights).iterator();
        Iterator<Integer> widthIterator = Arrays.asList(featureMapWidths).iterator();

        int offset = 0;
        while (heightIterator.hasNext() && widthIterator.hasNext()){
            int height = heightIterator.next();
            int width = widthIterator.next();

            int totalNumberOfLocationsForThisLayer = height * width * noOfPriors * locationsPerPrior;
            int stepsForLoop = height - 1;
            int j;
            int i = 0;
            int step = 0;

            while (i < totalNumberOfLocationsForThisLayer){
                while (step < height){
                    j = step;
                    while (j < totalNumberOfLocationsForThisLayer - stepsForLoop + step){
                        rearranged[0][offset + i] = locations[0][offset + j];
                        i++;
                        j = j + height;
                    }
                    step++;
                }
                offset = offset + totalNumberOfLocationsForThisLayer;
            }
        }

        return rearranged;
    }

    /**
     * Convert regressional location results of SSD into boxes in the form of:
     *
     * ```
     * (center_x, center_y, h, w)
     * ```
     */
    public float[][] convertLocationsToBoxes(float[][] locations, float[][] priors, float centerVariance, float sizeVariance) {
        float[][] boxes = new float[locations.length][locations[0].length];

        for (int i = 0; i< locations.length; i++){
            for(int j = 0; j < 2 ; j++){
                boxes[i][j] = locations[i][j] * centerVariance * priors[i][j+2] + priors[i][j];
                boxes[i][j+2] = (float) (Math.exp(locations[i][j+2]* sizeVariance) * priors[i][j+2]);

            }

        }

        return boxes;
    }

    /**
     * Convert center from (center_x, center_y, h, w) to corner form (XMin, YMin, XMax, YMax)
     */
    public float[][] centerFormToCornerForm(float[][] locations) {
        float[][] boxes = new float[locations.length][locations[0].length];

        for(int i = 0; i < locations.length; i++){
            for (int j = 0; j < 2; j++){
                boxes[i][j] = locations[i][j] - locations[i][j+2]/2;
                boxes[i][j+2] = locations[i][j] + locations[i][j+2]/2;
            }
        }

        return boxes;
    }

    /**
     * Clamp the value between min and max
     */
    public static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    /**
     * Compute softmax for each row. This will replace each row value with a value normalized by the
     * sum of all the values in the same row.
     */
    public float[][] softmax2D(float[][] scores) {
        float[][] normalizedScores = new float[scores.length][scores[0].length];
        float rowSum;

        for(int i = 0; i < scores.length; i++){
            rowSum = 0.0f;
            for(int j = 0; j < scores[0].length; j++){
                rowSum = (float) (rowSum + Math.exp(scores[i][j]));
            }
            for(int j = 0; j < scores[0].length; j++){
                normalizedScores[i][j] = (float) (Math.exp(scores[i][j]) / rowSum);
            }
        }

        return normalizedScores;
    }
}