//
///* Copyright 2018 The TensorFlow Authors. All Rights Reserved.
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
//==============================================================================*/
//
//package com.getbouncer.cardscan.base.ml.models.legacy;
//
//import android.content.Context;
//import android.util.Log;
//
//import java.io.IOException;
//import java.nio.MappedByteBuffer;
//import java.util.HashMap;
//import java.util.Hashtable;
//import java.util.Map;
//
//
//class SSDOcrModel extends ImageClassifier {
//    /** We normalized the images with mean 127.5 and std 128.5 during training */
//
//    private static final float IMAGE_MEAN = 127.5f;
//    private static final float IMAGE_STD = 128.5f;
//
//
//    /** the model takes a 300x300 sample images as input */
//
//    private static final int CROP_SIZE_HEIGHT = 375;
//
//    private static final int CROP_SIZE_WIDTH = 600;
//    /** To be  used later */
//    private static final int NUM_THREADS = 4;
//    private boolean isModelQuantized; // TODO later
//
//    /** We use the output from last two layers with feature maps 19x19 and 10x10
//     * and for each feature map activation we have 6 priors, so total priors are
//     * 19x19x6 + 10x10x6 = 2766
//     */
//    static final int NUM_OF_PRIORS = 3420;
//
//    /** For each activation in our feature map, we have predictions for 6 bounding boxes
//     *   of different aspect ratios
//     */
//    static final int NUM_OF_PRIORS_PER_ACTIVATION = 3;
//
//    /** We can detect a total of 12 objects plus the background class */
//    static final int NUM_OF_CLASSES = 11;
//
//    /** Each prior or bounding box can be represented by 4 coordinates
//     * XMin, YMin, XMax, YMax.
//     */
//    static final int NUM_OF_COORDINATES = 4;
//
//    /** Represents the total number of datapoints for locations and classes */
//
//    static final int NUM_LOC = NUM_OF_COORDINATES * NUM_OF_PRIORS;
//    static final int NUM_CLASS = NUM_OF_CLASSES * NUM_OF_PRIORS;
//
//    static final float PROB_THRESHOLD = 0.50f;
//    static final float IOU_THRESHOLD = 0.50f;
//    static final float CENTER_VARIANCE = 0.1f;
//    static final float SIZE_VARIANCE = 0.2f;
//    static final int CANDIDATE_SIZE = 50;
//    static final int TOP_K = 20;
//    static final Hashtable<String, Integer> featureMapSizes = new Hashtable<String, Integer>();
//
//
//
//    //static final int[] featureMapSizes = {32, 16};
//
//    // Config values.
//    private int inputSize;
//    // Pre-allocated buffers.
//
//    private int[] intValues;
//
//    /** outputLocations corresponds to the values of four co-ordinates of
//     * all the priors, this is equal to NUM_OF_COORDINATES x NUM_OF_PRIORS
//     * But this is reshaped by the model to 1 x [NUM_OF_COORDINATES x NUM_OF_PRIORS] */
//
//    float[][] outputLocations;
//
//    /** outputClasses corresponds to the values of the NUM_OF_CLASSES for all priors
//     *  this is equal to NUM_OF_CLASSES x NUM_OF_PRIORS
//     *  but this is reshaped by the model to 1 x [NUM_OF_CLASSES x NUM_OF_PRIORS]
//     */
//
//    float[][] outputClasses;
//
//    /** This datastructure will be populated by the Interpreter
//     * Since the model outputs multiple output types, we need to create the
//     * HashMap<> with outputLocations and outputClasses to be populated by running
//     * the SSD Model
//     */
//
//    private Map<Integer, Object> outputMap = new HashMap<>();
//
//
//    /**
//     * Initializes an {@code ImageClassifierFloatMobileNet}.
//     *
//     * @param context
//     */
//    public SSDOcrModel(Context context) throws IOException {
//        super(context);
//        featureMapSizes.put("layerOneWidth", 38);
//        featureMapSizes.put("layerOneHeight", 24);
//        featureMapSizes.put("layerTwoWidth", 19);
//        featureMapSizes.put("layerTwoHeight", 12);
//
//        /** The model reshapes all the data to 1 x [All Data Points]
//         */
//        outputLocations = new float[1][NUM_LOC];
//        outputClasses = new float[1][NUM_CLASS];
//
//        outputMap.put(0, outputClasses);
//        outputMap.put(1, outputLocations);
//
//
//    }
//
//
//    @Override
//    protected MappedByteBuffer loadModelFile(Context context) throws IOException {
//        return ModelFactory.getSharedInstance().loadSSDDetectModelFile(context);
//    }
//
//    @Override
//    protected int getImageSizeX() {
//        return CROP_SIZE_WIDTH;
//    }
//
//    @Override
//    protected int getImageSizeY() {
//        return CROP_SIZE_HEIGHT;
//    }
//
//    @Override
//    protected int getNumBytesPerChannel() {
//        return 4; // Float.SIZE / Byte.SIZE;
//    }
//
//    @Override
//    protected void addPixelValue(int pixelValue) {
//
//        /** Normalize each pixel with MEAN and STD from training */
//
//        imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
//        imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
//        imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
//    }
//
//    @Override
//    protected void runInference() {
//        Object[] inputArray = {imgData};
//        Log.d("SSD Inference", "Running inference on image ");
//        //final long startTime = SystemClock.uptimeMillis();
//        tflite.runForMultipleInputsOutputs(inputArray, outputMap);
//        //Log.d("SSD Inference", "Inference time: " + Long.toString(SystemClock.uptimeMillis() - startTime) );
//
//    }
//}