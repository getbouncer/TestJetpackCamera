package com.getbouncer.cardscan.base.ml.models.legacy.ssd;

import android.graphics.RectF;

import org.json.JSONObject;


public class DetectedOcrBox implements Comparable<DetectedOcrBox> {
    private float left, top, right, bottom;
    private float confidence;
    public int label;

    public RectF rect;


    public DetectedOcrBox(float left, float top, float right, float bottom, float confidence,
                          int imageWidth, int imageHeight, int label) {

        this.left = left * imageWidth;
        this.right = right * imageWidth;
        this.top = top * imageHeight;
        this.bottom = bottom * imageHeight;
        this.confidence = confidence;
        this.label = label;
        this.rect = new RectF(this.left, this.top, this.right, this.bottom);
    }

    @Override
    public int compareTo(DetectedOcrBox detectedOcrBox) {
        return Float.compare(this.left, detectedOcrBox.left);
    }
}
