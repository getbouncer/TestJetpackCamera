//package com.getbouncer.cardscan.base.ml.models.legacy;
//
//import android.app.ActivityManager;
//import android.content.Context;
//import android.content.pm.ConfigurationInfo;
//import android.graphics.Bitmap;
//import android.util.Log;
//import android.util.Size;
//
//import com.getbouncer.cardscan.base.domain.DetectionBox;
//import com.getbouncer.cardscan.base.ml.models.FindFourModel;
//import com.getbouncer.cardscan.base.ml.models.RecognizeNumbers;
//import com.getbouncer.cardscan.base.ml.models.RecognizedDigitsModel;
//
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//
///**
// * This class is not thread safe, make sure that all methods run on the same thread.
// */
//public class Ocr {
//    private static FindFourModel findFour = null;
//    private static RecognizedDigitsModel recognizedDigitsModel = null;
//    public List<DetectionBox> digitBoxes = new ArrayList<>();
//    public DetectionBox expiryBox = null;
//    public Expiry expiry = null;
//    boolean hadUnrecoverableException = false;
//    public static boolean USE_GPU = false;
//
//    static boolean isInit() {
//        return findFour != null && recognizedDigitsModel != null;
//    }
//
//    private ArrayList<DetectionBox> detectBoxes(Bitmap image) {
//        ArrayList<DetectionBox> boxes = new ArrayList<>();
//        for (int row = 0; row < findFour.ROWS; row++) {
//            for (int col = 0; col < findFour.COLS; col++) {
//
//                if (findFour.hasDigits(row, col)) {
//                    float confidence = findFour.digitConfidence(row, col);
//                    Size imageSize = new Size(image.getWidth(), image.getHeight());
//                    DetectionBox box = new DetectionBox(row, col, confidence, findFour.ROWS,
//                            findFour.COLS, findFour.BOX_SIZE, findFour.CARD_SIZE, imageSize);
//                    boxes.add(box);
//                }
//
//            }
//        }
//        return boxes;
//    }
//
//    private ArrayList<DetectionBox> detectExpiry(Bitmap image) {
//        ArrayList<DetectionBox> boxes = new ArrayList<>();
//        for (int row = 0; row < findFour.ROWS; row++) {
//            for (int col = 0; col < findFour.COLS; col++) {
//
//                if (findFour.hasExpiry(row, col)) {
//                    float confidence = findFour.expiryConfidence(row, col);
//                    Size imageSize = new Size(image.getWidth(), image.getHeight());
//                    DetectionBox box = new DetectionBox(row, col, confidence, findFour.ROWS,
//                            findFour.COLS, findFour.BOX_SIZE, findFour.CARD_SIZE, imageSize);
//                    boxes.add(box);
//                }
//
//            }
//        }
//        return boxes;
//    }
//
//    private String runModel(Bitmap image) {
//        findFour.classifyFrame(image);
//        ArrayList<DetectionBox> boxes = detectBoxes(image);
//        ArrayList<DetectionBox> expiryBoxes = detectExpiry(image);
//        PostDetectionAlgorithm postDetection = new PostDetectionAlgorithm(boxes);
//        RecognizeNumbers recognizeNumbers = new RecognizeNumbers(image, FindFourModel.ROWS,
//                FindFourModel.COLS);
//        ArrayList<ArrayList<DetectionBox>> lines = postDetection.horizontalNumbers();
//
//        String algorithm = null;
//        String number = recognizeNumbers.number(recognizedDigitsModel, lines);
//        if (number == null) {
//            ArrayList<ArrayList<DetectionBox>> verticalLines = postDetection.verticalNumbers();
//            number = recognizeNumbers.number(recognizedDigitsModel, verticalLines);
//            lines.addAll(verticalLines);
//        } else {
//            algorithm = "horizontal";
//        }
//
//        if (number == null) {
//            ArrayList<ArrayList<DetectionBox>> amexLines = postDetection.amexNumbers();
//            number = recognizeNumbers.amexNumber(recognizedDigitsModel, amexLines);
//            lines.addAll(amexLines);
//            if (number != null) {
//                algorithm = "amex";
//            }
//        } else {
//            algorithm = "vertical";
//        }
//
//        boxes = new ArrayList<>();
//        for (ArrayList<DetectionBox> numbers:lines) {
//            boxes.addAll(numbers);
//        }
//
//        this.expiry = null;
//        if (expiryBoxes.size() > 0) {
//            Collections.sort(expiryBoxes);
//            DetectionBox expiryBox = expiryBoxes.get(expiryBoxes.size() - 1);
//            this.expiry = Expiry.from(recognizedDigitsModel, image, expiryBox.rect);
//            if (this.expiry != null) {
//                this.expiryBox = expiryBox;
//            } else {
//                this.expiryBox = null;
//            }
//        }
//
//
//        this.digitBoxes = boxes;
//        return number;
//    }
//
//    private boolean hasOpenGl31(Context context) {
//        int openGlVersion = 0x00030001;
//        ActivityManager activityManager =
//                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
//        ConfigurationInfo configInfo = activityManager.getDeviceConfigurationInfo();
//        if (configInfo.reqGlEsVersion != ConfigurationInfo.GL_ES_VERSION_UNDEFINED) {
//            return configInfo.reqGlEsVersion >= openGlVersion;
//        } else {
//            return false;
//        }
//    }
//
//    public synchronized String predict(Bitmap image, Context context) {
//        final int NUM_THREADS = 4;
//        try {
//            boolean createdNewModel = false;
//
//            if (findFour == null) {
//                findFour = new FindFourModel(context);
//                findFour.setNumThreads(NUM_THREADS);
//                createdNewModel = true;
//            }
//
//            if (recognizedDigitsModel == null) {
//                recognizedDigitsModel = new RecognizedDigitsModel(context);
//                recognizedDigitsModel.setNumThreads(NUM_THREADS);
//                createdNewModel = true;
//            }
//
//            if (createdNewModel && hasOpenGl31(context) && USE_GPU) {
//                try {
//                    findFour.useNNAPI();
//                    recognizedDigitsModel.useNNAPI();
//                } catch (Error | Exception e) {
//                    Log.i("Ocr", "useGpu exception, falling back to CPU", e);
//                    findFour = new FindFourModel(context);
//                    recognizedDigitsModel = new RecognizedDigitsModel(context);
//                }
//            }
//
//
//            try {
//                return runModel(image);
//            } catch (Error | Exception e) {
//                Log.i("Ocr", "runModel exception, retry prediction", e);
//                findFour = new FindFourModel(context);
//                recognizedDigitsModel = new RecognizedDigitsModel(context);
//                return runModel(image);
//            }
//        } catch (Error | Exception e) {
//            Log.e("Ocr", "unrecoverable exception on Ocr", e);
//            hadUnrecoverableException = true;
//            return null;
//        }
//    }
//}
