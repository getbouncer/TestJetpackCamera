package com.getbouncer.cardscan.base.ml.models.ssd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class NMS{

    /**
     * In this project we implement HARD NMS and NOT Soft NMS. I highly recommend checkout SOFT NMS
     * implementation of Facebook Detectron Framework
     *
     * @param subsetBoxes (N, 4): boxes in corner-form and probabilities.
     * @param iouThreshold: intersection over union threshold.
     * @param topK: keep top_k results. If k <= 0, keep all the results.
     *
     * @return pickedIndices: a list of indexes of the kept boxes
     */
    public static ArrayList<Integer> hardNMS(
            ArrayList<float[]> subsetBoxes,
            ArrayList<Float> probabilities,
            float iouThreshold,
            int topK
    ) {
        float iou ;
        Float[] prob = probabilities.toArray(new Float[probabilities.size()]);

        ArrayIndexComparator comparator = new ArrayIndexComparator(prob);
        Integer[] indexes = comparator.createIndexArray();
        Arrays.sort(indexes, comparator);

        if(indexes.length > 200){  // Exceptional Situation
            indexes = Arrays.copyOfRange(indexes, 0, 200);
        }
        ArrayList<Integer> indexArray = new ArrayList<>(Arrays.asList(indexes));

        int current;
        float[] currentBox;
        ArrayList<Integer> pickedIndices = new ArrayList<>();

        while(indexArray.size() > 0){
            current = indexArray.get(0);
            pickedIndices.add(current);

            if (topK > 0 && topK == pickedIndices.size() || indexArray.size() == 1){
                break;
            }
            currentBox = subsetBoxes.get(current);
            indexArray.remove(0);

            Iterator<Integer> it = indexArray.iterator();
            while(it.hasNext()){
                Integer Index = it.next();
                iou = intersectionOverUnionOf(currentBox, subsetBoxes.get(Index));
                if(iou >= iouThreshold){
                    it.remove();

                }
            }
        }

        return pickedIndices;
    }

    /**
     * Return intersection-over-union (Jaccard index) of boxes.
     *
     * Args:
     * boxes0 (N, 4): ground truth boxes.
     *        boxes1 (N or 1, 4): predicted boxes.
     * eps: a small number to avoid 0 as denominator.
     * Returns: iou (N): IOU values
     */
    private static float intersectionOverUnionOf(float[] currentBox, float[] nextBox) {
        float eps = 0.00001f;
        float overlapArea;
        float area0;
        float area1;

        float[] area0Left = new float[2];
        float[] area1Left = new float[2];

        float[] area0Right = new float[2];
        float[] area1Right = new float[2];


        float[] overlap_left_top = new float[2];
        float[] overlap_right_bottom = new float[2];

        overlap_left_top[0] = Math.max(nextBox[0],currentBox[0]);
        overlap_left_top[1] = Math.max(nextBox[1],currentBox[1]);

        overlap_right_bottom[0] = Math.min(nextBox[2],currentBox[2]);
        overlap_right_bottom[1] = Math.min(nextBox[3],currentBox[3]);

        overlapArea = AreaOf(overlap_left_top, overlap_right_bottom);

        area0Left[0] = nextBox[0]; area0Left[1] = nextBox[1];
        area0Right[0] = nextBox[2]; area0Right[1] = nextBox[3];

        area1Left[0] = currentBox[0]; area1Left[1] = currentBox[1];
        area1Right[0] = currentBox[2]; area1Right[1] = currentBox[3];

        area0 = AreaOf(area0Left, area0Right);
        area1 = AreaOf(area1Left, area1Right);

        return (overlapArea / (area0 + area1 - overlapArea + eps));

    }

    /**
     * Compute the areas of rectangles given two corners.
     *
     * Args:
     * left_top (N, 2): left top corner.
     *        right_bottom (N, 2): right bottom corner.
     *
     *        Returns:
     * area (N): return the area.
     */
    private static float AreaOf(float[] leftTop, float[] rightBottom) {
        float left, right;
        left = rightBottom[0] - leftTop[0];
        left = ArrUtils.clamp(left, 0.0f, 1000.0f);
        right = rightBottom[1] - leftTop[1];
        right = ArrUtils.clamp(right, 0.0f, 1000.0f);

        return left * right;
    }
}
