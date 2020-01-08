///* Copyright 2017 The TensorFlow Authors. All Rights Reserved.
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
//==============================================================================*/
//package com.getbouncer.cardscan.base.ml.models.legacy
//
//import android.graphics.Bitmap
//import android.os.SystemClock
//import android.util.Log
//import com.getbouncer.cardscan.base.ml.MLModel
//import com.getbouncer.cardscan.base.ml.MLModelFactory
//import org.tensorflow.lite.Interpreter
//import java.io.IOException
//import java.nio.ByteBuffer
//import java.nio.ByteOrder
//import java.nio.MappedByteBuffer
//
///**
// * Classifies images with Tensorflow Lite.
// */
//abstract class ImageClassifier(factory: MLModelFactory) : MLModel {
//
//    /** Preallocated buffers for storing image data in.  */
//    private val intValues = IntArray(imageSizeX * imageSizeY)
//    /** Options for configuring the Interpreter.  */
//
//    private val tfliteOptions = Interpreter.Options()
//
//    /** The loaded TensorFlow Lite model.  */
//    private var tfliteModel: MappedByteBuffer?
//
//    /** An instance of the driver class to run model inference with Tensorflow Lite.  */
//    @JvmField
//    protected var tflite: Interpreter?
//
//    /** A ByteBuffer to hold image data, to be feed into Tensorflow Lite as inputs.  */
//    @JvmField
//    protected var imgData: ByteBuffer?
//
//    /** Classifies a frame from the preview stream.  */
//    fun classifyFrame(bitmap: Bitmap) {
//        if (tflite == null) {
//            Log.e(TAG, "Image classifier has not been initialized; Skipped.")
//        }
//        convertBitmapToByteBuffer(bitmap)
//        // Here's where the magic happens!!!
//        val startTime = SystemClock.uptimeMillis()
//        runInference()
//        val endTime = SystemClock.uptimeMillis()
//        //Log.d(TAG, "Timecost to run model inference: " + Long.toString(endTime - startTime));
//    }
//
//    private fun recreateInterpreter() {
//        if (tflite != null) {
//            tflite!!.close()
//            tflite = Interpreter(tfliteModel!!, tfliteOptions)
//        }
//    }
//
//    fun useCPU() {
//        tfliteOptions.setUseNNAPI(false)
//        recreateInterpreter()
//    }
//
//    fun useNNAPI() {
//        tfliteOptions.setUseNNAPI(true)
//        recreateInterpreter()
//    }
//
//    fun setNumThreads(numThreads: Int) {
//        tfliteOptions.setNumThreads(numThreads)
//        recreateInterpreter()
//    }
//
//    /** Closes tflite to release resources.  */
//    fun close() {
//        tflite!!.close()
//        tflite = null
//        tfliteModel = null
//    }
//
//    /** Memory-map the model file in Assets.  */
//    @Throws(IOException::class)
//    protected abstract fun loadModelFile(factory: MLModelFactory?): MappedByteBuffer?
//
//    /** Writes Image data into a `ByteBuffer`.  */
//    private fun convertBitmapToByteBuffer(bitmap: Bitmap) {
//        if (imgData == null) {
//            return
//        }
//        imgData!!.rewind()
//        val resizedBitmap =
//            Bitmap.createScaledBitmap(bitmap, imageSizeX, imageSizeY, false)
//        resizedBitmap.getPixels(
//            intValues, 0, resizedBitmap.width, 0, 0,
//            resizedBitmap.width, resizedBitmap.height
//        )
//        // Convert the image to floating point.
//        var pixel = 0
//        val startTime = SystemClock.uptimeMillis()
//        for (i in 0 until imageSizeX) {
//            for (j in 0 until imageSizeY) {
//                val `val` = intValues[pixel++]
//                addPixelValue(`val`)
//            }
//        }
//        val endTime = SystemClock.uptimeMillis()
//        //Log.d(TAG, "Timecost to put values into ByteBuffer: " + Long.toString(endTime - startTime));
//    }
//
//    /**
//     * Get the image size along the x axis.
//     *
//     * @return
//     */
//    protected abstract val imageSizeX: Int
//
//    /**
//     * Get the image size along the y axis.
//     *
//     * @return
//     */
//    protected abstract val imageSizeY: Int
//
//    /**
//     * Get the number of bytes that is used to store a single color channel value.
//     *
//     * @return
//     */
//    protected abstract val numBytesPerChannel: Int
//
//    /**
//     * Add pixelValue to byteBuffer.
//     *
//     * @param pixelValue
//     */
//    protected abstract fun addPixelValue(pixelValue: Int)
//
//    /**
//     * Run inference using the prepared input in [.imgData]. Afterwards, the result will be
//     * provided by getProbability().
//     *
//     *
//     * This additional method is necessary, because we don't have a common base for different
//     * primitive data types.
//     */
//    protected abstract fun runInference()
//
//    companion object {
//        /** Tag for the [Log].  */
//        private const val TAG = "CardScan"
//        /** Dimensions of inputs.  */
//        private const val DIM_BATCH_SIZE = 1
//        private const val DIM_PIXEL_SIZE = 3
//    }
//
//    /** Initializes an `ImageClassifier`.  */
//    init {
//        tfliteModel = loadModelFile(factory)
//        tflite = Interpreter(tfliteModel!!, tfliteOptions)
//        imgData = ByteBuffer.allocateDirect(
//            DIM_BATCH_SIZE
//                    * imageSizeX
//                    * imageSizeY
//                    * DIM_PIXEL_SIZE
//                    * numBytesPerChannel
//        )
//        imgData!!.order(ByteOrder.nativeOrder())
//    }
//}